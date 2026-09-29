package uk.co.rocketpub.staffportal.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import uk.co.rocketpub.staffportal.account.ForgotPasswordRequest;
import uk.co.rocketpub.staffportal.account.PasswordResetService;
import uk.co.rocketpub.staffportal.account.ResetPasswordRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(
        origins = {"https://rocketpubserver.co.uk", "http://localhost:8000"},
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
            HttpServletRequest request,
            HttpServletResponse response) {

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

        int lifetime = loginRequest.isRememberMe()
                ? 60 * 60 * 24 * 30
                : 60 * 60 * 12;
        session.setMaxInactiveInterval(lifetime);

        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie
                .from("JSESSIONID", session.getId())
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .secure(isSecure(request));

        if (loginRequest.isRememberMe()) {
            cookie.maxAge(lifetime);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());

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
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        session.invalidate();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from("JSESSIONID", "")
                        .httpOnly(true)
                        .sameSite("Lax")
                        .secure(isSecure(request))
                        .path("/")
                        .maxAge(0)
                        .build()
                        .toString()
        );

        return Map.of("loggedOut", true);
    }

    private boolean isSecure(HttpServletRequest request) {
        String forwardedProtocol = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProtocol);
    }
}
