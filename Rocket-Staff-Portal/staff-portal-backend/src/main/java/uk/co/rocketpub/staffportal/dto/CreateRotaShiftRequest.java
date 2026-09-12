package uk.co.rocketpub.staffportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// Represents the JSON needed to create one rota shift.
public class CreateRotaShiftRequest {

    private Long staffMemberId;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean finishShift;
    private boolean kitchenShift;
    private String note;

    public CreateRotaShiftRequest() {
    }

    public Long getStaffMemberId() {
        return staffMemberId;
    }

    public void setStaffMemberId(Long staffMemberId) {
        this.staffMemberId = staffMemberId;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isFinishShift() {
        return finishShift;
    }

    public void setFinishShift(boolean finishShift) {
        this.finishShift = finishShift;
    }

    public boolean isKitchenShift() {
        return kitchenShift;
    }

    public void setKitchenShift(boolean kitchenShift) {
        this.kitchenShift = kitchenShift;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
