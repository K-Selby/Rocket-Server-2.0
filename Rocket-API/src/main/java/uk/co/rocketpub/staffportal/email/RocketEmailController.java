package uk.co.rocketpub.staffportal.email;

import java.net.URI;
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
@CrossOrigin(origins = "https://rocketpubserver.co.uk", allowCredentials = "true")
public class RocketEmailController {
    private final RocketEmailService emailService;
    private final AuthService authService;
    private final RequestOriginValidator originValidator;

    public RocketEmailController(
            RocketEmailService emailService,
            AuthService authService,
            RequestOriginValidator originValidator) {
        this.emailService = emailService;
        this.authService = authService;
        this.originValidator = originValidator;
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> connectionError() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "Rocket Email could not complete this action. Check its configuration and try again."));
    }

    private void requireAdmin(HttpSession session) {
        if (authService.currentUser(session).role() != StaffRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
        }
    }
}
