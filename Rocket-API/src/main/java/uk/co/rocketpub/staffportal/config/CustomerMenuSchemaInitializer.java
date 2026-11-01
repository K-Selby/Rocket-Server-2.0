package uk.co.rocketpub.staffportal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CustomerMenuSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public CustomerMenuSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS customer_menu (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                slug TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                description TEXT,
                file_name TEXT NOT NULL,
                is_primary INTEGER NOT NULL DEFAULT 0,
                display_order INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        addColumns(Set.of(
                "button_text TEXT",
                "card_color TEXT NOT NULL DEFAULT '#ffffff'",
                "text_color TEXT NOT NULL DEFAULT '#173326'",
                "sidebar_name TEXT",
                "icon TEXT NOT NULL DEFAULT 'pdf'"));

        jdbc.update("""
            INSERT OR IGNORE INTO customer_menu
                (slug, title, description, file_name, is_primary, display_order)
            VALUES ('food', 'Food Menu', 'View our current food menu.',
                'the-rocket-pub-food-menu.pdf', 1, 10)
            """);
        jdbc.update("""
            INSERT OR IGNORE INTO customer_menu
                (slug, title, description, file_name, is_primary, display_order)
            VALUES ('christmas', 'Christmas Menu',
                'Prebook your Christmas dinner with us.',
                'rocket-christmas-menu.pdf', 0, 20)
            """);
        jdbc.update("""
            UPDATE customer_menu
            SET button_text = 'View Food Menu', card_color = '#ffffff', text_color = '#173326'
            WHERE slug = 'food' AND button_text IS NULL
            """);
        jdbc.update("""
            UPDATE customer_menu
            SET button_text = 'View Our Christmas Menu 🎄',
                card_color = '#7f202a', text_color = '#fffaf0'
            WHERE slug = 'christmas' AND button_text IS NULL
            """);
        jdbc.update("UPDATE customer_menu SET sidebar_name = title WHERE sidebar_name IS NULL");
        jdbc.update("UPDATE customer_menu SET title = button_text WHERE button_text IS NOT NULL");
        jdbc.update("UPDATE customer_menu SET icon = 'star' WHERE slug = 'christmas' AND icon = 'pdf'");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS customer_portal_template (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                menu_json TEXT NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS customer_portal_button (
                button_key TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                sidebar_name TEXT NOT NULL,
                icon TEXT NOT NULL,
                card_color TEXT NOT NULL,
                text_color TEXT NOT NULL
            )
            """);
        jdbc.update("""
            INSERT OR IGNORE INTO customer_portal_button
                (button_key, title, description, sidebar_name, icon, card_color, text_color)
            VALUES ('allergens', 'Allergen Menu', 'Search dishes and check allergen information.',
                'Allergens', 'material:allergy', '#ffffff', '#173326')
            """);
    }

    private void addColumns(Set<String> definitions) {
        Set<String> existing = jdbc.queryForList("PRAGMA table_info(customer_menu)").stream()
                .map(row -> String.valueOf(row.get("name"))).collect(Collectors.toSet());
        for (String definition : definitions) {
            String name = definition.substring(0, definition.indexOf(' '));
            if (!existing.contains(name)) jdbc.execute("ALTER TABLE customer_menu ADD COLUMN " + definition);
        }
    }
}
