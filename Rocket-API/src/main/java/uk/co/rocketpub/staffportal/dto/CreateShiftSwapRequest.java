package uk.co.rocketpub.staffportal.dto;

// JSON sent when requesting a cover or swap.
public class CreateShiftSwapRequest {

    private Long requesterShiftId;
    private Long targetStaffMemberId;
    private Long targetShiftId;
    private String note;

    public CreateShiftSwapRequest() {
    }

    public Long getRequesterShiftId() {
        return requesterShiftId;
    }

    public void setRequesterShiftId(Long requesterShiftId) {
        this.requesterShiftId = requesterShiftId;
    }

    public Long getTargetStaffMemberId() {
        return targetStaffMemberId;
    }

    public void setTargetStaffMemberId(Long targetStaffMemberId) {
        this.targetStaffMemberId = targetStaffMemberId;
    }

    public Long getTargetShiftId() {
        return targetShiftId;
    }

    public void setTargetShiftId(Long targetShiftId) {
        this.targetShiftId = targetShiftId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
