package uk.co.rocketpub.staffportal.auth;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.service.PasswordService;

@Service
public class AuthService {

    public static final String SESSION_USER_ID =
            "rocketStaffUserId";

    private final StaffMemberRepository staffMemberRepository;
    private final PasswordService passwordService;

    public AuthService(
            StaffMemberRepository staffMemberRepository,
            PasswordService passwordService) {

        this.staffMemberRepository =
                staffMemberRepository;

        this.passwordService =
                passwordService;
    }

    public AuthUser login(
            LoginRequest request,
            HttpSession session) {

        String login = request.getLogin() == null
                ? ""
                : request.getLogin().trim();

        String password = request.getPassword() == null
                ? ""
                : request.getPassword();

        if (login.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter your username/email and password"
            );
        }

        StaffMember user = staffMemberRepository
                .findActiveLogin(login)
                .orElseThrow(() ->
                        invalidLogin()
                );

        if (!passwordService.matches(
                password,
                user.getPasswordHash())) {

            throw invalidLogin();
        }

        // Prevent session fixation after successful login.
        session.invalidate();

        /*
         * A new session is created by the controller immediately
         * after authentication.
         */
        user.setLastLoginAt(LocalDateTime.now());
        staffMemberRepository.save(user);

        return AuthUser.from(user);
    }

    public AuthUser currentUser(
            HttpSession session) {

        return AuthUser.from(
                currentStaffMember(session)
        );
    }

    public StaffMember currentStaffMember(
            HttpSession session) {

        Object storedId = session.getAttribute(
                SESSION_USER_ID
        );

        if (!(storedId instanceof Long userId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Not signed in"
            );
        }

        return staffMemberRepository
                .findById(userId)
                .filter(StaffMember::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Not signed in"
                        )
                );
    }

    private ResponseStatusException invalidLogin() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Incorrect username/email or password"
        );
    }
}
