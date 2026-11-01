package uk.co.rocketpub.staffportal.customer;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.model.StaffRole;

@RestController
@RequestMapping("/api")
public class CustomerMenuController {
    private final CustomerMenuService menus;
    private final AuthService auth;

    public CustomerMenuController(CustomerMenuService menus, AuthService auth) {
        this.menus = menus;
        this.auth = auth;
    }

    @GetMapping("/customer/menus")
    public List<CustomerMenuView> publicMenus() {
        return menus.list();
    }

    @GetMapping("/customer/portal-buttons")
    public List<CustomerPortalButtonView> publicPortalButtons() {
        return menus.portalButtons();
    }

    @GetMapping("/booking/menus/portal-buttons")
    public List<CustomerPortalButtonView> managedPortalButtons(HttpSession session) {
        requireManager(session);
        return menus.portalButtons();
    }

    @PutMapping("/booking/menus/portal-buttons/{key}")
    public CustomerPortalButtonView updatePortalButton(
            @PathVariable String key,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String sidebarName,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) String cardColor,
            @RequestParam(required = false) String textColor,
            HttpSession session) {
        requireManager(session);
        return menus.updatePortalButton(key, title, description, sidebarName, icon, cardColor, textColor);
    }

    @GetMapping("/customer/menus/{slug}")
    public CustomerMenuView publicMenu(@PathVariable String slug) {
        return menus.find(slug);
    }

    @GetMapping("/customer/menus/{slug}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable String slug) {
        Resource pdf = menus.pdf(slug);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(slug + ".pdf").build().toString())
                .body(pdf);
    }

    @GetMapping("/booking/menus")
    public List<CustomerMenuView> managedMenus(HttpSession session) {
        requireManager(session);
        return menus.list();
    }

    @GetMapping("/booking/menus/templates")
    public List<PortalTemplateView> templates(HttpSession session) {
        requireManager(session);
        return menus.templates();
    }

    @GetMapping("/booking/menus/templates/{id}")
    public List<CustomerMenuView> previewTemplate(@PathVariable long id, HttpSession session) {
        requireManager(session);
        return menus.templateMenus(id);
    }

    @PostMapping("/booking/menus/templates")
    public PortalTemplateView createTemplate(HttpSession session) {
        requireManager(session);
        return menus.createTemplate();
    }

    @PostMapping("/booking/menus/templates/{id}/restore")
    public void restoreTemplate(@PathVariable long id, HttpSession session) {
        requireManager(session);
        menus.createTemplate();
        menus.restoreTemplate(id);
    }

    @PostMapping(value = "/booking/menus", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CustomerMenuView create(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String sidebarName,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) String cardColor,
            @RequestParam(required = false) String textColor,
            @RequestParam("pdf") MultipartFile pdf,
            HttpSession session) {
        requireManager(session);
        return menus.create(title, description, sidebarName, icon, cardColor, textColor, pdf);
    }

    @PutMapping(value = "/booking/menus/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CustomerMenuView update(
            @PathVariable long id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String sidebarName,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) String cardColor,
            @RequestParam(required = false) String textColor,
            @RequestParam(value = "pdf", required = false) MultipartFile pdf,
            HttpSession session) {
        requireManager(session);
        return menus.update(id, title, description, sidebarName, icon, cardColor, textColor, pdf);
    }

    @DeleteMapping("/booking/menus/{id}")
    public void delete(@PathVariable long id, HttpSession session) {
        requireManager(session);
        menus.delete(id);
    }

    private void requireManager(HttpSession session) {
        StaffRole role = auth.currentUser(session).role();
        if (role != StaffRole.MANAGER && role != StaffRole.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Manager access required.");
        }
    }
}
