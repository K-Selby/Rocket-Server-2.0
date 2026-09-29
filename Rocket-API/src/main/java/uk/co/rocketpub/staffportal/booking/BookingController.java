package uk.co.rocketpub.staffportal.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final JdbcTemplate jdbc;
    private final AuthService authService;
    private final BookingAllocationService allocation;
    public BookingController(JdbcTemplate jdbc, AuthService authService, BookingAllocationService allocation) { this.jdbc = jdbc; this.authService = authService; this.allocation = allocation; }

    @GetMapping
    public Map<String,Object> list(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="active") String view, HttpSession session) {
        authService.currentStaffMember(session);
        LocalDate selected = date == null ? LocalDate.now() : date;
        String status = "cancelled".equalsIgnoreCase(view) ? "b.status='Cancelled'" : "b.status!='Cancelled'";
        String search = "%" + q.trim().toLowerCase() + "%";
        String bookingSql = """
            SELECT b.id,b.customer_id,b.booking_date,b.booking_time,b.duration_minutes,b.party_size,
              COALESCE(b.number_of_children,0) number_of_children,COALESCE(b.high_chairs_required,0) high_chairs_required,
              COALESCE(b.is_eating_food,1) is_eating_food,b.status,b.occasion,b.notes,
              COALESCE(b.deposit_paid_amount,0) deposit_paid_amount,c.name customer_name,c.phone customer_phone,
              COALESCE(GROUP_CONCAT(pt.number,', '),'') table_numbers,COALESCE(GROUP_CONCAT(pt.id,','),'') table_ids,
              COALESCE(SUM(pt.capacity),0) selected_capacity
            FROM booking b JOIN customer c ON c.id=b.customer_id
            LEFT JOIN booking_table bt ON bt.booking_id=b.id LEFT JOIN pub_table pt ON pt.id=bt.table_id
            WHERE b.booking_date=? AND %s AND
              (lower(c.name) LIKE ? OR c.phone LIKE ? OR lower(COALESCE(b.notes,'')) LIKE ?)
            GROUP BY b.id ORDER BY b.booking_time,b.id
            """.formatted(status);
        List<Map<String,Object>> bookings = jdbc.queryForList(
                bookingSql, selected.toString(), search, search, search);
        return Map.of("date",selected,"bookings",bookings,"tables",jdbc.queryForList("""
            SELECT pt.id,pt.number,pt.capacity,a.name area FROM pub_table pt JOIN area a ON a.id=pt.area_id
            WHERE pt.active=1 AND lower(a.name)!='bar' ORDER BY a.name,CAST(pt.number AS INTEGER),pt.number
                """));
    }

    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            HttpSession session) {
        authService.currentStaffMember(session);
        LocalDate selected = date == null ? LocalDate.now() : date;
        List<Map<String,Object>> bookings = jdbc.queryForList("""
            SELECT b.id,b.booking_time,b.duration_minutes,b.party_size,b.status,b.occasion,b.notes,
              COALESCE(b.is_eating_food,1) is_eating_food,c.name customer_name,c.phone customer_phone,
              COALESCE(GROUP_CONCAT(pt.id,','),'') table_ids,COALESCE(GROUP_CONCAT(pt.number,', '),'') table_numbers
            FROM booking b JOIN customer c ON c.id=b.customer_id
            LEFT JOIN booking_table bt ON bt.booking_id=b.id LEFT JOIN pub_table pt ON pt.id=bt.table_id
            WHERE b.booking_date=? AND b.status NOT IN ('Cancelled','Completed')
            GROUP BY b.id ORDER BY b.booking_time,b.id
            """,selected.toString());
        List<Map<String,Object>> largeParties = jdbc.queryForList("""
            SELECT lp.id,lp.event_time,lp.expected_end_time,lp.reserve_for_rest_of_day,lp.party_size,
              lp.status,lp.occasion,lp.notes,lp.customer_name,lp.customer_phone,
              COALESCE(GROUP_CONCAT(DISTINCT lpt.table_id),'') table_ids,
              COALESCE(GROUP_CONCAT(DISTINCT lpa.area_id),'') area_ids
            FROM large_party_inquiry lp
            LEFT JOIN large_party_reserved_table lpt ON lpt.inquiry_id=lp.id
            LEFT JOIN large_party_reserved_area lpa ON lpa.inquiry_id=lp.id
            WHERE lp.event_date=? AND lp.status!='Cancelled' GROUP BY lp.id ORDER BY lp.event_time,lp.id
            """,selected.toString());
        Integer settingsCount=jdbc.queryForObject("SELECT COUNT(*) FROM floor_plan_setting WHERE name='main'",Integer.class);
        Map<String,Object> settings=settingsCount!=null&&settingsCount>0
            ?jdbc.queryForMap("SELECT canvas_width,canvas_height FROM floor_plan_setting WHERE name='main'")
            :Map.of("canvas_width",1200,"canvas_height",760);
        return Map.of("date",selected,"settings",settings,"bookings",bookings,"largeParties",largeParties,
          "pendingRepeats",jdbc.queryForList("""
            SELECT r.id,r.booking_time,r.party_size,c.name customer_name,c.phone customer_phone
            FROM repeat_booking r JOIN customer c ON c.id=r.customer_id
            WHERE r.active=1 AND r.weekday=? AND NOT EXISTS(
              SELECT 1 FROM repeat_booking_occurrence o WHERE o.repeat_booking_id=r.id AND o.occurrence_date=?)
            ORDER BY r.booking_time,c.name
            """,selected.getDayOfWeek().getValue()%7,selected.toString()),
          "reminders",jdbc.queryForList("""
            SELECT ir.id,ir.reminder_date,ir.note,ir.reminder_kind,lp.customer_name
            FROM inquiry_reminder ir JOIN large_party_inquiry lp ON lp.id=ir.inquiry_id
            WHERE ir.completed=0 AND ir.reminder_date<=? AND lp.status!='Cancelled'
            ORDER BY ir.reminder_date,ir.id LIMIT 8
            """,selected.toString()),
          "tables",jdbc.queryForList("""
            SELECT pt.id,pt.number,pt.capacity,pt.area_id,a.name area,COALESCE(pt.active,1) active,
              COALESCE(pt.x_position,40) x,COALESCE(pt.y_position,40) y,COALESCE(pt.layout_width,90) width,
              COALESCE(pt.layout_height,60) height,COALESCE(pt.layout_shape,'rectangle') shape,
              COALESCE(pt.layout_rotation,0) rotation FROM pub_table pt JOIN area a ON a.id=pt.area_id
            WHERE pt.active=1 ORDER BY CAST(pt.number AS INTEGER),pt.number
            """),
          "objects",jdbc.queryForList("""
            SELECT id,object_type,label,x_position x,y_position y,layout_width width,layout_height height,
              layout_shape shape,layout_rotation rotation,z_index,area_id FROM floor_plan_object ORDER BY z_index,id
            """));
    }

    @GetMapping("/upcoming")
    public Map<String,Object> upcoming(
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="3") int months,
            HttpSession session) {
        authService.currentStaffMember(session);
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(Math.max(1, Math.min(months, 12)));
        String search = "%" + q.trim().toLowerCase() + "%";
        List<Map<String,Object>> items = jdbc.queryForList("""
            SELECT 'booking' item_type,b.id,b.booking_date event_date,b.booking_time event_time,
              c.name customer_name,c.phone customer_phone,b.party_size,b.status,b.occasion,b.notes,
              COALESCE(b.number_of_children,0) number_of_children,
              COALESCE(b.high_chairs_required,0) high_chairs_required,
              COALESCE(b.is_eating_food,1) is_eating_food,
              COALESCE(b.deposit_paid_amount,0) deposit_paid_amount,
              COALESCE(GROUP_CONCAT(pt.number,', '),'') table_numbers,
              COALESCE(GROUP_CONCAT(pt.id,','),'') table_ids
            FROM booking b JOIN customer c ON c.id=b.customer_id
            LEFT JOIN booking_table bt ON bt.booking_id=b.id
            LEFT JOIN pub_table pt ON pt.id=bt.table_id
            WHERE b.booking_date BETWEEN ? AND ? AND b.status NOT IN ('Cancelled','Completed')
              AND (lower(c.name) LIKE ? OR c.phone LIKE ? OR lower(COALESCE(b.notes,'')) LIKE ?)
            GROUP BY b.id
            UNION ALL
            SELECT 'large_party' item_type,lp.id,lp.event_date,lp.event_time,
              lp.customer_name,lp.customer_phone,lp.party_size,lp.status,lp.occasion,lp.notes,
              COALESCE(lp.number_of_children,0),COALESCE(lp.high_chairs_required,0),
              CASE WHEN lower(COALESCE(lp.food_type,''))='drinks only' THEN 0 ELSE 1 END,
              COALESCE(lp.deposit_paid_amount,0),'',''
            FROM large_party_inquiry lp
            WHERE lp.event_date BETWEEN ? AND ? AND lp.status!='Cancelled'
              AND (lower(lp.customer_name) LIKE ? OR lp.customer_phone LIKE ? OR lower(COALESCE(lp.notes,'')) LIKE ?)
            ORDER BY event_date,event_time,id
            """, from.toString(),to.toString(),search,search,search,
            from.toString(),to.toString(),search,search,search);
        return Map.of("from",from,"to",to,"items",items,"tables",jdbc.queryForList("""
            SELECT pt.id,pt.number,pt.capacity,a.name area FROM pub_table pt JOIN area a ON a.id=pt.area_id
            WHERE pt.active=1 AND lower(a.name)!='bar' ORDER BY a.name,CAST(pt.number AS INTEGER),pt.number
            """));
    }

    @PostMapping @Transactional
    public Map<String,Object> create(@RequestBody BookingRequest r,HttpSession s) {
        authService.currentStaffMember(s); List<Long> tableIds=resolvedTables(r,null); validate(r,null,tableIds); long customer=findOrCreateCustomer(r.customerName(),r.customerPhone());
        jdbc.update("""
            INSERT INTO booking (customer_id,booking_date,booking_time,duration_minutes,party_size,number_of_children,
            high_chairs_required,is_eating_food,occasion,notes,status,deposit_required_amount,deposit_paid_amount,created_at)
            VALUES (?,?,?,150,?,?,?,?,?,?,'Booked',?,?,CURRENT_TIMESTAMP)
            """, customer,r.bookingDate().toString(),r.bookingTime().toString(),
            r.partySize(),r.numberOfChildren(),r.highChairsRequired(),r.isEatingFood(),blank(r.occasion()),blank(r.notes()),depositFor(r.partySize()),Math.max(0,r.depositPaidAmount()));
        Long id=jdbc.queryForObject("SELECT last_insert_rowid()",Long.class); replaceTables(id,tableIds);
        if(r.repeatWeekly()) { jdbc.update("""
            INSERT INTO repeat_booking(customer_id,weekday,booking_time,party_size,number_of_children,is_eating_food,
              preferred_table_id,wants_near_tv,avoids_bench,occasion,notes,active,created_at,high_chairs_required)
            VALUES(?,?,?,?,?,?,?,0,0,?,?,1,CURRENT_TIMESTAMP,?)
            """,customer,r.bookingDate().getDayOfWeek().getValue()%7,r.bookingTime().toString(),r.partySize(),r.numberOfChildren(),r.isEatingFood(),
            tableIds.size()==1?tableIds.getFirst():null,blank(r.occasion()),blank(r.notes()),r.highChairsRequired());
            Long repeatId=jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);
            jdbc.update("UPDATE booking SET repeat_booking_id=? WHERE id=?",repeatId,id);
            jdbc.update("INSERT INTO repeat_booking_occurrence(repeat_booking_id,occurrence_date,status,booking_id,created_at) VALUES(?,?,'Confirmed',?,CURRENT_TIMESTAMP)",repeatId,r.bookingDate().toString(),id);
        }
        return Map.of("id",id,"warning",capacityWarning(r.partySize(),tableIds),"autoAssigned",safe(r.tableIds()).isEmpty()&&!tableIds.isEmpty());
    }

    @PutMapping("/{id}") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody BookingRequest r,HttpSession s) {
        authService.currentStaffMember(s); List<Long> tableIds=resolvedTables(r,id); validate(r,id,tableIds); long customer=findOrCreateCustomer(r.customerName(),r.customerPhone());
        int changed=jdbc.update("""
            UPDATE booking SET customer_id=?,booking_date=?,booking_time=?,party_size=?,number_of_children=?,
            high_chairs_required=?,is_eating_food=?,occasion=?,notes=?,deposit_required_amount=?,deposit_paid_amount=? WHERE id=?
            """,
            customer,r.bookingDate().toString(),r.bookingTime().toString(),r.partySize(),r.numberOfChildren(),r.highChairsRequired(),
            r.isEatingFood(),blank(r.occasion()),blank(r.notes()),depositFor(r.partySize()),Math.max(0,r.depositPaidAmount()),id);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found."); replaceTables(id,tableIds);
        return Map.of("updated",true,"warning",capacityWarning(r.partySize(),tableIds),"autoAssigned",safe(r.tableIds()).isEmpty()&&!tableIds.isEmpty());
    }

    @PutMapping("/{id}/status")
    public Map<String,Boolean> status(@PathVariable long id,@RequestBody StatusRequest r,HttpSession s) {
        authService.currentStaffMember(s); if(!List.of("Booked","Completed","Cancelled").contains(r.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid booking status.");
        int changed=jdbc.update("UPDATE booking SET status=?,completed_at=CASE WHEN ?='Completed' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id=?",r.status(),r.status(),id);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found."); return Map.of("updated",true);
    }

    @DeleteMapping("/{id}") @Transactional
    public Map<String,Boolean> delete(@PathVariable long id,HttpSession s) {
        authService.currentStaffMember(s); String status=jdbc.query("SELECT status FROM booking WHERE id=?",rs->rs.next()?rs.getString(1):null,id);
        if(status==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found.");
        if(!"Cancelled".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancel the booking before deleting it.");
        jdbc.update("UPDATE repeat_booking_occurrence SET booking_id=NULL,status='Cancelled' WHERE booking_id=?",id);
        jdbc.update("DELETE FROM booking_table WHERE booking_id=?",id); jdbc.update("DELETE FROM booking WHERE id=?",id); return Map.of("deleted",true);
    }

    private void validate(BookingRequest r,Long existing,List<Long> tableIds) {
        if(r.customerName()==null||r.customerName().isBlank()||r.customerPhone()==null||r.customerPhone().replaceAll("\\D","").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Customer name and phone number are required.");
        if(r.partySize()<1||r.partySize()>20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bookings can be for 1 to 20 people.");
        if(r.numberOfChildren()<0||r.numberOfChildren()>r.partySize()||r.highChairsRequired()<0||r.highChairsRequired()>r.partySize()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Children and high chairs cannot exceed the party size.");
        if(r.bookingDate()==null||r.bookingTime()==null||r.bookingTime().isBefore(LocalTime.NOON)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid date and a time from 12:00 midday.");
        if(existing==null&&(r.bookingDate().isBefore(LocalDate.now())||(r.bookingDate().equals(LocalDate.now())&&!r.bookingTime().isAfter(LocalTime.now())))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New bookings must be in the future.");
        if(tableIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No suitable table is available. Choose different booking details or select tables manually.");
        for(Long table:tableIds) { Integer conflicts=jdbc.queryForObject("""
            SELECT COUNT(*) FROM booking_table bt JOIN booking b ON b.id=bt.booking_id
            WHERE bt.table_id=? AND b.booking_date=? AND b.status NOT IN ('Cancelled','Completed')
            AND time(b.booking_time)<time(?,'+150 minutes') AND time(b.booking_time,'+'||b.duration_minutes||' minutes')>time(?)
            AND (? IS NULL OR b.id!=?)
            """,Integer.class,table,r.bookingDate().toString(),r.bookingTime().toString(),r.bookingTime().toString(),existing,existing);
            if(conflicts!=null&&conflicts>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"One of the selected tables overlaps another booking."); }
    }
    private List<Long> resolvedTables(BookingRequest r,Long existing){List<Long> manual=safe(r.tableIds());if(!manual.isEmpty())return manual;if(r.partySize()>=10)return List.of();return allocation.suggest(r.bookingDate(),r.bookingTime(),r.partySize(),r.isEatingFood(),null,null,false,false,existing,List.of());}
    private long findOrCreateCustomer(String name,String raw) { String phone=raw.replaceAll("\\D",""); List<Long> ids=jdbc.query("SELECT id FROM customer WHERE phone=?",(rs,n)->rs.getLong(1),phone); if(!ids.isEmpty()){jdbc.update("UPDATE customer SET name=? WHERE id=?",name.trim(),ids.getFirst());return ids.getFirst();} jdbc.update("INSERT INTO customer(name,phone,created_at) VALUES(?,?,CURRENT_TIMESTAMP)",name.trim(),phone); return jdbc.queryForObject("SELECT last_insert_rowid()",Long.class); }
    private void replaceTables(long id,List<Long> ids){jdbc.update("DELETE FROM booking_table WHERE booking_id=?",id);for(Long table:safe(ids))jdbc.update("INSERT INTO booking_table(booking_id,table_id) VALUES(?,?)",id,table);}
    private String capacityWarning(int people,List<Long> ids){if(safe(ids).isEmpty())return "No tables have been assigned.";int capacity=safe(ids).stream().mapToInt(id->jdbc.queryForObject("SELECT capacity FROM pub_table WHERE id=?",Integer.class,id)).sum();return capacity<people?"Selected tables provide "+capacity+" seats for "+people+" people.":"";}
    private List<Long> safe(List<Long> ids){return ids==null?List.of():ids.stream().distinct().toList();} private double depositFor(int size){return size>=10?size*5.0:0;} private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    public record BookingRequest(String customerName,String customerPhone,LocalDate bookingDate,LocalTime bookingTime,int partySize,int numberOfChildren,int highChairsRequired,boolean isEatingFood,String occasion,String notes,double depositPaidAmount,List<Long> tableIds,boolean repeatWeekly){}
    public record StatusRequest(String status){}
}
