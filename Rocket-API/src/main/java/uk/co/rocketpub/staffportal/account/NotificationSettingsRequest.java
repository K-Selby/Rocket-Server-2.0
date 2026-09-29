package uk.co.rocketpub.staffportal.account;
public record NotificationSettingsRequest(boolean notificationsEnabled, boolean notifyRequestDecisions, boolean notifyShiftSwaps, boolean notifyPublishedRotas) {}
