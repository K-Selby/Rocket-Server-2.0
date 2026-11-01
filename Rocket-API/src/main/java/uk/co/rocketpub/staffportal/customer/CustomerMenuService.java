package uk.co.rocketpub.staffportal.customer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerMenuService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Value("${rocket.menu.path:../data/assets/menus}")
    private String menuPath;

    public CustomerMenuService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<CustomerMenuView> list() {
        return jdbc.query("""
            SELECT id, slug, title, description, is_primary, sidebar_name, icon, card_color, text_color
            FROM customer_menu
            ORDER BY display_order, title COLLATE NOCASE
            """, (result, row) -> new CustomerMenuView(
                result.getLong("id"),
                result.getString("slug"),
                result.getString("title"),
                result.getString("description"),
                result.getBoolean("is_primary"),
                fallback(result.getString("sidebar_name"), result.getString("title")),
                fallback(result.getString("icon"), "pdf"),
                fallback(result.getString("card_color"), "#ffffff"),
                fallback(result.getString("text_color"), "#173326"),
                "/api/customer/menus/" + result.getString("slug") + "/pdf"));
    }

    public List<CustomerPortalButtonView> portalButtons() {
        return jdbc.query("""
            SELECT button_key, title, description, sidebar_name, icon, card_color, text_color
            FROM customer_portal_button ORDER BY button_key
            """, (result, row) -> new CustomerPortalButtonView(
                result.getString("button_key"), result.getString("title"), result.getString("description"),
                result.getString("sidebar_name"), result.getString("icon"), result.getString("card_color"),
                result.getString("text_color")));
    }

    public CustomerPortalButtonView updatePortalButton(String key, String title, String description,
            String sidebarName, String icon, String cardColor, String textColor) {
        int changed = jdbc.update("""
            UPDATE customer_portal_button SET title = ?, description = ?, sidebar_name = ?, icon = ?,
                card_color = ?, text_color = ? WHERE button_key = ?
            """, requiredTitle(title), blank(description), text(sidebarName, title), icon(icon),
                color(cardColor, "#ffffff"), color(textColor, "#173326"), key);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portal button not found.");
        return portalButtons().stream().filter(button -> button.key().equals(key)).findFirst().orElseThrow();
    }

    public CustomerMenuView create(String title, String description, String sidebarName, String icon,
            String cardColor, String textColor, MultipartFile pdf) {
        String cleanTitle = requiredTitle(title);
        validatePdf(pdf, true);
        String slug = uniqueSlug(slugify(cleanTitle));
        String fileName = storePdf(pdf, slug);
        Integer order = jdbc.queryForObject(
                "SELECT COALESCE(MAX(display_order), 0) + 10 FROM customer_menu", Integer.class);
        jdbc.update("""
            INSERT INTO customer_menu
                (slug, title, description, button_text, sidebar_name, icon, card_color, text_color,
                 file_name, is_primary, display_order, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, CURRENT_TIMESTAMP)
            """, slug, cleanTitle, blank(description), cleanTitle,
                text(sidebarName, cleanTitle), icon(icon),
                color(cardColor, "#ffffff"), color(textColor, "#173326"), fileName, order);
        return find(slug);
    }

    public CustomerMenuView update(long id, String title, String description, String sidebarName, String icon,
            String cardColor, String textColor, MultipartFile pdf) {
        MenuFile current = findFile(id);
        String cleanTitle = requiredTitle(title);
        String fileName = current.fileName();

        if (pdf != null && !pdf.isEmpty()) {
            validatePdf(pdf, false);
            fileName = storePdf(pdf, current.slug());
        }

        jdbc.update("""
            UPDATE customer_menu
            SET title = ?, description = ?, button_text = ?, sidebar_name = ?, icon = ?, card_color = ?, text_color = ?,
                file_name = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, cleanTitle, blank(description), cleanTitle, text(sidebarName, cleanTitle), icon(icon),
                color(cardColor, "#ffffff"), color(textColor, "#173326"), fileName, id);

        return find(current.slug());
    }

    public void delete(long id) {
        MenuFile current = findFile(id);
        if (current.primary()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The primary food menu cannot be removed.");
        }
        jdbc.update("DELETE FROM customer_menu WHERE id = ?", id);
    }

    public PortalTemplateView createTemplate() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM customer_menu ORDER BY display_order, id");
        CustomerPortalButtonView allergens = portalButtons().stream().filter(button -> button.key().equals("allergens")).findFirst().orElseThrow();
        Map<String, Object> allergenRow = new HashMap<>();
        allergenRow.put("id", -9007199254740991L); allergenRow.put("slug", "allergens");
        allergenRow.put("title", allergens.title()); allergenRow.put("description", allergens.description());
        allergenRow.put("is_primary", 1); allergenRow.put("sidebar_name", allergens.sidebarName());
        allergenRow.put("icon", allergens.icon()); allergenRow.put("card_color", allergens.cardColor());
        allergenRow.put("text_color", allergens.textColor()); allergenRow.put("file_name", "");
        allergenRow.put("display_order", 15); allergenRow.put("button_text", allergens.title());
        rows.add(allergenRow);
        try {
            jdbc.update("INSERT INTO customer_portal_template (menu_json) VALUES (?)", json.writeValueAsString(rows));
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the portal version.");
        }
        return jdbc.query("SELECT id, created_at FROM customer_portal_template ORDER BY id DESC LIMIT 1",
                (result, row) -> new PortalTemplateView(result.getLong("id"), result.getString("created_at")))
                .getFirst();
    }

    public List<PortalTemplateView> templates() {
        return jdbc.query("SELECT id, created_at FROM customer_portal_template ORDER BY id DESC LIMIT 30",
                (result, row) -> new PortalTemplateView(result.getLong("id"), result.getString("created_at")));
    }

    public List<CustomerMenuView> templateMenus(long id) {
        String stored = templateJson(id);
        try {
            List<Map<String, Object>> rows = json.readValue(stored, new TypeReference<>() {});
            return rows.stream().map(row -> new CustomerMenuView(
                    ((Number) row.get("id")).longValue(), String.valueOf(row.get("slug")),
                    String.valueOf(row.get("title")), row.get("description") == null ? null : String.valueOf(row.get("description")),
                    numberBoolean(row.get("is_primary")), fallback(value(row.get("sidebar_name")), value(row.get("title"))),
                    fallback(value(row.get("icon")), "pdf"), fallback(value(row.get("card_color")), "#ffffff"),
                    fallback(value(row.get("text_color")), "#173326"),
                    "/api/customer/menus/" + row.get("slug") + "/pdf")).toList();
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The saved version could not be previewed.");
        }
    }

    public void restoreTemplate(long id) {
        String stored = templateJson(id);
        try {
            List<Map<String, Object>> rows = json.readValue(stored, new TypeReference<>() {});
            jdbc.update("DELETE FROM customer_menu");
            for (Map<String, Object> row : rows) {
                if ("allergens".equals(String.valueOf(row.get("slug")))) {
                    updatePortalButton("allergens", value(row.get("title")), value(row.get("description")),
                            value(row.get("sidebar_name")), value(row.get("icon")), value(row.get("card_color")),
                            value(row.get("text_color")));
                    continue;
                }
                jdbc.update("""
                    INSERT INTO customer_menu
                        (id, slug, title, description, file_name, is_primary, display_order, updated_at,
                         button_text, card_color, text_color, sidebar_name, icon)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, row.get("id"), row.get("slug"), row.get("title"), row.get("description"),
                        row.get("file_name"), row.get("is_primary"), row.get("display_order"), row.get("updated_at"),
                        row.get("button_text"), row.get("card_color"), row.get("text_color"),
                        row.get("sidebar_name"), row.get("icon"));
            }
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The saved version could not be restored.");
        }
    }

    private String templateJson(long id) {
        return jdbc.query("SELECT menu_json FROM customer_portal_template WHERE id = ?",
                (result, row) -> result.getString("menu_json"), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved version not found."));
    }

    private static String value(Object value) { return value == null ? null : String.valueOf(value); }
    private static boolean numberBoolean(Object value) { return value instanceof Number number ? number.intValue() != 0 : Boolean.parseBoolean(String.valueOf(value)); }

    public Resource pdf(String slug) {
        MenuFile menu = findFile(slug);
        Path file = root().resolve(menu.fileName()).normalize();
        if (!file.startsWith(root()) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu PDF not found.");
        }
        return new FileSystemResource(file);
    }

    public CustomerMenuView find(String slug) {
        return jdbc.query("""
            SELECT id, slug, title, description, is_primary, sidebar_name, icon, card_color, text_color
            FROM customer_menu WHERE slug = ?
            """, (result, row) -> new CustomerMenuView(
                result.getLong("id"), result.getString("slug"),
                result.getString("title"), result.getString("description"),
                result.getBoolean("is_primary"),
                fallback(result.getString("sidebar_name"), result.getString("title")),
                fallback(result.getString("icon"), "pdf"),
                fallback(result.getString("card_color"), "#ffffff"),
                fallback(result.getString("text_color"), "#173326"),
                "/api/customer/menus/" + result.getString("slug") + "/pdf"), slug)
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found."));
    }

    private MenuFile findFile(long id) {
        return jdbc.query("SELECT id, slug, file_name, is_primary FROM customer_menu WHERE id = ?",
                (result, row) -> new MenuFile(result.getLong("id"), result.getString("slug"),
                        result.getString("file_name"), result.getBoolean("is_primary")), id)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found."));
    }

    private MenuFile findFile(String slug) {
        return jdbc.query("SELECT id, slug, file_name, is_primary FROM customer_menu WHERE slug = ?",
                (result, row) -> new MenuFile(result.getLong("id"), result.getString("slug"),
                        result.getString("file_name"), result.getBoolean("is_primary")), slug)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found."));
    }

    private String storePdf(MultipartFile pdf, String slug) {
        try {
            Files.createDirectories(root());
            String fileName = slug + "-" + Instant.now().toEpochMilli() + ".pdf";
            Path temporary = Files.createTempFile(root(), slug + "-", ".tmp");
            try {
                pdf.transferTo(temporary);
                Files.move(temporary, root().resolve(fileName),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return fileName;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the menu PDF.");
        }
    }

    private void validatePdf(MultipartFile pdf, boolean required) {
        if (pdf == null || pdf.isEmpty()) {
            if (required) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a PDF file.");
            return;
        }
        if (pdf.getSize() > 20L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The PDF must be smaller than 20 MB.");
        }
        try {
            byte[] header = pdf.getInputStream().readNBytes(5);
            if (header.length != 5 || header[0] != '%' || header[1] != 'P'
                    || header[2] != 'D' || header[3] != 'F' || header[4] != '-') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files can be uploaded.");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The PDF could not be read.");
        }
    }

    private String uniqueSlug(String base) {
        String candidate = base;
        int suffix = 2;
        while (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM customer_menu WHERE slug = ?)", Boolean.class, candidate))) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String title) {
        String slug = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "menu" : slug;
    }

    private String requiredTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a menu name.");
        }
        return title.trim();
    }

    private String blank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String icon(String value) {
        String clean = text(value, "pdf");
        if (clean.matches("material:[a-z0-9_]{1,64}")) return clean;
        return clean.length() <= 64 ? clean : "pdf";
    }

    private String color(String value, String fallback) {
        if (value == null || !value.trim().matches("#[0-9a-fA-F]{6}")) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Path root() {
        return Path.of(menuPath).toAbsolutePath().normalize();
    }

    private void deleteIfUnused(String fileName) {
        Integer references = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_menu WHERE file_name = ?", Integer.class, fileName);
        if (references != null && references == 0) {
            try {
                Files.deleteIfExists(root().resolve(fileName));
            } catch (IOException ignored) {
                // The catalogue is already correct; an old file can be cleaned up later.
            }
        }
    }

    private record MenuFile(long id, String slug, String fileName, boolean primary) {}
}
