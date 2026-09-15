package uk.co.rocketpub.staffportal.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Inbox view of one grouped day-off request.
public class DayOffRequest {

    private Long id;
    private StaffMember requester;
    private List<LocalDate> dates = new ArrayList<>();
    private DayOffRequestStatus status;
    private String note;
    private String createdAt;
    private StaffMember actedBy;
    private String actedAt;

    public DayOffRequest() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StaffMember getRequester() {
        return requester;
    }

    public void setRequester(StaffMember requester) {
        this.requester = requester;
    }

    public List<LocalDate> getDates() {
        return dates;
    }

    public void setDates(List<LocalDate> dates) {
        this.dates = dates;
    }

    public DayOffRequestStatus getStatus() {
        return status;
    }

    public void setStatus(DayOffRequestStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public StaffMember getActedBy() {
        return actedBy;
    }

    public void setActedBy(StaffMember actedBy) {
        this.actedBy = actedBy;
    }

    public String getActedAt() {
        return actedAt;
    }

    public void setActedAt(String actedAt) {
        this.actedAt = actedAt;
    }
}
