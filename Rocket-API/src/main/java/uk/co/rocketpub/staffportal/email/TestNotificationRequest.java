package uk.co.rocketpub.staffportal.email;

public record TestNotificationRequest(
        Long staffMemberId,
        String notificationType,
        String weekStart) {
}
