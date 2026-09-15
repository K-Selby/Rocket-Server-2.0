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
@Table(name = "staff_diary_entry")
public class StaffDiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    @JsonIgnore
    private String entryDateValue;

    @Column(name = "entry_type", nullable = false, length = 30)
    @JsonIgnore
    private String typeValue;

    @Column(name = "available_from")
    @JsonIgnore
    private String availableFromValue;

    @Column(name = "available_to")
    @JsonIgnore
    private String availableToValue;

    @Column(name = "available_until_finish", nullable = false)
    private boolean availableUntilFinish;

    @Column(name = "note", length = 400)
    private String note;

    @Column(name = "status", nullable = false, length = 20)
    @JsonIgnore
    private String statusValue;

    @Column(name = "request_group_id", length = 36)
    private String requestGroupId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private StaffMember staffMember;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_user_id")
    private StaffMember createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewed_by_user_id")
    private StaffMember reviewedBy;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "reviewed_at")
    private String reviewedAt;

    public StaffDiaryEntry() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEntryDate() {
        return entryDateValue == null ? null : LocalDate.parse(entryDateValue);
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDateValue = entryDate == null ? null : entryDate.toString();
    }

    public StaffDiaryType getType() {
        return StaffDiaryType.fromDatabaseValue(typeValue);
    }

    public void setType(StaffDiaryType type) {
        this.typeValue = type == null ? null : type.getDatabaseValue();
    }

    public StaffDiaryStatus getStatus() {
        return StaffDiaryStatus.fromDatabaseValue(statusValue);
    }

    public void setStatus(StaffDiaryStatus status) {
        this.statusValue = status == null ? null : status.getDatabaseValue();
    }

    public LocalTime getAvailableFrom() {
        return parseTime(availableFromValue);
    }

    public void setAvailableFrom(LocalTime availableFrom) {
        this.availableFromValue = formatTime(availableFrom);
    }

    public LocalTime getAvailableTo() {
        return parseTime(availableToValue);
    }

    public void setAvailableTo(LocalTime availableTo) {
        this.availableToValue = formatTime(availableTo);
    }

    public boolean isAvailableUntilFinish() {
        return availableUntilFinish;
    }

    public void setAvailableUntilFinish(boolean availableUntilFinish) {
        this.availableUntilFinish = availableUntilFinish;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRequestGroupId() {
        return requestGroupId;
    }

    public void setRequestGroupId(String requestGroupId) {
        this.requestGroupId = requestGroupId;
    }

    public StaffMember getStaffMember() {
        return staffMember;
    }

    public void setStaffMember(StaffMember staffMember) {
        this.staffMember = staffMember;
    }

    public StaffMember getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(StaffMember createdBy) {
        this.createdBy = createdBy;
    }

    public StaffMember getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(StaffMember reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(String reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    @JsonIgnore
    public String getEntryDateValue() {
        return entryDateValue;
    }

    @JsonIgnore
    public String getTypeValue() {
        return typeValue;
    }

    @JsonIgnore
    public String getStatusValue() {
        return statusValue;
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;

        String clean = value.trim();

        // SQLite may contain values such as 16:00:00.000000.
        if (clean.length() >= 8) clean = clean.substring(0, 8);

        return LocalTime.parse(clean);
    }

    private String formatTime(LocalTime value) {
        if (value == null) return null;

        return value.withNano(0).toString();
    }
}
