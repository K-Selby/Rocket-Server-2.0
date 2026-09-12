package uk.co.rocketpub.staffportal.account;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.email.RocketEmailService;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.service.PasswordService;

@Service
public class PasswordResetService {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final StaffMemberRepository staffRepository;
    private final PasswordService passwordService;
    private final RocketEmailService emailService;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    private record Challenge(String codeHash, Instant expiresAt) {}

    public PasswordResetService(
            StaffMemberRepository staffRepository,
            PasswordService passwordService,
            RocketEmailService emailService) {
        this.staffRepository = staffRepository;
        this.passwordService = passwordService;
        this.emailService = emailService;
    }

    public void request(String requestedEmail) {
        String email = normalise(requestedEmail);

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("Enter a valid email address");
        }

        StaffMember user = staffRepository.findActiveVerifiedEmail(email)
                .orElseThrow(() -> badRequest(
                        "That email is not linked to a verified account. Contact a manager."));

        String code = String.format("%06d", random.nextInt(1_000_000));
        challenges.put(email, new Challenge(
                passwordService.generate(code),
                Instant.now().plusSeconds(900)));

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), code);
        } catch (RuntimeException exception) {
            challenges.remove(email);
            throw badRequest("The reset email could not be sent. Contact a manager.");
        }
    }

    public void reset(ResetPasswordRequest request) {
        String email = normalise(request.email());
        String code = request.code() == null ? "" : request.code().trim();
        String newPassword = request.newPassword() == null ? "" : request.newPassword();
        String confirmPassword = request.confirmPassword() == null ? "" : request.confirmPassword();

        Challenge challenge = challenges.get(email);

        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())
                || !passwordService.matches(code, challenge.codeHash())) {
            throw badRequest("That reset code is invalid or has expired.");
        }

        if (newPassword.length() < 8) {
            throw badRequest("New passwords must be at least 8 characters.");
        }

        if (PasswordService.TEMPORARY_PASSWORD.equals(newPassword)) {
            throw badRequest("Your new password cannot be the same as your temporary password.");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw badRequest("The new passwords do not match.");
        }

        StaffMember user = staffRepository.findActiveVerifiedEmail(email)
                .orElseThrow(() -> badRequest("That email is not linked to a verified account."));
        user.setPasswordHash(passwordService.generate(newPassword));
        user.setMustChangePassword(false);
        staffRepository.save(user);
        challenges.remove(email);
    }

    private String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
