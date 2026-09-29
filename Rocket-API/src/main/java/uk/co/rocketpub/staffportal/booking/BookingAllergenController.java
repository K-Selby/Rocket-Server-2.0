package uk.co.rocketpub.staffportal.booking;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.auth.AuthUser;
import uk.co.rocketpub.staffportal.model.StaffRole;

@RestController
@RequestMapping("/api/booking/allergens")
public class BookingAllergenController {
    private static final Set<String> STATUSES=Set.of("free","contains","may_contain");
    private final JdbcTemplate jdbc; private final AuthService auth;
    public BookingAllergenController(JdbcTemplate jdbc,AuthService auth){this.jdbc=jdbc;this.auth=auth;}

    @GetMapping
    public Map<String,Object> list(HttpSession session){AuthUser user=auth.currentUser(session);return Map.of("manager",isManager(user),"items",jdbc.queryForList("""
      SELECT id,name,category,description,ingredients,milk_status,nuts_status,egg_status,gluten_status,
        vegetarian,can_make_vegetarian,vegetarian_changes,can_make_gluten_free,gluten_free_changes,active
      FROM allergen_menu_item ORDER BY active DESC,category,name
      """));}

    @PostMapping
    public Map<String,Object> create(@RequestBody ItemRequest r,HttpSession session){requireManager(session);validate(r,null);jdbc.update("""
      INSERT INTO allergen_menu_item(name,category,description,ingredients,contains_milk,contains_nuts,contains_egg,
        contains_gluten,vegetarian,can_make_vegetarian,vegetarian_changes,active,created_at,updated_at,milk_status,
        nuts_status,egg_status,gluten_status,can_make_gluten_free,gluten_free_changes)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?,?,?,?)
      """,r.name().trim(),r.category().trim(),blank(r.description()),blank(r.ingredients()),contains(r.milkStatus()),contains(r.nutsStatus()),contains(r.eggStatus()),contains(r.glutenStatus()),r.vegetarian(),r.canMakeVegetarian(),blank(r.vegetarianChanges()),true,r.milkStatus(),r.nutsStatus(),r.eggStatus(),r.glutenStatus(),r.canMakeGlutenFree(),blank(r.glutenFreeChanges()));return Map.of("id",jdbc.queryForObject("SELECT last_insert_rowid()",Long.class));}

    @PutMapping("/{id}")
    public Map<String,Boolean> update(@PathVariable long id,@RequestBody ItemRequest r,HttpSession session){requireManager(session);validate(r,id);int changed=jdbc.update("""
      UPDATE allergen_menu_item SET name=?,category=?,description=?,ingredients=?,contains_milk=?,contains_nuts=?,
        contains_egg=?,contains_gluten=?,vegetarian=?,can_make_vegetarian=?,vegetarian_changes=?,milk_status=?,
        nuts_status=?,egg_status=?,gluten_status=?,can_make_gluten_free=?,gluten_free_changes=?,active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
      """,r.name().trim(),r.category().trim(),blank(r.description()),blank(r.ingredients()),contains(r.milkStatus()),contains(r.nutsStatus()),contains(r.eggStatus()),contains(r.glutenStatus()),r.vegetarian(),r.canMakeVegetarian(),blank(r.vegetarianChanges()),r.milkStatus(),r.nutsStatus(),r.eggStatus(),r.glutenStatus(),r.canMakeGlutenFree(),blank(r.glutenFreeChanges()),r.active(),id);if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item not found.");return Map.of("updated",true);}

    private void validate(ItemRequest r,Long id){if(r.name()==null||r.name().isBlank()||r.category()==null||r.category().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name and category are required.");if(!STATUSES.containsAll(List.of(r.milkStatus(),r.nutsStatus(),r.eggStatus(),r.glutenStatus())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid allergen status.");Integer duplicate=jdbc.queryForObject("SELECT COUNT(*) FROM allergen_menu_item WHERE lower(name)=lower(?) AND (? IS NULL OR id!=?)",Integer.class,r.name().trim(),id,id);if(duplicate!=null&&duplicate>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A menu item already uses that name.");}
    private boolean contains(String status){return "contains".equals(status);}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private boolean isManager(AuthUser user){return user.role()==StaffRole.MANAGER||user.role()==StaffRole.ADMIN;}
    private void requireManager(HttpSession session){if(!isManager(auth.currentUser(session)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager access required.");}
    public record ItemRequest(String name,String category,String description,String ingredients,String milkStatus,String nutsStatus,String eggStatus,String glutenStatus,boolean vegetarian,boolean canMakeVegetarian,String vegetarianChanges,boolean canMakeGlutenFree,String glutenFreeChanges,boolean active){}
}
