package uk.co.rocketpub.staffportal.model;

import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "large_party_inquiry")
public class LargeParty {

    @Id
    private Long id;

    @Column(name = "event_date")
    @JsonIgnore
    private String eventDateValue;

    @Column(name = "event_time")
    @JsonIgnore
    private String startTimeValue;

    @Column(name = "expected_end_time")
    @JsonIgnore
    private String endTimeValue;

    @Column(name = "customer_name", nullable = false)
    private String name;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "occasion")
    private String occasion;

    @Column(name = "notes")
    private String note;

    @Column(name = "status", nullable = false)
    @JsonIgnore
    private String status;

    public LargeParty() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEventDate() {
        return eventDateValue == null ? null : LocalDate.parse(eventDateValue);
    }

    public LocalTime getStartTime() {
        return parseTime(startTimeValue);
    }

    public LocalTime getEndTime() {
        return parseTime(endTimeValue);
    }

    public String getName() {
        return name;
    }

    public int getPartySize() {
        return partySize;
    }

    public String getOccasion() {
        return occasion;
    }

    public String getNote() {
        return note;
    }

    @JsonIgnore
    public String getEventDateValue() {
        return eventDateValue;
    }

    @JsonIgnore
    public String getStatus() {
        return status;
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;

        String clean = value.trim();
        if (clean.length() >= 8) clean = clean.substring(0, 8);

        return LocalTime.parse(clean);
    }
}
