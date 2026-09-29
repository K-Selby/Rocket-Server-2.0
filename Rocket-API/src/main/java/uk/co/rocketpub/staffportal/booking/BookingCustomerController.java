package uk.co.rocketpub.staffportal.booking;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;

@RestController
@RequestMapping("/api/booking/customers")
public class BookingCustomerController {

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public BookingCustomerController(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String q,
            HttpSession session) {

        authService.currentStaffMember(session);
        String search = "%" + q.trim().toLowerCase() + "%";
        List<Map<String, Object>> customers = jdbc.queryForList("""
                SELECT c.id, c.name, c.phone, c.email, c.notes,
                       c.preferred_area_id, a.name AS preferred_area,
                       c.preferred_table_id, pt.number AS preferred_table,
                       COALESCE(c.prefers_near_tv, 0) AS prefers_near_tv,
                       COALESCE(c.avoids_bench, 0) AS avoids_bench,
                       COUNT(DISTINCT b.id) AS booking_count,
                       MAX(b.booking_date) AS last_booking_date
                FROM customer c
                LEFT JOIN area a ON a.id = c.preferred_area_id
                LEFT JOIN pub_table pt ON pt.id = c.preferred_table_id
                LEFT JOIN booking b ON b.customer_id = c.id
                WHERE lower(c.name) LIKE ? OR c.phone LIKE ?
                GROUP BY c.id
                ORDER BY lower(c.name), c.id
                """, search, search);

        return Map.of(
                "customers", customers,
                "areas", jdbc.queryForList("SELECT id, name FROM area ORDER BY name"),
                "tables", jdbc.queryForList("SELECT id, number, capacity FROM pub_table WHERE active=1 ORDER BY CAST(number AS INTEGER), number")
        );
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Boolean> update(
            @PathVariable long id,
            @RequestBody CustomerUpdate request,
            HttpSession session) {

        authService.currentStaffMember(session);
        String name = request.name() == null ? "" : request.name().trim();
        String phone = request.phone() == null ? "" : request.phone().replaceAll("\\D", "");

        if (name.isBlank() || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name and phone number are required.");
        }

        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE phone=? AND id!=?",
                Integer.class, phone, id);
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another customer already uses that phone number.");
        }

        int changed = jdbc.update("""
                UPDATE customer SET name=?, phone=?, email=?, preferred_area_id=?,
                    preferred_table_id=?, prefers_near_tv=?, avoids_bench=?, notes=?
                WHERE id=?
                """, name, phone, blankToNull(request.email()), request.preferredAreaId(),
                request.preferredTableId(), request.prefersNearTv(), request.avoidsBench(),
                blankToNull(request.notes()), id);

        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found.");
        return Map.of("updated", true);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Boolean> delete(
            @PathVariable long id,
            HttpSession session) {

        authService.currentStaffMember(session);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE id=?",
                Integer.class,
                id);

        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found.");
        }

        jdbc.update("""
                DELETE FROM repeat_booking_occurrence
                WHERE repeat_booking_id IN (
                    SELECT id FROM repeat_booking WHERE customer_id=?
                ) OR booking_id IN (
                    SELECT id FROM booking WHERE customer_id=?
                )
                """, id, id);
        jdbc.update("""
                DELETE FROM booking_table
                WHERE booking_id IN (
                    SELECT id FROM booking WHERE customer_id=?
                )
                """, id);
        jdbc.update("DELETE FROM booking WHERE customer_id=?", id);
        jdbc.update("DELETE FROM repeat_booking WHERE customer_id=?", id);
        jdbc.update("DELETE FROM customer WHERE id=?", id);

        return Map.of("deleted", true);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CustomerUpdate(
            String name,
            String phone,
            String email,
            Long preferredAreaId,
            Long preferredTableId,
            boolean prefersNearTv,
            boolean avoidsBench,
            String notes) {
    }
}
