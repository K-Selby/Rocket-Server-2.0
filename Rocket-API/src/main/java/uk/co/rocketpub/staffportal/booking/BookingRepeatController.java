package uk.co.rocketpub.staffportal.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;

@RestController
@RequestMapping("/api/booking/repeats")
public class BookingRepeatController {
    private final JdbcTemplate jdbc; private final AuthService auth; private final BookingAllocationService allocation;
    public BookingRepeatController(JdbcTemplate jdbc,AuthService auth,BookingAllocationService allocation){this.jdbc=jdbc;this.auth=auth;this.allocation=allocation;}

    @GetMapping
    public Map<String,Object> list(HttpSession session){auth.currentStaffMember(session);return Map.of("repeats",jdbc.queryForList("""
      SELECT r.id,r.weekday,r.booking_time,r.party_size,r.number_of_children,r.high_chairs_required,r.is_eating_food,
        r.occasion,r.notes,r.preferred_table_id,c.name customer_name,c.phone customer_phone
      FROM repeat_booking r JOIN customer c ON c.id=r.customer_id WHERE r.active=1 ORDER BY r.weekday,r.booking_time,c.name
      """));}

    @PostMapping("/{id}/confirm") @Transactional
    public Map<String,Object> confirm(@PathVariable long id,@RequestBody OccurrenceRequest request,HttpSession session){auth.currentStaffMember(session);Map<String,Object> repeat=find(id);LocalDate date=request.occurrenceDate();validateDate(repeat,date);Integer existing=countOccurrence(id,date);if(existing>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This repeat booking has already been handled for that date.");int people=((Number)repeat.get("party_size")).intValue();LocalTime time=LocalTime.parse(repeat.get("booking_time").toString());Long preferred=repeat.get("preferred_table_id") instanceof Number number?number.longValue():null;List<Long> tables=allocation.suggest(date,time,people,truth(repeat.get("is_eating_food")),null,preferred,false,false,null,List.of());if(tables.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No suitable table is available. Create the booking manually and choose tables.");jdbc.update("""
      INSERT INTO booking(customer_id,booking_date,booking_time,duration_minutes,party_size,number_of_children,high_chairs_required,
        is_eating_food,occasion,notes,status,deposit_required_amount,deposit_paid_amount,repeat_booking_id,created_at)
      VALUES(?,?,?,150,?,?,?,?,?,?,'Booked',?,0,?,CURRENT_TIMESTAMP)
      """,repeat.get("customer_id"),date.toString(),time.toString(),people,repeat.get("number_of_children"),repeat.get("high_chairs_required"),repeat.get("is_eating_food"),repeat.get("occasion"),repeat.get("notes"),people>=10?people*5.0:0,id);Long bookingId=jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);for(Long table:tables)jdbc.update("INSERT INTO booking_table(booking_id,table_id) VALUES(?,?)",bookingId,table);jdbc.update("INSERT INTO repeat_booking_occurrence(repeat_booking_id,occurrence_date,status,booking_id,created_at) VALUES(?,?,'Confirmed',?,CURRENT_TIMESTAMP)",id,date.toString(),bookingId);return Map.of("bookingId",bookingId,"warning",tables.isEmpty()?"No tables could be assigned automatically.":"");}

    @PostMapping("/{id}/skip")
    public Map<String,Boolean> skip(@PathVariable long id,@RequestBody OccurrenceRequest request,HttpSession session){auth.currentStaffMember(session);Map<String,Object> repeat=find(id);validateDate(repeat,request.occurrenceDate());if(countOccurrence(id,request.occurrenceDate())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This repeat booking has already been handled for that date.");jdbc.update("INSERT INTO repeat_booking_occurrence(repeat_booking_id,occurrence_date,status,created_at) VALUES(?,?,'Skipped',CURRENT_TIMESTAMP)",id,request.occurrenceDate().toString());return Map.of("skipped",true);}

    @PutMapping("/{id}")
    public Map<String,Boolean> update(@PathVariable long id,@RequestBody RepeatRequest r,HttpSession session){auth.currentStaffMember(session);if(r.partySize()<1||r.partySize()>20||r.bookingTime()==null||r.bookingTime().isBefore(LocalTime.NOON))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid time and party size.");int changed=jdbc.update("UPDATE repeat_booking SET weekday=?,booking_time=?,party_size=?,number_of_children=?,high_chairs_required=?,is_eating_food=?,occasion=?,notes=?,active=? WHERE id=?",r.weekday(),r.bookingTime().toString(),r.partySize(),r.numberOfChildren(),r.highChairsRequired(),r.isEatingFood(),r.occasion(),r.notes(),r.active(),id);if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Repeat booking not found.");return Map.of("updated",true);}

    private Map<String,Object> find(long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM repeat_booking WHERE id=? AND active=1",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Repeat booking not found.");return rows.getFirst();}
    private int countOccurrence(long id,LocalDate date){return jdbc.queryForObject("SELECT COUNT(*) FROM repeat_booking_occurrence WHERE repeat_booking_id=? AND occurrence_date=?",Integer.class,id,date.toString());}
    private void validateDate(Map<String,Object> repeat,LocalDate date){if(date==null||date.isBefore(LocalDate.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an upcoming date.");int weekday=((Number)repeat.get("weekday")).intValue();int javaWeekday=weekday==0?7:weekday;if(date.getDayOfWeek().getValue()!=javaWeekday)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose the correct weekday for this repeat booking.");}
    private boolean truth(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    public record OccurrenceRequest(LocalDate occurrenceDate){}
    public record RepeatRequest(int weekday,LocalTime bookingTime,int partySize,int numberOfChildren,int highChairsRequired,boolean isEatingFood,String occasion,String notes,boolean active){}
}
