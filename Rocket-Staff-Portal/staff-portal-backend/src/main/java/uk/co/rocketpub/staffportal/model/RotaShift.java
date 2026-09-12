package uk.co.rocketpub.staffportal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rota_shift")
public class RotaShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stored as ISO text to avoid SQLite JDBC date/time parsing.
    @Column(name = "shift_date")
    private String shiftDate;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    @Column(name = "finish_shift")
    private boolean finishShift;

    @Column(name = "kitchen_shift")
    private boolean kitchenShift;

    private String note;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private StaffMember staffMember;

    @ManyToOne
    @JoinColumn(
            name = "rota_week_start",
            referencedColumnName = "week_start"
    )
    private RotaWeek rotaWeek;

    @Column(name = "has_published_version")
    private boolean hasPublishedVersion;

    @Column(name = "working_deleted")
    private boolean workingDeleted;

    @Column(name = "published_shift_date")
    private String publishedShiftDate;

    @Column(name = "published_start_time")
    private String publishedStartTime;

    @Column(name = "published_end_time")
    private String publishedEndTime;

    @Column(name = "published_finish_shift")
    private boolean publishedFinishShift;

    @Column(name = "published_kitchen_shift")
    private boolean publishedKitchenShift;

    @Column(name = "published_note")
    private String publishedNote;

    @ManyToOne
    @JoinColumn(name = "published_user_id")
    private StaffMember publishedStaffMember;

    public RotaShift() {}

    public Long getId() { return id; }

    public String getShiftDate() { return shiftDate; }
    public void setShiftDate(String shiftDate) { this.shiftDate = shiftDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isFinishShift() { return finishShift; }
    public void setFinishShift(boolean finishShift) { this.finishShift = finishShift; }

    public boolean isKitchenShift() { return kitchenShift; }
    public void setKitchenShift(boolean kitchenShift) { this.kitchenShift = kitchenShift; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public StaffMember getStaffMember() { return staffMember; }
    public void setStaffMember(StaffMember staffMember) { this.staffMember = staffMember; }

    public RotaWeek getRotaWeek() { return rotaWeek; }
    public void setRotaWeek(RotaWeek rotaWeek) { this.rotaWeek = rotaWeek; }

    public boolean isHasPublishedVersion() { return hasPublishedVersion; }
    public void setHasPublishedVersion(boolean hasPublishedVersion) {
        this.hasPublishedVersion = hasPublishedVersion;
    }

    public boolean isWorkingDeleted() { return workingDeleted; }
    public void setWorkingDeleted(boolean workingDeleted) {
        this.workingDeleted = workingDeleted;
    }

    public String getPublishedShiftDate() { return publishedShiftDate; }
    public void setPublishedShiftDate(String publishedShiftDate) {
        this.publishedShiftDate = publishedShiftDate;
    }

    public String getPublishedStartTime() { return publishedStartTime; }
    public void setPublishedStartTime(String publishedStartTime) {
        this.publishedStartTime = publishedStartTime;
    }

    public String getPublishedEndTime() { return publishedEndTime; }
    public void setPublishedEndTime(String publishedEndTime) {
        this.publishedEndTime = publishedEndTime;
    }

    public boolean isPublishedFinishShift() { return publishedFinishShift; }
    public void setPublishedFinishShift(boolean publishedFinishShift) {
        this.publishedFinishShift = publishedFinishShift;
    }

    public boolean isPublishedKitchenShift() { return publishedKitchenShift; }
    public void setPublishedKitchenShift(boolean publishedKitchenShift) {
        this.publishedKitchenShift = publishedKitchenShift;
    }

    public String getPublishedNote() { return publishedNote; }
    public void setPublishedNote(String publishedNote) {
        this.publishedNote = publishedNote;
    }

    public StaffMember getPublishedStaffMember() { return publishedStaffMember; }
    public void setPublishedStaffMember(StaffMember publishedStaffMember) {
        this.publishedStaffMember = publishedStaffMember;
    }
}
