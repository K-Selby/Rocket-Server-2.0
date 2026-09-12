package uk.co.rocketpub.staffportal.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "shift_cover_request")
public class ShiftSwapRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The shift the requester wants somebody else to cover.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requester_shift_id", nullable = false)
    private RotaShift requesterShift;

    // The staff member giving away the shift.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private StaffMember requester;

    // The staff member being asked to cover it.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_user_id", nullable = false)
    private StaffMember targetStaffMember;

    /*
     * Kept in the API for frontend compatibility.
     * Shift cover is one-way, so it is never stored.
     */
    @Transient
    private RotaShift targetShift;

    @Column(name = "status", nullable = false)
    @JsonIgnore
    private String statusValue;

    @Column(name = "note")
    private String note;

    @Column(name = "target_response_note")
    @JsonIgnore
    private String targetResponseNote;

    @Column(name = "manager_note")
    @JsonIgnore
    private String managerNote;

    @Column(name = "created_at")
    @JsonIgnore
    private String createdAtValue;

    @Column(name = "target_responded_at")
    @JsonIgnore
    private String targetRespondedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_acted_by_user_id")
    private StaffMember managerActedBy;

    @Column(name = "manager_acted_at")
    @JsonIgnore
    private String managerActedAtValue;

    public ShiftSwapRequest() {
    }

    @PrePersist
    public void beforeSave() {
        if (createdAtValue == null) createdAtValue = now();
        if (statusValue == null) setStatus(ShiftSwapStatus.PENDING);
    }

    public Long getId() {
        return id;
    }

    public RotaShift getRequesterShift() {
        return requesterShift;
    }

    public void setRequesterShift(RotaShift requesterShift) {
        this.requesterShift = requesterShift;
    }

    public StaffMember getRequester() {
        return requester;
    }

    public void setRequester(StaffMember requester) {
        this.requester = requester;
    }

    public StaffMember getTargetStaffMember() {
        return targetStaffMember;
    }

    public void setTargetStaffMember(StaffMember targetStaffMember) {
        this.targetStaffMember = targetStaffMember;
    }

    public RotaShift getTargetShift() {
        return targetShift;
    }

    public void setTargetShift(RotaShift targetShift) {
        this.targetShift = targetShift;
    }

    public ShiftSwapStatus getStatus() {
        return ShiftSwapStatus.fromDatabaseValue(statusValue);
    }

    public void setStatus(ShiftSwapStatus status) {
        this.statusValue = status == null ? null : status.getDatabaseValue();
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedAt() {
        return createdAtValue;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAtValue = createdAt;
    }

    public StaffMember getManagerActedBy() {
        return managerActedBy;
    }

    public void setManagerActedBy(StaffMember managerActedBy) {
        this.managerActedBy = managerActedBy;
    }

    public String getManagerActedAt() {
        return managerActedAtValue;
    }

    public void setManagerActedAt(String managerActedAt) {
        this.managerActedAtValue = managerActedAt;
    }

    @JsonIgnore
    public String getStatusValue() {
        return statusValue;
    }

    @JsonIgnore
    public String getTargetResponseNote() {
        return targetResponseNote;
    }

    public void setTargetResponseNote(String targetResponseNote) {
        this.targetResponseNote = targetResponseNote;
    }

    @JsonIgnore
    public String getManagerNote() {
        return managerNote;
    }

    public void setManagerNote(String managerNote) {
        this.managerNote = managerNote;
    }

    @JsonIgnore
    public String getTargetRespondedAt() {
        return targetRespondedAt;
    }

    public void setTargetRespondedAt(String targetRespondedAt) {
        this.targetRespondedAt = targetRespondedAt;
    }

    private String now() {
        return LocalDateTime.now().withNano(0).toString();
    }
}
