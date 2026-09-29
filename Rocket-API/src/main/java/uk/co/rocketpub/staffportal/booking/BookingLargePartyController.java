package uk.co.rocketpub.staffportal.booking;

import java.time.LocalDate;
import java.time.LocalTime;
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
@RequestMapping("/api/booking/large-parties")
public class BookingLargePartyController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final BookingAllocationService allocation;
    public BookingLargePartyController(JdbcTemplate jdbc,AuthService auth,BookingAllocationService allocation){this.jdbc=jdbc;this.auth=auth;this.allocation=allocation;}

    @GetMapping
    public Map<String,Object> list(@RequestParam(defaultValue="") String q,HttpSession session){
        auth.currentStaffMember(session);ensureSupportingData();String search="%"+q.trim().toLowerCase()+"%";LocalDate archiveDate=LocalDate.now().minusDays(2);
        List<Map<String,Object>> inquiries=jdbc.queryForList("""
            SELECT lp.*,mo.name menu_name,COALESCE(mo.price_per_head,0) menu_price,
              COALESCE(GROUP_CONCAT(DISTINCT a.name),'') reserved_areas,
              COALESCE(GROUP_CONCAT(DISTINCT pt.number),'') reserved_tables,
              COALESCE(GROUP_CONCAT(DISTINCT lpa.area_id),'') reserved_area_ids,
              COALESCE(GROUP_CONCAT(DISTINCT lpt.table_id),'') reserved_table_ids,
              COALESCE((SELECT SUM(x.price_per_head*x.quantity_people) FROM inquiry_extra_dish x WHERE x.inquiry_id=lp.id),0) extras_total,
              COALESCE((SELECT SUM(p.amount) FROM inquiry_payment p WHERE p.inquiry_id=lp.id),0) balance_paid,
              MAX(0,COALESCE(lp.quoted_food_total,0)+COALESCE((SELECT SUM(x.price_per_head*x.quantity_people) FROM inquiry_extra_dish x WHERE x.inquiry_id=lp.id),0)-COALESCE(lp.deposit_paid_amount,0)-COALESCE((SELECT SUM(p.amount) FROM inquiry_payment p WHERE p.inquiry_id=lp.id),0)) outstanding_balance
            FROM large_party_inquiry lp LEFT JOIN large_party_menu_option mo ON mo.id=lp.menu_option_id
            LEFT JOIN large_party_reserved_area lpa ON lpa.inquiry_id=lp.id LEFT JOIN area a ON a.id=lpa.area_id
            LEFT JOIN large_party_reserved_table lpt ON lpt.inquiry_id=lp.id LEFT JOIN pub_table pt ON pt.id=lpt.table_id
            WHERE lp.status!='Cancelled' AND (lp.event_date IS NULL OR lp.event_date>?)
              AND (lower(lp.customer_name) LIKE ? OR lp.customer_phone LIKE ? OR lower(COALESCE(lp.notes,'')) LIKE ?)
            GROUP BY lp.id ORDER BY lp.event_date IS NULL,lp.event_date,lp.event_time,lp.created_at DESC
            """,archiveDate.toString(),search,search,search);
        return Map.of("inquiries",inquiries,
            "menuOptions",jdbc.queryForList("SELECT id,option_number,name,price_per_head,items_text FROM large_party_menu_option WHERE active=1 ORDER BY option_number"),
            "extraOptions",jdbc.queryForList("SELECT id,name,default_price_per_head,minimum_people FROM extra_dish_option WHERE active=1 ORDER BY name"),
            "extras",jdbc.queryForList("SELECT id,inquiry_id,dish_name,price_per_head,quantity_people,is_custom FROM inquiry_extra_dish ORDER BY id"),
            "payments",jdbc.queryForList("SELECT id,inquiry_id,amount,payment_date,payment_method,taken_by,created_at FROM inquiry_payment ORDER BY payment_date,id"),
            "reminders",jdbc.queryForList("SELECT id,inquiry_id,reminder_date,note,completed,reminder_kind FROM inquiry_reminder ORDER BY completed,reminder_date,id"),
            "areas",jdbc.queryForList("SELECT id,name FROM area WHERE lower(name)!='bar' ORDER BY name"),
            "tables",jdbc.queryForList("SELECT id,number,capacity FROM pub_table WHERE active=1 ORDER BY CAST(number AS INTEGER),number"));
    }

    @PostMapping @Transactional
    public Map<String,Object> create(@RequestBody PartyRequest r,HttpSession s){auth.currentStaffMember(s);validate(r);jdbc.update("""
        INSERT INTO large_party_inquiry(customer_name,customer_phone,event_date,event_time,expected_end_time,reserve_for_rest_of_day,
          party_size,number_of_children,high_chairs_required,food_type,menu_option_id,catered_people,quoted_price_per_head,
          quoted_food_total,deposit_required_amount,deposit_paid_amount,deposit_due_date,deposit_paid_date,deposit_payment_method,
          deposit_taken_by,occasion,notes,status,created_at,updated_at)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """,r.customerName().trim(),phone(r.customerPhone()),r.eventDate(),r.eventTime(),r.expectedEndTime(),r.reserveForRestOfDay(),
        r.partySize(),r.numberOfChildren(),r.highChairsRequired(),blank(r.foodType()),r.menuOptionId(),r.cateredPeople(),menuPrice(r.menuOptionId()),foodTotal(r),
        depositFor(r.partySize()),r.depositPaidAmount(),r.depositDueDate(),r.depositPaidDate(),blank(r.depositPaymentMethod()),blank(r.depositTakenBy()),blank(r.occasion()),blank(r.notes()),r.status());
        Long id=jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);reservations(id,r);syncExtras(id,r.extraDishes());syncDepositReminder(id,r);List<String>warnings=relocateConflicts(id,r);return Map.of("id",id,"warnings",warnings);
    }

    @PutMapping("/{id}") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody PartyRequest r,HttpSession s){auth.currentStaffMember(s);validate(r);int changed=jdbc.update("""
        UPDATE large_party_inquiry SET customer_name=?,customer_phone=?,event_date=?,event_time=?,expected_end_time=?,reserve_for_rest_of_day=?,
          party_size=?,number_of_children=?,high_chairs_required=?,food_type=?,menu_option_id=?,catered_people=?,quoted_price_per_head=?,
          quoted_food_total=?,deposit_required_amount=?,deposit_paid_amount=?,deposit_due_date=?,deposit_paid_date=?,deposit_payment_method=?,
          deposit_taken_by=?,occasion=?,notes=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
        """,r.customerName().trim(),phone(r.customerPhone()),r.eventDate(),r.eventTime(),r.expectedEndTime(),r.reserveForRestOfDay(),r.partySize(),
        r.numberOfChildren(),r.highChairsRequired(),blank(r.foodType()),r.menuOptionId(),r.cateredPeople(),menuPrice(r.menuOptionId()),foodTotal(r),
        depositFor(r.partySize()),r.depositPaidAmount(),r.depositDueDate(),r.depositPaidDate(),blank(r.depositPaymentMethod()),blank(r.depositTakenBy()),
        blank(r.occasion()),blank(r.notes()),r.status(),id);if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Large party not found.");reservations(id,r);syncExtras(id,r.extraDishes());syncDepositReminder(id,r);List<String>warnings=relocateConflicts(id,r);return Map.of("updated",true,"warnings",warnings);}

    @PutMapping("/{id}/cancel")
    public Map<String,Boolean> cancel(@PathVariable long id,HttpSession s){auth.currentStaffMember(s);if(jdbc.update("UPDATE large_party_inquiry SET status='Cancelled',updated_at=CURRENT_TIMESTAMP WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Large party not found.");return Map.of("updated",true);}

    @PostMapping("/{id}/reminders")
    public Map<String,Object> addReminder(@PathVariable long id,@RequestBody ReminderRequest r,HttpSession s){auth.currentStaffMember(s);ensureInquiry(id);if(r.reminderDate()==null||r.note()==null||r.note().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a reminder date and enter a note.");jdbc.update("INSERT INTO inquiry_reminder(inquiry_id,reminder_date,note,completed,created_at,reminder_kind) VALUES(?,?,?,0,CURRENT_TIMESTAMP,'manual')",id,r.reminderDate().toString(),r.note().trim());return Map.of("id",jdbc.queryForObject("SELECT last_insert_rowid()",Long.class));}

    @PutMapping("/reminders/{id}/complete")
    public Map<String,Boolean> completeReminder(@PathVariable long id,HttpSession s){auth.currentStaffMember(s);if(jdbc.update("UPDATE inquiry_reminder SET completed=1 WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reminder not found.");return Map.of("updated",true);}

    @PostMapping("/{id}/extras")
    public Map<String,Object> addExtra(@PathVariable long id,@RequestBody ExtraRequest r,HttpSession s){auth.currentStaffMember(s);ensureSupportingData();ensureInquiry(id);String name=r.dishName();double price=r.pricePerHead();if(r.optionId()!=null){Map<String,Object> option=jdbc.queryForMap("SELECT name,default_price_per_head FROM extra_dish_option WHERE id=? AND active=1",r.optionId());name=option.get("name").toString();if(price<0)price=((Number)option.get("default_price_per_head")).doubleValue();}if(name==null||name.isBlank()||r.quantityPeople()<1||price<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a dish, price and number of people.");jdbc.update("INSERT INTO inquiry_extra_dish(inquiry_id,dish_name,price_per_head,quantity_people,is_custom) VALUES(?,?,?,?,?)",id,name.trim(),price,r.quantityPeople(),r.optionId()==null);return Map.of("id",jdbc.queryForObject("SELECT last_insert_rowid()",Long.class));}

    @PostMapping("/{id}/payments")
    public Map<String,Object> addPayment(@PathVariable long id,@RequestBody PaymentRequest r,HttpSession s){auth.currentStaffMember(s);ensureSupportingData();ensureInquiry(id);if(r.amount()<=0||r.paymentDate()==null||!List.of("Cash","Card").contains(r.paymentMethod())||r.takenBy()==null||r.takenBy().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the amount, payment date, method and staff member.");jdbc.update("INSERT INTO inquiry_payment(inquiry_id,amount,payment_date,payment_method,taken_by,created_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",id,r.amount(),r.paymentDate().toString(),r.paymentMethod(),r.takenBy().trim());return Map.of("id",jdbc.queryForObject("SELECT last_insert_rowid()",Long.class));}

    @DeleteMapping("/extras/{id}")
    public Map<String,Boolean> removeExtra(@PathVariable long id,HttpSession s){auth.currentStaffMember(s);if(jdbc.update("DELETE FROM inquiry_extra_dish WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Extra dish not found.");return Map.of("deleted",true);}

    private void validate(PartyRequest r){if(r.customerName()==null||r.customerName().isBlank()||r.customerPhone()==null||phone(r.customerPhone()).isBlank()||r.partySize()<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name, phone number and party size are required.");if(r.numberOfChildren()<0||r.numberOfChildren()>r.partySize()||r.highChairsRequired()<0||r.highChairsRequired()>r.partySize())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Children and high chairs cannot exceed the party size.");if(r.eventTime()!=null&&!r.reserveForRestOfDay()&&(r.expectedEndTime()==null||!r.expectedEndTime().isAfter(r.eventTime())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter an end time after the start time, or reserve for the rest of the day.");if(r.cateredPeople()!=null&&(r.cateredPeople()<0||r.cateredPeople()>r.partySize()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Catered people cannot exceed the party size.");if(!List.of("Enquiry","Awaiting customer","Provisional","Confirmed").contains(r.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid status.");if(r.depositPaidAmount()>0&&(r.depositPaidDate()==null||!List.of("Cash","Card").contains(r.depositPaymentMethod())||r.depositTakenBy()==null||r.depositTakenBy().isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paid deposits need a date, Cash or Card, and the staff member who took payment.");}
    private void reservations(long id,PartyRequest r){jdbc.update("DELETE FROM large_party_reserved_area WHERE inquiry_id=?",id);jdbc.update("DELETE FROM large_party_reserved_table WHERE inquiry_id=?",id);for(Long area:safe(r.reservedAreaIds()))jdbc.update("INSERT INTO large_party_reserved_area(inquiry_id,area_id) VALUES(?,?)",id,area);for(Long table:safe(r.reservedTableIds()))jdbc.update("INSERT INTO large_party_reserved_table(inquiry_id,table_id) VALUES(?,?)",id,table);}
    private void syncExtras(long inquiryId,List<ExtraRequest> extras){jdbc.update("DELETE FROM inquiry_extra_dish WHERE inquiry_id=?",inquiryId);if(extras==null)return;for(ExtraRequest extra:extras){String name=blank(extra.dishName());double price=extra.pricePerHead();boolean custom=extra.optionId()==null;if(!custom){Map<String,Object> option=jdbc.queryForMap("SELECT name,default_price_per_head FROM extra_dish_option WHERE id=? AND active=1",extra.optionId());name=option.get("name").toString();if(price<0)price=((Number)option.get("default_price_per_head")).doubleValue();}if(name==null||extra.quantityPeople()<1||price<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Every extra dish needs a dish, price and number of people.");jdbc.update("INSERT INTO inquiry_extra_dish(inquiry_id,dish_name,price_per_head,quantity_people,is_custom) VALUES(?,?,?,?,?)",inquiryId,name,price,extra.quantityPeople(),custom);}}
    private void syncDepositReminder(long id,PartyRequest r){jdbc.update("DELETE FROM inquiry_reminder WHERE inquiry_id=? AND reminder_kind='deposit'",id);if(r.depositDueDate()!=null&&r.depositPaidAmount()<depositFor(r.partySize()))jdbc.update("INSERT INTO inquiry_reminder(inquiry_id,reminder_date,note,completed,created_at,reminder_kind) VALUES(?,?,?,0,CURRENT_TIMESTAMP,'deposit')",id,r.depositDueDate().toString(),"Deposit payment is due.");}
    private List<String> relocateConflicts(long inquiryId,PartyRequest r){if(r.eventDate()==null||r.eventTime()==null)return List.of();List<Long> excluded=new java.util.ArrayList<>(safe(r.reservedTableIds()));for(Long area:safe(r.reservedAreaIds()))excluded.addAll(jdbc.query("SELECT id FROM pub_table WHERE area_id=?",(rs,row)->rs.getLong(1),area));if(excluded.isEmpty())return List.of();LocalTime end=r.reserveForRestOfDay()?LocalTime.of(23,59):r.expectedEndTime();String marks=excluded.stream().map(id->"?").collect(java.util.stream.Collectors.joining(","));List<Object> args=new java.util.ArrayList<>();args.add(r.eventDate().toString());args.add(end.toString());args.add(r.eventTime().toString());args.addAll(excluded);List<Map<String,Object>> bookings=jdbc.queryForList("SELECT DISTINCT b.id,b.booking_time,b.party_size,b.is_eating_food,c.name customer_name FROM booking b JOIN customer c ON c.id=b.customer_id JOIN booking_table bt ON bt.booking_id=b.id WHERE b.booking_date=? AND b.status NOT IN ('Cancelled','Completed') AND time(b.booking_time)<time(?) AND time(b.booking_time,'+'||b.duration_minutes||' minutes')>time(?) AND bt.table_id IN ("+marks+")",args.toArray());List<String>warnings=new java.util.ArrayList<>();for(Map<String,Object> booking:bookings){long bookingId=((Number)booking.get("id")).longValue();List<Long> replacement=allocation.suggest(r.eventDate(),LocalTime.parse(booking.get("booking_time").toString()),((Number)booking.get("party_size")).intValue(),Boolean.TRUE.equals(booking.get("is_eating_food"))||booking.get("is_eating_food") instanceof Number n&&n.intValue()!=0,null,null,false,false,bookingId,excluded);if(replacement.isEmpty()){warnings.add(booking.get("customer_name")+" could not be moved automatically.");continue;}jdbc.update("DELETE FROM booking_table WHERE booking_id=?",bookingId);for(Long table:replacement)jdbc.update("INSERT INTO booking_table(booking_id,table_id) VALUES(?,?)",bookingId,table);}return warnings;}
    private void ensureInquiry(long id){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM large_party_inquiry WHERE id=?",Integer.class,id);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Large party not found.");}
    private void ensureSupportingData(){jdbc.execute("""
      CREATE TABLE IF NOT EXISTS inquiry_payment(
        id INTEGER PRIMARY KEY AUTOINCREMENT,inquiry_id INTEGER NOT NULL,amount FLOAT NOT NULL,
        payment_date DATE NOT NULL,payment_method VARCHAR(20) NOT NULL,taken_by VARCHAR(120) NOT NULL,
        created_at DATETIME,FOREIGN KEY(inquiry_id) REFERENCES large_party_inquiry(id))
      """);jdbc.update("UPDATE extra_dish_option SET active=0 WHERE lower(name)=lower('Hot water')");Integer drinks=jdbc.queryForObject("SELECT COUNT(*) FROM extra_dish_option WHERE lower(name)=lower('Tea / coffee')",Integer.class);if(drinks==null||drinks==0)jdbc.update("INSERT INTO extra_dish_option(name,default_price_per_head,minimum_people,active) VALUES('Tea / coffee',1.50,1,1)");else jdbc.update("UPDATE extra_dish_option SET default_price_per_head=1.50,minimum_people=1,active=1 WHERE lower(name)=lower('Tea / coffee')");}
    private Double menuPrice(Long id){return id==null?null:jdbc.query("SELECT price_per_head FROM large_party_menu_option WHERE id=?",rs->rs.next()?(rs.getObject(1)==null?null:rs.getDouble(1)):null,id);} private Double foodTotal(PartyRequest r){Double price=menuPrice(r.menuOptionId());return price==null||r.cateredPeople()==null?null:Math.round(price*r.cateredPeople()*100)/100.0;} private double depositFor(int size){return size>10?Math.min(size*5.0,100):0;}private String phone(String v){return v==null?"":v.replaceAll("\\D","");}private String blank(String v){return v==null||v.isBlank()?null:v.trim();}private List<Long> safe(List<Long> v){return v==null?List.of():v.stream().distinct().toList();}
    public record PartyRequest(String customerName,String customerPhone,LocalDate eventDate,LocalTime eventTime,LocalTime expectedEndTime,boolean reserveForRestOfDay,int partySize,int numberOfChildren,int highChairsRequired,String foodType,Long menuOptionId,Integer cateredPeople,double depositPaidAmount,LocalDate depositDueDate,LocalDate depositPaidDate,String depositPaymentMethod,String depositTakenBy,String occasion,String notes,String status,List<Long> reservedAreaIds,List<Long> reservedTableIds,List<ExtraRequest> extraDishes){}
    public record ReminderRequest(LocalDate reminderDate,String note){}
    public record ExtraRequest(Long optionId,String dishName,double pricePerHead,int quantityPeople){}
    public record PaymentRequest(double amount,LocalDate paymentDate,String paymentMethod,String takenBy){}
}
