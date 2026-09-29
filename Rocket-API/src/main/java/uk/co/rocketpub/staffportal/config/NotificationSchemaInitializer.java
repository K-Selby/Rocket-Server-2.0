package uk.co.rocketpub.staffportal.config;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class NotificationSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public NotificationSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void run(ApplicationArguments args) {
        addColumns("app_user", Set.of("notifications_enabled INTEGER NOT NULL DEFAULT 1", "notify_request_decisions INTEGER NOT NULL DEFAULT 1", "notify_shift_swaps INTEGER NOT NULL DEFAULT 1", "notify_published_rotas INTEGER NOT NULL DEFAULT 1"));
        addColumns("staff_settings", Set.of("notifications_enabled INTEGER NOT NULL DEFAULT 1"));
    }
    private void addColumns(String table, Set<String> definitions) {
        Set<String> existing = jdbc.queryForList("PRAGMA table_info(" + table + ")").stream().map(row -> String.valueOf(row.get("name"))).collect(Collectors.toSet());
        for (String definition : definitions) {
            String name = definition.substring(0, definition.indexOf(' '));
            if (!existing.contains(name)) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + definition);
        }
    }
}
