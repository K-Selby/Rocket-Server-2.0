package uk.co.rocketpub.staffportal.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.auth.AuthUser;
import uk.co.rocketpub.staffportal.model.StaffRole;

@RestController
@RequestMapping("/api/booking/archive")
public class BookingArchiveController {
    private final JdbcTemplate jdbc; private final AuthService auth;
    public BookingArchiveController(JdbcTemplate jdbc,AuthService auth){this.jdbc=jdbc;this.auth=auth;}

    @GetMapping
    public Map<String,Object> archive(@RequestParam(defaultValue="") String q,HttpSession session){requireManager(session);String search="%"+q.trim().toLowerCase()+"%";
        return Map.of(
          "cancelledBookings",bookings("b.status='Cancelled'",search),
          "pastBookings",bookings("b.status!='Cancelled' AND b.booking_date<?",search,LocalDate.now().toString()),
          "cancelledLargeParties",largeParties("lp.status='Cancelled'",search),
          "pastLargeParties",largeParties("lp.status!='Cancelled' AND lp.event_date IS NOT NULL AND lp.event_date<=?",search,LocalDate.now().minusDays(2).toString()));
    }

    @PutMapping("/bookings/{id}/restore")
    public Map<String,Boolean> restoreBooking(@PathVariable long id,HttpSession session){requireManager(session);if(jdbc.update("UPDATE booking SET status='Booked',completed_at=NULL WHERE id=? AND status='Cancelled'",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cancelled booking not found.");return Map.of("restored",true);}

    @PutMapping("/large-parties/{id}/restore")
    public Map<String,Boolean> restoreLargeParty(@PathVariable long id,HttpSession session){requireManager(session);if(jdbc.update("UPDATE large_party_inquiry SET status='Enquiry',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='Cancelled'",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cancelled large party not found.");return Map.of("restored",true);}

    private List<Map<String,Object>> bookings(String condition,String search,Object... extra){String sql="""
      SELECT b.id,b.booking_date event_date,b.booking_time event_time,b.party_size,b.status,b.occasion,b.notes,
        c.name customer_name,c.phone customer_phone,COALESCE(GROUP_CONCAT(pt.number,', '),'') table_numbers
      FROM booking b JOIN customer c ON c.id=b.customer_id LEFT JOIN booking_table bt ON bt.booking_id=b.id
      LEFT JOIN pub_table pt ON pt.id=bt.table_id WHERE %s
        AND (lower(c.name) LIKE ? OR c.phone LIKE ? OR lower(COALESCE(b.notes,'')) LIKE ?)
      GROUP BY b.id ORDER BY b.booking_date DESC,b.booking_time DESC LIMIT 250
      """.formatted(condition);Object[] args=new Object[extra.length+3];System.arraycopy(extra,0,args,0,extra.length);args[extra.length]=search;args[extra.length+1]=search;args[extra.length+2]=search;return jdbc.queryForList(sql,args);}
    private List<Map<String,Object>> largeParties(String condition,String search,Object... extra){String sql="""
      SELECT lp.id,lp.event_date,lp.event_time,lp.party_size,lp.status,lp.occasion,lp.notes,
        lp.customer_name,lp.customer_phone,COALESCE(lp.deposit_required_amount,0) deposit_required_amount,
        COALESCE(lp.deposit_paid_amount,0) deposit_paid_amount
      FROM large_party_inquiry lp WHERE %s
        AND (lower(lp.customer_name) LIKE ? OR lp.customer_phone LIKE ? OR lower(COALESCE(lp.notes,'')) LIKE ?)
      ORDER BY lp.event_date DESC,lp.event_time DESC,lp.created_at DESC LIMIT 250
      """.formatted(condition);Object[] args=new Object[extra.length+3];System.arraycopy(extra,0,args,0,extra.length);args[extra.length]=search;args[extra.length+1]=search;args[extra.length+2]=search;return jdbc.queryForList(sql,args);}
    private void requireManager(HttpSession session){AuthUser user=auth.currentUser(session);if(user.role()!=StaffRole.MANAGER&&user.role()!=StaffRole.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager access required.");}
}
