package uk.co.rocketpub.staffportal.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import uk.co.rocketpub.staffportal.account.ForgotPasswordRequest;
import uk.co.rocketpub.staffportal.account.PasswordResetService;
import uk.co.rocketpub.staffportal.account.ResetPasswordRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(
        origins = "http://localhost:8000",
        allowCredentials = "true"
)
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public AuthUser login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {

        /*
         * Validate the login first using the existing session.
         * AuthService invalidates it after success.
         */
        AuthUser user = authService.login(
                loginRequest,
                request.getSession()
        );

        // Create the new authenticated session.
        HttpSession session =
                request.getSession(true);

        session.setAttribute(
                AuthService.SESSION_USER_ID,
                user.id()
        );

        return user;
    }

    @PostMapping("/forgot-password")
    public Map<String, Boolean> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResetService.request(request.email());
        return Map.of("accepted", true);
    }

    @PostMapping("/reset-password")
    public Map<String, Boolean> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request);
        return Map.of("reset", true);
    }

    @GetMapping("/me")
    public AuthUser me(HttpSession session) {
        return authService.currentUser(session);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(
            HttpSession session) {

        session.invalidate();

        return Map.of("loggedOut", true);
    }
}
