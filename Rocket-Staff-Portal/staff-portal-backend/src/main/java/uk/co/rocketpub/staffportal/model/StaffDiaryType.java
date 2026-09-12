package uk.co.rocketpub.staffportal.model;

// Defines the valid Staff Diary entry types.
public enum StaffDiaryType {

    DAY_OFF("day_off"),
    UNAVAILABLE("unavailable"),
    AVAILABLE("available"),
    NO_ONE_OFF("no_one_off"),
    NOTE("note");

    private final String databaseValue;

    StaffDiaryType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static StaffDiaryType fromDatabaseValue(String value) {
        if (value == null) return null;

        for (StaffDiaryType type : values()) {
            if (type.databaseValue.equalsIgnoreCase(value)) return type;
        }

        throw new IllegalArgumentException("Unknown Staff Diary type: " + value);
    }
}
