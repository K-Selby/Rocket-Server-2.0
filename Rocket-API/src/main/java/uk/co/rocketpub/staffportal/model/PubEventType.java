package uk.co.rocketpub.staffportal.model;

// Defines the types of events that can appear in the pub calendar.
public enum PubEventType {

    FOOTBALL("football"),
    EVENT("event");

    private final String databaseValue;

    PubEventType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static PubEventType fromDatabaseValue(String value) {
        if (value == null) return null;

        for (PubEventType type : values()) {
            if (type.databaseValue.equalsIgnoreCase(value)) return type;
        }

        throw new IllegalArgumentException("Unknown pub event type: " + value);
    }
}
