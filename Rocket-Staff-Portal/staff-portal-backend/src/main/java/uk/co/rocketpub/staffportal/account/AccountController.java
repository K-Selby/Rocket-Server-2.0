package uk.co.rocketpub.staffportal.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthUser;

@RestController
@RequestMapping("/api/account")
@CrossOrigin(origins = "http://localhost:8000", allowCredentials = "true")
public class AccountController {
    private final AccountService accountService;

    @Value("${rocket.email.frontend-url:http://localhost:8000/staff}")
    private String frontendUrl;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/password")
    public AuthUser changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        return accountService.changePassword(request, session);
    }

    @PostMapping("/email")
    public AuthUser startEmailVerification(
            @RequestBody EmailRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        return accountService.startEmailVerification(request, session);
    }

    @PostMapping("/email/resend")
    public AuthUser resendEmailVerification(
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        return accountService.resendEmailVerification(session);
    }

    @PostMapping("/email/verify")
    public AuthUser verifyEmail(
            @RequestBody VerifyEmailRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        return accountService.verifyEmail(request, session);
    }

    @DeleteMapping("/email/pending")
    public AuthUser cancelPendingEmail(
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        requireOrigin(origin);
        return accountService.cancelPendingEmail(session);
    }

    private void requireOrigin(String origin) {
        if (!frontendUrl.equals(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin is not allowed");
        }
    }
}
