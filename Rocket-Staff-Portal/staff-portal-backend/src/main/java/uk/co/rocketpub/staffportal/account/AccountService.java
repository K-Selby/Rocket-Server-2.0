package uk.co.rocketpub.staffportal.account;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import uk.co.rocketpub.staffportal.auth.AuthService;
import uk.co.rocketpub.staffportal.auth.AuthUser;
import uk.co.rocketpub.staffportal.email.RocketEmailService;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.service.PasswordService;

@Service
public class AccountService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AuthService authService;
    private final StaffMemberRepository staffRepository;
    private final PasswordService passwordService;
    private final RocketEmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public AccountService(
            AuthService authService,
            StaffMemberRepository staffRepository,
            PasswordService passwordService,
            RocketEmailService emailService) {

        this.authService = authService;
        this.staffRepository = staffRepository;
        this.passwordService = passwordService;
        this.emailService = emailService;
    }

    @Transactional
    public AuthUser changePassword(ChangePasswordRequest request, HttpSession session) {
        StaffMember user = authService.currentStaffMember(session);
        String currentPassword = request.currentPassword() == null ? "" : request.currentPassword();
        String newPassword = request.newPassword() == null ? "" : request.newPassword();
        String confirmation = request.confirmPassword() == null ? "" : request.confirmPassword();

        if (!passwordService.matches(currentPassword, user.getPasswordHash())) {
            throw badRequest("Your current password is incorrect");
        }

        if (newPassword.length() < 8) {
            throw badRequest("New passwords must be at least 8 characters");
        }

        if (PasswordService.TEMPORARY_PASSWORD.equals(newPassword)) {
            throw badRequest("Your new password cannot be the same as your temporary password");
        }

        if (!newPassword.equals(confirmation)) {
            throw badRequest("The new passwords do not match");
        }

        if (passwordService.matches(newPassword, user.getPasswordHash())) {
            throw badRequest("Choose a different password from your current password");
        }

        user.setPasswordHash(passwordService.generate(newPassword));
        user.setMustChangePassword(false);
        return AuthUser.from(staffRepository.save(user));
    }

    public AuthUser startEmailVerification(EmailRequest request, HttpSession session) {
        StaffMember user = authService.currentStaffMember(session);
        String email = normaliseEmail(request.email());

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("Enter a valid email address");
        }

        if (user.isEmailVerified() && email.equalsIgnoreCase(user.getEmail())) {
            throw badRequest("That email address is already linked to your account");
        }

        if (staffRepository.emailUsedByAnotherUser(email, user.getId())) {
            throw badRequest("That email address is already attached to another user");
        }

        issueVerificationCode(user, email);
        return AuthUser.from(user);
    }

    public AuthUser resendEmailVerification(HttpSession session) {
        StaffMember user = authService.currentStaffMember(session);

        if (user.getPendingEmail() == null || user.getPendingEmail().isBlank()) {
            throw badRequest("There is no email waiting for verification");
        }

        issueVerificationCode(user, user.getPendingEmail());
        return AuthUser.from(user);
    }

    @Transactional
    public AuthUser verifyEmail(VerifyEmailRequest request, HttpSession session) {
        StaffMember user = authService.currentStaffMember(session);
        String code = request.code() == null ? "" : request.code().trim();

        if (user.getPendingEmail() == null
                || user.getEmailVerificationCodeHash() == null
                || user.getEmailVerificationExpiresAt() == null) {
            throw badRequest("Request a new email verification code");
        }

        if (LocalDateTime.now().isAfter(user.getEmailVerificationExpiresAt())) {
            throw badRequest("That verification code has expired. Send a new code");
        }

        if (!passwordService.matches(code, user.getEmailVerificationCodeHash())) {
            throw badRequest("Incorrect verification code");
        }

        user.setEmail(user.getPendingEmail());
        user.setEmailVerified(true);
        clearVerification(user);
        return AuthUser.from(staffRepository.save(user));
    }

    @Transactional
    public AuthUser cancelPendingEmail(HttpSession session) {
        StaffMember user = authService.currentStaffMember(session);
        clearVerification(user);
        return AuthUser.from(staffRepository.save(user));
    }

    private void issueVerificationCode(StaffMember user, String email) {
        String code = String.format("%06d", random.nextInt(1_000_000));

        user.setPendingEmail(email);
        user.setEmailVerificationCodeHash(passwordService.generate(code));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
        staffRepository.save(user);

        try {
            emailService.sendVerificationEmail(email, code);
        } catch (RuntimeException exception) {
            clearVerification(user);
            staffRepository.save(user);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The verification email could not be sent. Check the Rocket Email connection and try again"
            );
        }
    }

    private void clearVerification(StaffMember user) {
        user.setPendingEmail(null);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
    }

    private String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
