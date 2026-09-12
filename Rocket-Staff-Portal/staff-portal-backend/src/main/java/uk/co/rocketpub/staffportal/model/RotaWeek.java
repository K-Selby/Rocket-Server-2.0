package uk.co.rocketpub.staffportal.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rota_week")
public class RotaWeek {

    private static final DateTimeFormatter SQLITE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Id
    @Column(name = "week_start")
    private String weekStart;

    private boolean published;
    private boolean editing;
    private String notes;

    // SQLite stores timestamps as text rather than Hibernate temporal values.
    @Column(name = "published_at")
    private String publishedAtValue;

    @ManyToOne
    @JoinColumn(name = "published_by_user_id")
    private StaffMember publishedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "rota_week_hidden_staff",
            joinColumns = @JoinColumn(name = "rota_week_start")
    )
    @Column(name = "user_id")
    private Set<Long> hiddenStaffIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "rota_week_published_hidden_staff",
            joinColumns = @JoinColumn(name = "rota_week_start")
    )
    @Column(name = "user_id")
    private Set<Long> publishedHiddenStaffIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "rota_week_dismissed_availability",
            joinColumns = @JoinColumn(name = "rota_week_start")
    )
    @Column(name = "diary_entry_id")
    private Set<Long> dismissedAvailabilityIds = new HashSet<>();

    public RotaWeek() {
    }

    public String getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(String weekStart) {
        this.weekStart = weekStart;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getPublishedAt() {
        return parseDateTime(publishedAtValue);
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAtValue = formatDateTime(publishedAt);
    }

    public StaffMember getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(StaffMember publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Set<Long> getHiddenStaffIds() {
        return hiddenStaffIds;
    }

    public void setHiddenStaffIds(Set<Long> hiddenStaffIds) {
        this.hiddenStaffIds = hiddenStaffIds;
    }

    public Set<Long> getPublishedHiddenStaffIds() {
        return publishedHiddenStaffIds;
    }

    public void setPublishedHiddenStaffIds(Set<Long> publishedHiddenStaffIds) {
        this.publishedHiddenStaffIds = publishedHiddenStaffIds;
    }

    public Set<Long> getDismissedAvailabilityIds() {
        return dismissedAvailabilityIds;
    }

    public void setDismissedAvailabilityIds(Set<Long> dismissedAvailabilityIds) {
        this.dismissedAvailabilityIds = dismissedAvailabilityIds;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;

        return LocalDateTime.parse(value.trim().replace(' ', 'T'));
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(SQLITE_DATE_TIME);
    }
}
