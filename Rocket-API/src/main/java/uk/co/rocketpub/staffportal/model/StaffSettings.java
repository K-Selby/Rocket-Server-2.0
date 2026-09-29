package uk.co.rocketpub.staffportal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_settings")
public class StaffSettings {

    @Id
    private Long id;

    // False by default: accepted covers update the rota immediately.
    @Column(name = "require_manager_shift_cover_approval", nullable = false)
    private boolean requireManagerShiftSwapApproval = false;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    public StaffSettings() {
    }

    public StaffSettings(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isRequireManagerShiftSwapApproval() {
        return requireManagerShiftSwapApproval;
    }

    public void setRequireManagerShiftSwapApproval(boolean requireManagerShiftSwapApproval) {
        this.requireManagerShiftSwapApproval = requireManagerShiftSwapApproval;
    }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean value) { notificationsEnabled = value; }
}
