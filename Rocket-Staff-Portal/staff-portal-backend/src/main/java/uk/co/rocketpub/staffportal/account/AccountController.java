package uk.co.rocketpub.staffportal.account;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthUser;
import uk.co.rocketpub.staffportal.security.RequestOriginValidator;

@RestController
@RequestMapping("/api/account")
@CrossOrigin(origins = "https://rocketpubserver.co.uk", allowCredentials = "true")
public class AccountController {
    private final AccountService accountService;
    private final RequestOriginValidator originValidator;

    public AccountController(
            AccountService accountService,
            RequestOriginValidator originValidator) {
        this.accountService = accountService;
        this.originValidator = originValidator;
    }

    @PostMapping("/password")
    public AuthUser changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        originValidator.requireAllowed(origin);
        return accountService.changePassword(request, session);
    }

    @PostMapping("/email")
    public AuthUser startEmailVerification(
            @RequestBody EmailRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        originValidator.requireAllowed(origin);
        return accountService.startEmailVerification(request, session);
    }

    @PostMapping("/email/resend")
    public AuthUser resendEmailVerification(
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        originValidator.requireAllowed(origin);
        return accountService.resendEmailVerification(session);
    }

    @PostMapping("/email/verify")
    public AuthUser verifyEmail(
            @RequestBody VerifyEmailRequest request,
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        originValidator.requireAllowed(origin);
        return accountService.verifyEmail(request, session);
    }

    @DeleteMapping("/email/pending")
    public AuthUser cancelPendingEmail(
            HttpSession session,
            @RequestHeader(value = "Origin", required = false) String origin) {

        originValidator.requireAllowed(origin);
        return accountService.cancelPendingEmail(session);
    }
}
