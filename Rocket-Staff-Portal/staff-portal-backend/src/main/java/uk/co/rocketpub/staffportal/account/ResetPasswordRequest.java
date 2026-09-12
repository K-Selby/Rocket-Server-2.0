package uk.co.rocketpub.staffportal.account;

public record ResetPasswordRequest(
        String email,
        String code,
        String newPassword,
        String confirmPassword) {
}
