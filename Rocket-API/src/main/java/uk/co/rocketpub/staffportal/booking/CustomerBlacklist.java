package uk.co.rocketpub.staffportal.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CustomerBlacklist {
    private CustomerBlacklist() {}
    static void check(JdbcTemplate jdbc, String phone) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS customer_blacklist (phone TEXT PRIMARY KEY, name TEXT NOT NULL, reason TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        String normalized=phone==null?"":phone.replaceAll("\\D","");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM customer_blacklist WHERE phone=?",Integer.class,normalized);
        if(count!=null&&count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This customer phone number is blacklisted. Review the Blacklist in Archive before taking a booking.");
    }
}
