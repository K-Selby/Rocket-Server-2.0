package uk.co.rocketpub.staffportal.booking;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RequestMapping("/api/booking/layout")
public class BookingLayoutController {
    private static final Set<String> SHAPES=Set.of("rectangle","square","round","oval");
    private static final Set<String> TYPES=Set.of("wall","door","bar","pillar","tv","fixed_table","label","area");
    private final JdbcTemplate jdbc;private final AuthService auth;
    public BookingLayoutController(JdbcTemplate jdbc,AuthService auth){this.jdbc=jdbc;this.auth=auth;}

    @GetMapping
    public Map<String,Object> get(HttpSession session){requireManager(session);ensureSettings();Map<String,Object> settings=jdbc.queryForMap("SELECT canvas_width,canvas_height,background_note FROM floor_plan_setting WHERE name='main'");return Map.of(
      "settings",settings,"areas",jdbc.queryForList("SELECT id,name FROM area ORDER BY name"),
      "tables",jdbc.queryForList("""
        SELECT pt.id,pt.number,pt.capacity,pt.area_id,a.name area,COALESCE(pt.active,1) active,
        COALESCE(pt.x_position,40) x,COALESCE(pt.y_position,40) y,COALESCE(pt.layout_width,90) width,
        COALESCE(pt.layout_height,60) height,COALESCE(pt.layout_shape,'rectangle') shape,COALESCE(pt.layout_rotation,0) rotation
        FROM pub_table pt JOIN area a ON a.id=pt.area_id ORDER BY CAST(pt.number AS INTEGER),pt.number
        """),
      "objects",jdbc.queryForList("""
        SELECT id,object_type,label,x_position x,y_position y,layout_width width,
        layout_height height,layout_shape shape,layout_rotation rotation,z_index,area_id FROM floor_plan_object ORDER BY z_index,id
        """));}

    @PutMapping @Transactional
    public Map<String,Boolean> save(@RequestBody LayoutRequest request,HttpSession session){requireManager(session);ensureSettings();int canvasWidth=clamp(request.canvasWidth(),600,3000),canvasHeight=clamp(request.canvasHeight(),400,2200);jdbc.update("UPDATE floor_plan_setting SET canvas_width=?,canvas_height=? WHERE name='main'",canvasWidth,canvasHeight);for(LayoutItem item:safe(request.tables())){jdbc.update("UPDATE pub_table SET x_position=?,y_position=?,layout_width=?,layout_height=?,layout_shape=?,layout_rotation=? WHERE id=?",clamp(item.x(),-500,canvasWidth+500),clamp(item.y(),-500,canvasHeight+500),clamp(item.width(),46,400),clamp(item.height(),46,300),shape(item.shape()),rotation(item.rotation()),item.id());}for(LayoutItem item:safe(request.objects())){jdbc.update("UPDATE floor_plan_object SET x_position=?,y_position=?,layout_width=?,layout_height=?,layout_shape=?,layout_rotation=?,z_index=? WHERE id=?",clamp(item.x(),-1000,canvasWidth+1000),clamp(item.y(),-1000,canvasHeight+1000),clamp(item.width(),16,1000),clamp(item.height(),10,800),shape(item.shape()),rotation(item.rotation()),clamp(item.zIndex(),-50,100),item.id());}return Map.of("saved",true);}

    @PostMapping("/objects") @Transactional
    public Map<String,Object> createObject(@RequestBody NewObject request,HttpSession session){requireManager(session);if(!TYPES.contains(request.objectType()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported floor-plan object.");Defaults d=defaults(request.objectType());String label=request.label()==null||request.label().isBlank()?d.label():request.label().trim();jdbc.update("""
      INSERT INTO floor_plan_object(object_type,label,x_position,y_position,layout_width,layout_height,layout_rotation,layout_shape,z_index,area_id)
      VALUES(?,?,80,80,?,?,0,?,?,?)
      """,request.objectType(),label,d.width(),d.height(),d.shape(),d.z(),request.areaId());Long id=jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);return Map.of("object",jdbc.queryForMap("""
      SELECT id,object_type,label,x_position x,y_position y,layout_width width,layout_height height,layout_shape shape,layout_rotation rotation,z_index,area_id FROM floor_plan_object WHERE id=?
      """,id));}

    @PutMapping("/objects/{id}")
    public Map<String,Boolean> updateObject(@PathVariable long id,@RequestBody ObjectDetails request,HttpSession session){requireManager(session);String label=request.label()==null?"":request.label().trim();if(jdbc.update("UPDATE floor_plan_object SET label=?,area_id=? WHERE id=?",label,request.areaId(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Floor object not found.");return Map.of("updated",true);}

    @PostMapping("/objects/{id}/duplicate")
    public Map<String,Object> duplicateObject(@PathVariable long id,HttpSession session){requireManager(session);int changed=jdbc.update("""
      INSERT INTO floor_plan_object(object_type,label,x_position,y_position,layout_width,layout_height,layout_rotation,layout_shape,z_index,area_id)
      SELECT object_type,label||' copy',x_position+24,y_position+24,layout_width,layout_height,layout_rotation,layout_shape,z_index,area_id
      FROM floor_plan_object WHERE id=?
      """,id);if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Floor object not found.");Long newId=jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);return Map.of("object",jdbc.queryForMap("""
      SELECT id,object_type,label,x_position x,y_position y,layout_width width,layout_height height,layout_shape shape,layout_rotation rotation,z_index,area_id FROM floor_plan_object WHERE id=?
      """,newId));}

    @DeleteMapping("/objects/{id}")
    public Map<String,Boolean> deleteObject(@PathVariable long id,HttpSession session){requireManager(session);if(jdbc.update("DELETE FROM floor_plan_object WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Floor object not found.");return Map.of("deleted",true);}

    private void ensureSettings(){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM floor_plan_setting WHERE name='main'",Integer.class);if(count==null||count==0)jdbc.update("INSERT INTO floor_plan_setting(name,canvas_width,canvas_height) VALUES('main',1200,760)");}
    private Defaults defaults(String type){return switch(type){case"wall"->new Defaults(220,16,"rectangle","Wall",1);case"door"->new Defaults(80,16,"rectangle","Door",2);case"bar"->new Defaults(240,70,"rectangle","Bar",2);case"pillar"->new Defaults(50,50,"square","Pillar",2);case"tv"->new Defaults(70,26,"rectangle","TV",3);case"fixed_table"->new Defaults(85,55,"rectangle","Non-bookable table",3);case"area"->new Defaults(300,220,"rectangle","Area",-5);default->new Defaults(150,40,"rectangle","Label",5);};}
    private String shape(String value){return SHAPES.contains(value)?value:"rectangle";}private double rotation(double value){return((value%360)+360)%360;}private int clamp(int v,int min,int max){return Math.max(min,Math.min(v,max));}private double clamp(double v,double min,double max){return Math.max(min,Math.min(v,max));}private List<LayoutItem> safe(List<LayoutItem> rows){return rows==null?List.of():rows;}private void requireManager(HttpSession session){AuthUser u=auth.currentUser(session);if(u.role()!=StaffRole.MANAGER&&u.role()!=StaffRole.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager access required.");}
    public record LayoutRequest(int canvasWidth,int canvasHeight,List<LayoutItem> tables,List<LayoutItem> objects){}public record LayoutItem(long id,double x,double y,double width,double height,String shape,double rotation,int zIndex){}public record NewObject(String objectType,String label,Long areaId){}public record ObjectDetails(String label,Long areaId){}private record Defaults(double width,double height,String shape,String label,int z){}
}
