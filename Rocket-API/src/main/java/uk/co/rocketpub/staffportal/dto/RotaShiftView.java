package uk.co.rocketpub.staffportal.dto;

import uk.co.rocketpub.staffportal.model.StaffMember;

import java.time.LocalDate;
import java.time.LocalTime;

// Clean JSON representation of one rota shift.
public class RotaShiftView {

    private Long id;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean finishShift;
    private boolean kitchenShift;
    private String note;
    private StaffMember staffMember;

    public RotaShiftView() {
    }

    public RotaShiftView(
            Long id,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            boolean finishShift,
            boolean kitchenShift,
            String note,
            StaffMember staffMember) {

        this.id = id;
        this.shiftDate = shiftDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.finishShift = finishShift;
        this.kitchenShift = kitchenShift;
        this.note = note;
        this.staffMember = staffMember;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isFinishShift() {
        return finishShift;
    }

    public boolean isKitchenShift() {
        return kitchenShift;
    }

    public String getNote() {
        return note;
    }

    public StaffMember getStaffMember() {
        return staffMember;
    }
}
