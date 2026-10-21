package uk.co.rocketpub.staffportal.email;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.security.RequestOriginValidator;

@RestController
@RequestMapping("/api/email")
@CrossOrigin(origins = {"https://rocketpubserver.co.uk", "http://localhost:8000"}, allowCredentials = "true")
public class RocketEmailController {
    private final RocketEmailService emailService;
    private final AuthService authService;
    private final RequestOriginValidator originValidator;
    private final StaffNotificationService notifications;

    public RocketEmailController(
            RocketEmailService emailService,
            AuthService authService,
            RequestOriginValidator originValidator,
            StaffNotificationService notifications) {
        this.emailService = emailService;
        this.authService = authService;
        this.originValidator = originValidator;
        this.notifications = notifications;
    }

    @GetMapping("/status")
    public RocketEmailStatus status(HttpSession session) {
        requireAdmin(session);
        return emailService.getStatus();
    }

    @PostMapping("/microsoft/connect")
    public Map<String, String> connect(
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {
        requireAdmin(session);
        originValidator.requireAllowed(origin);
        return Map.of("authorizationUrl", emailService.startMicrosoftConnection(session));
    }

    @GetMapping("/microsoft/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpSession session) {
        requireAdmin(session);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(emailService.completeMicrosoftConnection(code, state, session)))
                .build();
    }

    @PostMapping("/microsoft/disconnect")
    public Map<String, Boolean> disconnect(
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {
        requireAdmin(session);
        originValidator.requireAllowed(origin);
        emailService.disconnect();
        return Map.of("disconnected", true);
    }

    @PostMapping("/test")
    public Map<String, Boolean> testNotification(
            @RequestBody TestNotificationRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {
        requireAdmin(session);
        originValidator.requireAllowed(origin);
        notifications.sendTest(
                request.staffMemberId(),
                request.notificationType(),
                LocalDate.parse(request.weekStart()));
        return Map.of("sent", true);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> connectionError() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "Rocket Email could not complete this action. Check its configuration and try again."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidTestRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    private void requireAdmin(HttpSession session) {
        if (authService.currentUser(session).role() != StaffRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
        }
    }

}
