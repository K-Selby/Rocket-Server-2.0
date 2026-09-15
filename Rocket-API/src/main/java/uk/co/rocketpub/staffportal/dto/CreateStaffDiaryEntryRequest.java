package uk.co.rocketpub.staffportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import uk.co.rocketpub.staffportal.model.StaffDiaryType;

// JSON sent when creating Staff Diary entries.
public class CreateStaffDiaryEntryRequest {

    private Long staffMemberId;

    // Kept for existing single-day requests.
    private LocalDate entryDate;

    // Used when several dates are selected together.
    private List<LocalDate> entryDates;

    private StaffDiaryType type;
    private LocalTime availableFrom;
    private LocalTime availableTo;
    private boolean availableUntilFinish;
    private String note;

    // Admin can add an approved day off directly.
    private boolean approveImmediately;

    public CreateStaffDiaryEntryRequest() {
    }

    public Long getStaffMemberId() {
        return staffMemberId;
    }

    public void setStaffMemberId(Long staffMemberId) {
        this.staffMemberId = staffMemberId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public List<LocalDate> getEntryDates() {
        return entryDates;
    }

    public void setEntryDates(List<LocalDate> entryDates) {
        this.entryDates = entryDates;
    }

    public StaffDiaryType getType() {
        return type;
    }

    public void setType(StaffDiaryType type) {
        this.type = type;
    }

    public LocalTime getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalTime availableFrom) {
        this.availableFrom = availableFrom;
    }

    public LocalTime getAvailableTo() {
        return availableTo;
    }

    public void setAvailableTo(LocalTime availableTo) {
        this.availableTo = availableTo;
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

    public boolean isApproveImmediately() {
        return approveImmediately;
    }

    public void setApproveImmediately(boolean approveImmediately) {
        this.approveImmediately = approveImmediately;
    }
}
