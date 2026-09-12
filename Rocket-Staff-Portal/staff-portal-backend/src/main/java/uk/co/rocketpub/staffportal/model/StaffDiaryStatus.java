package uk.co.rocketpub.staffportal.model;

// Defines the possible states of a Staff Diary entry.
public enum StaffDiaryStatus {

    REQUESTED("requested"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    INFO("info");

    private final String databaseValue;

    StaffDiaryStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static StaffDiaryStatus fromDatabaseValue(String value) {
        if (value == null) return null;

        for (StaffDiaryStatus status : values()) {
            if (status.databaseValue.equalsIgnoreCase(value)) return status;
        }

        throw new IllegalArgumentException("Unknown Staff Diary status: " + value);
    }
}
