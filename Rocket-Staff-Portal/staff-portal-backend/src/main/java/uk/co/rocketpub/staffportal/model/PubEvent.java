package uk.co.rocketpub.staffportal.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pub_calendar_event")
public class PubEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_date", nullable = false)
    @JsonIgnore
    private String eventDateValue;

    @Column(name = "event_time")
    @JsonIgnore
    private String eventTimeValue;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "event_type", nullable = false)
    @JsonIgnore
    private String eventTypeValue;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    @JsonIgnore
    private StaffMember createdBy;

    @Column(name = "created_at")
    @JsonIgnore
    private String createdAt;

    public PubEvent() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEventDate() {
        return eventDateValue == null ? null : LocalDate.parse(eventDateValue);
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDateValue = eventDate == null ? null : eventDate.toString();
    }

    public LocalTime getEventTime() {
        return parseTime(eventTimeValue);
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTimeValue = formatTime(eventTime);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PubEventType getEventType() {
        return PubEventType.fromDatabaseValue(eventTypeValue);
    }

    public void setEventType(PubEventType eventType) {
        this.eventTypeValue = eventType == null ? null : eventType.getDatabaseValue();
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @JsonIgnore
    public String getEventDateValue() {
        return eventDateValue;
    }

    @JsonIgnore
    public String getEventTypeValue() {
        return eventTypeValue;
    }

    @JsonIgnore
    public StaffMember getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(StaffMember createdBy) {
        this.createdBy = createdBy;
    }

    @JsonIgnore
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;

        String clean = value.trim();
        if (clean.length() >= 8) clean = clean.substring(0, 8);

        return LocalTime.parse(clean);
    }

    private String formatTime(LocalTime value) {
        if (value == null) return null;

        return value.withNano(0).toString();
    }
}
