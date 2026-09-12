package uk.co.rocketpub.staffportal.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.auth.AuthUser;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.service.StaffMemberService;

@RestController
@RequestMapping("/api/staff")
@CrossOrigin(origins = "http://localhost:8000", allowCredentials = "true")
public class StaffMemberController {
    private final StaffMemberService service;
    private final AuthService authService;

    @Value("${rocket.email.frontend-url:http://localhost:8000/staff}")
    private String frontendUrl;

    public StaffMemberController(StaffMemberService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public List<StaffMember> getAllStaff(HttpSession session) {
        requireManager(session);
        return service.getAllStaff();
    }

    @GetMapping("/rota")
    public List<StaffMember> getRotaStaff(HttpSession session) {
        authService.currentUser(session);
        return service.getRotaStaff();
    }

    @PostMapping
    public StaffMember createStaffMember(
            @RequestBody StaffMember staffMember,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        requireManager(session);
        return service.createStaffMember(staffMember);
    }

    @PutMapping("/{id}")
    public StaffMember updateStaffMember(
            @PathVariable Long id,
            @RequestBody StaffMember staffMember,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        requireManager(session);
        return service.updateStaffMember(id, staffMember);
    }

    @DeleteMapping("/{id}")
    public void deleteStaffMember(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        requireManager(session);
        service.deactivateStaffMember(id);
    }

    @PostMapping("/{id}/reset-password")
    public StaffMember resetPassword(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        return service.resetPassword(id, requireManager(session));
    }

    private AuthUser requireManager(HttpSession session) {
        AuthUser user = authService.currentUser(session);

        if (user.role() != StaffRole.MANAGER && user.role() != StaffRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }

        return user;
    }

    private void requireOrigin(String origin) {
        if (!frontendUrl.equals(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin is not allowed");
        }
    }
}
