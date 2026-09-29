package uk.co.rocketpub.staffportal.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.rocketpub.staffportal.model.*;
import uk.co.rocketpub.staffportal.repository.StaffDiaryEntryRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.service.StaffSettingsService;

@Service
public class StaffNotificationService {
    private final RocketEmailService email;
    private final StaffMemberRepository staff;
    private final StaffDiaryEntryRepository diary;
    private final StaffSettingsService settings;

    public StaffNotificationService(RocketEmailService email, StaffMemberRepository staff,
            StaffDiaryEntryRepository diary, StaffSettingsService settings) {
        this.email = email; this.staff = staff; this.diary = diary; this.settings = settings;
    }

    public void requestDecision(StaffMember recipient, String requestName, boolean approved) {
        send(recipient, recipient.isNotifyRequestDecisions(), "Rocket request " + (approved ? "approved" : "declined"),
                "Your " + requestName + " has been " + (approved ? "approved." : "declined.") + "\n\nOpen the Staff Portal to view the details.");
    }

    public void shiftRequest(StaffMember recipient, String requesterName) {
        send(recipient, recipient.isNotifyShiftSwaps(), "New Rocket shift cover request",
                requesterName + " has sent you a shift cover request.\n\nOpen the Staff Portal Inbox to respond.");
    }

    public void shiftResult(List<StaffMember> recipients, boolean approved) {
        for (StaffMember recipient : recipients) send(recipient, recipient.isNotifyShiftSwaps(),
                "Rocket shift cover " + (approved ? "approved" : "declined"),
                "The shift cover request has been " + (approved ? "approved by a manager." : "declined.") + "\n\nOpen the Staff Portal Inbox to view it.");
    }

    public void managerShiftApprovalNeeded() {
        for (StaffMember manager : managers()) send(manager, manager.isNotifyShiftSwaps(),
                "Rocket shift cover needs approval", "A shift cover request is waiting for manager approval.\n\nOpen the Staff Portal Inbox to review it.");
    }

    public void rotaPublished(LocalDate weekStart) {
        String date = weekStart.format(DateTimeFormatter.ofPattern("d MMMM yyyy"));
        for (StaffMember member : staff.findByActiveTrueOrderByNameAsc()) send(member, member.isNotifyPublishedRotas(),
                "Rocket rota published", "The rota for the week beginning " + date + " has been published or updated.\n\nOpen the Staff Portal to view it.");
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/London")
    public void dailyManagerDayOffSummary() {
        long count = diary.findByTypeValueOrderByIdAsc(StaffDiaryType.DAY_OFF.getDatabaseValue()).stream()
                .filter(entry -> entry.getStatus() == StaffDiaryStatus.REQUESTED)
                .map(entry -> entry.getRequestGroupId() == null ? "entry-" + entry.getId() : entry.getRequestGroupId())
                .distinct().count();
        if (count == 0) return;
        String subject = count + (count == 1 ? " day-off request" : " day-off requests") + " waiting";
        String body = "There " + (count == 1 ? "is 1 day-off request" : "are " + count + " day-off requests") + " waiting for review.\n\nOpen the Staff Portal Inbox to review them.";
        for (StaffMember manager : managers()) send(manager, manager.isNotifyRequestDecisions(), subject, body);
    }

    private List<StaffMember> managers() {
        return staff.findByActiveTrueOrderByNameAsc().stream().filter(member -> member.getRole() == StaffRole.MANAGER || member.getRole() == StaffRole.ADMIN).toList();
    }

    private void send(StaffMember recipient, boolean preference, String subject, String body) {
        if (!settings.getSettings().isNotificationsEnabled() || recipient == null || !recipient.isNotificationsEnabled()
                || !preference || !recipient.isEmailVerified() || recipient.getEmail() == null || recipient.getEmail().isBlank()) return;
        try { email.sendEmail(recipient.getEmail(), subject, body); } catch (RuntimeException ignored) { /* A notification must never break the user's action. */ }
    }
}
