package uk.co.rocketpub.staffportal.dto;

import java.time.LocalDate;
import java.util.List;

// JSON used when submitting one consecutive day-off request.
public class CreateDayOffRequest {

    private Long staffMemberId;

    private List<LocalDate> dates;

    private String note;

    private boolean approveImmediately;

    public CreateDayOffRequest() {
    }

    public Long getStaffMemberId() {
        return staffMemberId;
    }

    public void setStaffMemberId(Long staffMemberId) {
        this.staffMemberId = staffMemberId;
    }

    public List<LocalDate> getDates() {
        return dates;
    }

    public void setDates(List<LocalDate> dates) {
        this.dates = dates;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isApproveImmediately() {
        return approveImmediately;
    }

    public void setApproveImmediately(boolean approveImmediately) {
        this.approveImmediately = approveImmediately;
    }
}
