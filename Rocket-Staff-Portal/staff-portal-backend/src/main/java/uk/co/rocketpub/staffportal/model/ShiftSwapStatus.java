package uk.co.rocketpub.staffportal.model;

// Defines the stages a shift-cover request can move through.
public enum ShiftSwapStatus {

    PENDING("pending"),
    AWAITING_MANAGER_APPROVAL("awaiting_manager_approval"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    private final String databaseValue;

    ShiftSwapStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static ShiftSwapStatus fromDatabaseValue(String value) {
        if (value == null) return null;

        for (ShiftSwapStatus status : values()) {
            if (status.databaseValue.equalsIgnoreCase(value)) return status;
        }

        throw new IllegalArgumentException("Unknown shift-cover status: " + value);
    }
}
