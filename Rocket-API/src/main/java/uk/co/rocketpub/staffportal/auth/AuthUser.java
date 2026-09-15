package uk.co.rocketpub.staffportal.auth;

import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;

public record AuthUser(
        Long id,
        String name,
        StaffRole role,
        boolean mustChangePassword,
        String email,
        boolean emailVerified,
        String pendingEmail) {

    public static AuthUser from(
            StaffMember staffMember) {

        return new AuthUser(
                staffMember.getId(),
                staffMember.getName(),
                staffMember.getRole(),
                staffMember.isMustChangePassword(),
                staffMember.getEmail(),
                staffMember.isEmailVerified(),
                staffMember.getPendingEmail()
        );
    }
}
