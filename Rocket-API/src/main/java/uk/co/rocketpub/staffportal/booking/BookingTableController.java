package uk.co.rocketpub.staffportal.booking;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.auth.AuthUser;
import uk.co.rocketpub.staffportal.model.StaffRole;

@RestController
@RequestMapping("/api/booking/tables")
public class BookingTableController {
    private final JdbcTemplate jdbc; private final AuthService auth;
    public BookingTableController(JdbcTemplate jdbc,AuthService auth){this.jdbc=jdbc;this.auth=auth;}

    @GetMapping
    public Map<String,Object> list(HttpSession session){requireManager(session);return Map.of(
        "tables",jdbc.queryForList("""
          SELECT pt.id,pt.number,pt.capacity,pt.area_id,a.name area,COALESCE(pt.near_tv,0) near_tv,
            COALESCE(pt.has_bench,0) has_bench,COALESCE(pt.accessible,0) accessible,
            COALESCE(pt.unsuitable_for_food,0) unsuitable_for_food,COALESCE(pt.active,1) active
          FROM pub_table pt JOIN area a ON a.id=pt.area_id ORDER BY CAST(pt.number AS INTEGER),pt.number
          """),
        "areas",jdbc.queryForList("SELECT id,name FROM area ORDER BY name"),
        "pairings",jdbc.queryForList("""
          SELECT p.id,p.table_a_id,p.table_b_id,a.number table_a_number,b.number table_b_number,
            a.capacity+b.capacity combined_capacity FROM table_pairing p
          JOIN pub_table a ON a.id=p.table_a_id JOIN pub_table b ON b.id=p.table_b_id
          ORDER BY CAST(a.number AS INTEGER),CAST(b.number AS INTEGER)
          """));}

    @PostMapping @Transactional
    public Map<String,Object> create(@RequestBody TableRequest r,HttpSession session){requireManager(session);validate(r,null);jdbc.update("""
      INSERT INTO pub_table(number,capacity,area_id,near_tv,has_bench,accessible,unsuitable_for_food,active,
        x_position,y_position,layout_width,layout_height,layout_shape,layout_rotation)
      VALUES(?,?,?,?,?,?,?,?,40,40,90,60,'rectangle',0)
      """,r.number().trim(),r.capacity(),r.areaId(),r.nearTv(),r.hasBench(),r.accessible(),r.unsuitableForFood(),true);return Map.of("id",jdbc.queryForObject("SELECT last_insert_rowid()",Long.class));}

    @PutMapping("/{id}")
    public Map<String,Boolean> update(@PathVariable long id,@RequestBody TableRequest r,HttpSession session){requireManager(session);validate(r,id);int changed=jdbc.update("""
      UPDATE pub_table SET number=?,capacity=?,area_id=?,near_tv=?,has_bench=?,accessible=?,unsuitable_for_food=?,active=? WHERE id=?
      """,r.number().trim(),r.capacity(),r.areaId(),r.nearTv(),r.hasBench(),r.accessible(),r.unsuitableForFood(),r.active(),id);if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found.");return Map.of("updated",true);}

    @PostMapping("/pairings")
    public Map<String,Object> pair(@RequestBody PairRequest r,HttpSession session){requireManager(session);if(r.tableAId()==r.tableBId())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose two different tables.");long first=Math.min(r.tableAId(),r.tableBId()),second=Math.max(r.tableAId(),r.tableBId());Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM table_pairing WHERE table_a_id=? AND table_b_id=?",Integer.class,first,second);if(count!=null&&count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Those tables are already paired.");jdbc.update("INSERT INTO table_pairing(table_a_id,table_b_id) VALUES(?,?)",first,second);return Map.of("id",jdbc.queryForObject("SELECT last_insert_rowid()",Long.class));}

    @DeleteMapping("/pairings/{id}")
    public Map<String,Boolean> unpair(@PathVariable long id,HttpSession session){requireManager(session);if(jdbc.update("DELETE FROM table_pairing WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pairing not found.");return Map.of("deleted",true);}

    private void validate(TableRequest r,Long id){if(r.number()==null||r.number().isBlank()||r.capacity()<1||r.areaId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Table number, capacity and area are required.");Integer duplicate=jdbc.queryForObject("SELECT COUNT(*) FROM pub_table WHERE number=? AND (? IS NULL OR id!=?)",Integer.class,r.number().trim(),id,id);if(duplicate!=null&&duplicate>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Another table already uses that number.");Integer area=jdbc.queryForObject("SELECT COUNT(*) FROM area WHERE id=?",Integer.class,r.areaId());if(area==null||area==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid area.");}
    private void requireManager(HttpSession session){AuthUser user=auth.currentUser(session);if(user.role()!=StaffRole.MANAGER&&user.role()!=StaffRole.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager access required.");}
    public record TableRequest(String number,int capacity,Long areaId,boolean nearTv,boolean hasBench,boolean accessible,boolean unsuitableForFood,boolean active){}
    public record PairRequest(long tableAId,long tableBId){}
}
