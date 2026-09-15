package uk.co.rocketpub.staffportal.account;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword,
        String confirmPassword) {
}
