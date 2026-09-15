package uk.co.rocketpub.staffportal.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AllergenMenuService {

    private static final String ITEM_SQL = """
            SELECT id, name, category, ingredients,
                   COALESCE(milk_status, 'free') AS milk_status,
                   COALESCE(nuts_status, 'free') AS nuts_status,
                   COALESCE(egg_status, 'free') AS egg_status,
                   COALESCE(gluten_status, 'free') AS gluten_status,
                   vegetarian, can_make_vegetarian, vegetarian_changes,
                   COALESCE(can_make_gluten_free, 0) AS can_make_gluten_free,
                   gluten_free_changes
            FROM allergen_menu_item
            WHERE active = 1
            ORDER BY category, name
            """;

    private final JdbcTemplate jdbcTemplate;

    public AllergenMenuService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AllergenItemView> getMenu() {
        List<MenuRow> rows = jdbcTemplate.query(ITEM_SQL, this::mapItem);
        Map<Long, MenuRow> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.id(), row));

        Map<Long, List<AllergenSideView>> sidesByMeal = new HashMap<>();
        jdbcTemplate.query(
                "SELECT meal_id, side_id FROM allergen_meal_side ORDER BY id",
                result -> {
                    MenuRow side = byId.get(result.getLong("side_id"));
                    if (side != null) {
                        sidesByMeal.computeIfAbsent(
                                result.getLong("meal_id"),
                                ignored -> new ArrayList<>()
                        ).add(side.asSide());
                    }
                }
        );

        return rows.stream()
                .map(row -> row.asItem(sidesByMeal.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private MenuRow mapItem(ResultSet result, int rowNumber) throws SQLException {
        return new MenuRow(
                result.getLong("id"),
                result.getString("name"),
                result.getString("category"),
                result.getString("ingredients"),
                result.getString("milk_status"),
                result.getString("nuts_status"),
                result.getString("egg_status"),
                result.getString("gluten_status"),
                result.getBoolean("vegetarian"),
                result.getBoolean("can_make_vegetarian"),
                result.getString("vegetarian_changes"),
                result.getBoolean("can_make_gluten_free"),
                result.getString("gluten_free_changes")
        );
    }

    private record MenuRow(
            long id,
            String name,
            String category,
            String ingredients,
            String milkStatus,
            String nutsStatus,
            String eggStatus,
            String glutenStatus,
            boolean vegetarian,
            boolean canMakeVegetarian,
            String vegetarianChanges,
            boolean canMakeGlutenFree,
            String glutenFreeChanges) {

        AllergenSideView asSide() {
            return new AllergenSideView(
                    id, name, milkStatus, nutsStatus, eggStatus, glutenStatus,
                    vegetarian, canMakeVegetarian, canMakeGlutenFree
            );
        }

        AllergenItemView asItem(List<AllergenSideView> sides) {
            return new AllergenItemView(
                    id, name, category, ingredients,
                    milkStatus, nutsStatus, eggStatus, glutenStatus,
                    vegetarian, canMakeVegetarian, vegetarianChanges,
                    canMakeGlutenFree, glutenFreeChanges, sides
            );
        }
    }
}
