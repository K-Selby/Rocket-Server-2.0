package uk.co.rocketpub.staffportal.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.rocketpub.staffportal.model.*;
import uk.co.rocketpub.staffportal.repository.StaffDiaryEntryRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.repository.RotaShiftRepository;
import uk.co.rocketpub.staffportal.service.StaffSettingsService;

@Service
public class StaffNotificationService {
    private final RocketEmailService email;
    private final StaffMemberRepository staff;
    private final StaffDiaryEntryRepository diary;
    private final RotaShiftRepository rotaShifts;
    private final StaffSettingsService settings;

    public StaffNotificationService(RocketEmailService email, StaffMemberRepository staff,
            StaffDiaryEntryRepository diary, RotaShiftRepository rotaShifts, StaffSettingsService settings) {
        this.email = email; this.staff = staff; this.diary = diary; this.rotaShifts = rotaShifts; this.settings = settings;
    }

    public void requestDecision(StaffMember recipient, List<LocalDate> dates, boolean approved) {
        String title = "Day-off request " + (approved ? "approved" : "declined");
        sendHtml(recipient, recipient.isNotifyRequestDecisions(), "Rocket " + title,
                brandedEmail(recipient, title, "Your day-off request has been "
                        + (approved ? "approved." : "declined."), dayOffDetails(dates),
                        "View request", "/staff/inbox"));
    }

    public void shiftRequest(StaffMember recipient, String requesterName) {
        sendHtml(recipient, recipient.isNotifyShiftSwaps(), "New Rocket shift cover request",
                brandedEmail(recipient, "New shift cover request",
                        requesterName + " has sent you a shift cover request.",
                        detailRows(List.of(List.of("Requested by", requesterName))),
                        "Review shift cover request", "/staff/inbox"));
    }

    public void shiftResult(List<StaffMember> recipients, boolean approved) {
        for (StaffMember recipient : recipients) {
            String title = "Shift cover " + (approved ? "approved" : "declined");
            sendHtml(recipient, recipient.isNotifyShiftSwaps(), "Rocket " + title,
                    brandedEmail(recipient, title,
                            approved
                                    ? "The shift cover has been approved by a manager. The rota has been updated."
                                    : "The shift cover request has been declined. The original shift remains on the rota.",
                            "", approved ? "View updated rota" : "View rota", "/staff/rota"));
        }
    }

    public void managerShiftApprovalNeeded() {
        for (StaffMember manager : managers()) sendHtml(manager, manager.isNotifyShiftSwaps(),
                "Rocket shift cover needs approval",
                brandedEmail(manager, "Shift cover needs approval",
                        "A shift cover has been accepted and is waiting for manager approval.", "",
                        "Review and approve", "/staff/inbox"));
    }

    public void rotaPublished(LocalDate weekStart) {
        for (StaffMember member : staff.findByActiveTrueOrderByNameAsc()) {
            sendRota(member, weekStart, false);
        }
    }

    public void sendTest(Long staffMemberId, String notificationType, LocalDate weekStart) {
        StaffMember recipient = testRecipient(staffMemberId);
        LocalDate shiftDate = weekStart.plusDays(1);
        LocalDate dayOffStart = weekStart.plusWeeks(2).plusDays(5);

        switch (notificationType) {
            case "ROTA_PUBLISHED" -> sendRota(recipient, weekStart, true);
            case "SHIFT_REQUEST" -> sendTestEmail(recipient, "New shift cover request",
                    "Alex has asked you to cover the following shift.",
                    detailRows(List.of(
                            List.of("Date", shiftDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))),
                            List.of("Shift", "5 - F"))),
                    "Review shift cover request", "/staff/inbox");
            case "SHIFT_MANAGER_APPROVAL" -> sendTestEmail(recipient, "Shift cover needs approval",
                    "A shift cover has been accepted and is waiting for manager approval.",
                    detailRows(List.of(
                            List.of("From", "Alex"),
                            List.of("Covering", "Jamie"),
                            List.of("Date", shiftDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))),
                            List.of("Shift", "5 - F"))),
                    "Review and approve", "/staff/inbox");
            case "SHIFT_APPROVED" -> sendTestEmail(recipient, "Shift cover approved",
                    "The shift cover has been approved by a manager. The rota has been updated.",
                    detailRows(List.of(
                            List.of("Date", shiftDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))),
                            List.of("Shift", "5 - F"),
                            List.of("Covering", "Jamie"))),
                    "View updated rota", "/staff/rota");
            case "SHIFT_DECLINED" -> sendTestEmail(recipient, "Shift cover declined",
                    "The shift cover request was declined. The original shift remains on the rota.",
                    detailRows(List.of(
                            List.of("Date", shiftDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))),
                            List.of("Shift", "5 - F"))),
                    "View rota", "/staff/rota");
            case "DAY_OFF_SUMMARY" -> sendTestEmail(recipient, "3 day-off requests waiting",
                    "There are 3 day-off requests waiting for manager review.",
                    detailRows(List.of(List.of("Outstanding requests", "3"))),
                    "Review day-off requests", "/staff/inbox");
            case "DAY_OFF_APPROVED" -> sendTestEmail(recipient, "Day-off request approved",
                    "Your day-off request has been approved.",
                    dayOffDetails(List.of(dayOffStart)),
                    "View request", "/staff/inbox");
            case "DAY_OFF_DECLINED" -> sendTestEmail(recipient, "Day-off request declined",
                    "Your day-off request has been declined. Open the request to view its details.",
                    dayOffDetails(List.of(dayOffStart, dayOffStart.plusDays(2), dayOffStart.plusDays(3))),
                    "View request", "/staff/inbox");
            default -> throw new IllegalArgumentException("Unknown notification type.");
        }
    }

    private StaffMember testRecipient(Long staffMemberId) {
        StaffMember recipient = staff.findById(staffMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
        if (!recipient.isEmailVerified() || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            throw new IllegalArgumentException("This staff member does not have a verified email address.");
        }
        return recipient;
    }

    private void sendTestEmail(StaffMember recipient, String title, String message,
            String details, String linkLabel, String path) {
        String body = brandedEmail(recipient, title, message, details, linkLabel, path);
        email.sendHtmlEmail(recipient.getEmail(), "TEST: " + title, body);
    }

    private String detailRows(List<List<String>> rows) {
        StringBuilder html = new StringBuilder("<table role=\"presentation\" style=\"width:100%;margin:8px 0 24px;border-collapse:collapse\">");
        for (List<String> row : rows) {
            html.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid #e2e9e3;color:#667269\">")
                    .append(escape(row.get(0)))
                    .append("</td><td style=\"padding:10px 0;border-bottom:1px solid #e2e9e3;color:#145d36;text-align:right;font-weight:800\">")
                    .append(escape(row.get(1))).append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    private String dayOffDetails(List<LocalDate> requestedDates) {
        List<LocalDate> dates = requestedDates == null
                ? List.of()
                : requestedDates.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (dates.isEmpty()) return "";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy");
        List<String> ranges = new java.util.ArrayList<>();
        LocalDate rangeStart = dates.get(0);
        LocalDate rangeEnd = rangeStart;

        for (int index = 1; index < dates.size(); index++) {
            LocalDate date = dates.get(index);
            if (date.equals(rangeEnd.plusDays(1))) {
                rangeEnd = date;
            } else {
                ranges.add(formatDateRange(rangeStart, rangeEnd, formatter));
                rangeStart = date;
                rangeEnd = date;
            }
        }
        ranges.add(formatDateRange(rangeStart, rangeEnd, formatter));

        String label = dates.size() == 1 ? "Day requested" : "Days requested";
        StringBuilder html = new StringBuilder("<div style=\"margin:8px 0 24px;padding:14px 16px;border:1px solid #e2e9e3;border-radius:8px;background:#f7faf8\">"
                + "<strong style=\"display:block;margin-bottom:8px;color:#172235\">" + label + "</strong>"
                + "<ul style=\"margin:0;padding-left:20px;color:#145d36;font-weight:700;line-height:1.7\">");
        for (String range : ranges) html.append("<li>").append(escape(range)).append("</li>");
        return html.append("</ul></div>").toString();
    }

    private String formatDateRange(LocalDate start, LocalDate end, DateTimeFormatter formatter) {
        return start.equals(end)
                ? start.format(formatter)
                : start.format(formatter) + " – " + end.format(formatter);
    }

    private String brandedEmail(StaffMember recipient, String title, String message,
            String details, String linkLabel, String path) {
        String url = "https://rocketpubserver.co.uk" + path;
        return "<div style=\"margin:0;padding:24px;background:#eef3ef;font-family:Arial,sans-serif;color:#172235\">"
                + "<div style=\"max-width:620px;margin:0 auto;padding:28px;background:#ffffff;border:1px solid #d8e2da;border-radius:12px\">"
                + "<div style=\"margin-bottom:22px;color:#b28d22;font-size:13px;font-weight:800;letter-spacing:1.4px;text-transform:uppercase\">The Rocket Pub</div>"
                + "<h1 style=\"margin:0 0 18px;font-size:25px\">" + escape(title) + "</h1>"
                + "<p style=\"margin:0 0 14px;line-height:1.6\">Hi " + escape(recipient.getName()) + ",</p>"
                + "<p style=\"margin:0 0 16px;line-height:1.6\">" + escape(message) + "</p>"
                + details
                + "<a href=\"" + url + "\" style=\"display:inline-block;padding:13px 18px;border-radius:7px;background:#126238;color:#ffffff;font-weight:800;text-decoration:none\">" + escape(linkLabel) + "</a>"
                + "<p style=\"margin:18px 0 0;color:#6a746d;font-size:13px;line-height:1.5\">You can also open <a href=\"" + url + "\" style=\"color:#126238;font-weight:700\">the Staff Portal</a> in your browser.</p>"
                + "</div></div>";
    }

    private void sendRota(StaffMember recipient, LocalDate weekStart, boolean test) {
        if (!test && (!settings.getSettings().isNotificationsEnabled()
                || !recipient.isNotificationsEnabled() || !recipient.isNotifyPublishedRotas()
                || !recipient.isEmailVerified() || recipient.getEmail() == null || recipient.getEmail().isBlank())) return;

        LocalDate weekEnd = weekStart.plusDays(6);
        DateTimeFormatter rangeFormat = DateTimeFormatter.ofPattern("d MMMM yyyy");
        DateTimeFormatter shiftDateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM");
        List<RotaShift> shifts = rotaShifts.findByRotaWeekWeekStart(weekStart.toString()).stream()
                .filter(RotaShift::isHasPublishedVersion)
                .filter(shift -> shift.getPublishedStaffMember() != null
                        && shift.getPublishedStaffMember().getId().equals(recipient.getId()))
                .sorted(Comparator.comparing(RotaShift::getPublishedShiftDate)
                        .thenComparing(RotaShift::getPublishedStartTime))
                .toList();

        StringBuilder shiftRows = new StringBuilder();
        for (RotaShift shift : shifts) {
            String date = LocalDate.parse(shift.getPublishedShiftDate()).format(shiftDateFormat);
            String times = shift.isPublishedKitchenShift()
                    ? "K"
                    : formatTime(shift.getPublishedStartTime()) + " - "
                            + (shift.isPublishedFinishShift() ? "F" : formatTime(shift.getPublishedEndTime()));
            shiftRows.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid #e2e9e3;color:#172235;font-weight:700\">")
                    .append(escape(date)).append("</td><td style=\"padding:10px 0;border-bottom:1px solid #e2e9e3;color:#145d36;text-align:right;font-weight:800\">")
                    .append(escape(times)).append("</td></tr>");
        }
        if (shifts.isEmpty()) {
            shiftRows.append("<tr><td colspan=\"2\" style=\"padding:14px 0;color:#667269\">You have no shifts scheduled for this week.</td></tr>");
        }

        String range = weekStart.format(rangeFormat) + " to " + weekEnd.format(rangeFormat);
        String body = "<div style=\"margin:0;padding:24px;background:#eef3ef;font-family:Arial,sans-serif;color:#172235\">"
                + "<div style=\"max-width:620px;margin:0 auto;padding:28px;background:#ffffff;border:1px solid #d8e2da;border-radius:12px\">"
                + "<div style=\"margin-bottom:22px;color:#b28d22;font-size:13px;font-weight:800;letter-spacing:1.4px;text-transform:uppercase\">The Rocket Pub</div>"
                + "<h1 style=\"margin:0 0 18px;font-size:25px\">Your rota has been published</h1>"
                + "<p style=\"margin:0 0 14px;line-height:1.6\">Hi " + escape(recipient.getName()) + ",</p>"
                + "<p style=\"margin:0 0 16px;line-height:1.6\">The rota for <strong>" + escape(range) + "</strong> has been published. Your shifts are shown below.</p>"
                + "<table role=\"presentation\" style=\"width:100%;margin:8px 0 24px;border-collapse:collapse\">" + shiftRows + "</table>"
                + "<a href=\"https://rocketpubserver.co.uk/staff/rota\" style=\"display:inline-block;padding:13px 18px;border-radius:7px;background:#126238;color:#ffffff;font-weight:800;text-decoration:none\">View full rota or request cover</a>"
                + "<p style=\"margin:18px 0 0;color:#6a746d;font-size:13px;line-height:1.5\">You can also open <a href=\"https://rocketpubserver.co.uk/staff/rota\" style=\"color:#126238;font-weight:700\">the Staff Portal rota</a> in your browser.</p>"
                + "</div></div>";
        email.sendHtmlEmail(recipient.getEmail(), test ? "TEST: Rocket rota published" : "Rocket rota published", body);
    }

    private String formatTime(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.split(":");
        int hour = Integer.parseInt(parts[0]);
        int displayHour = hour == 0 ? 12 : hour > 12 ? hour - 12 : hour;
        return parts.length < 2 || "00".equals(parts[1])
                ? String.valueOf(displayHour)
                : displayHour + ":" + parts[1];
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/London")
    public void dailyManagerDayOffSummary() {
        long count = diary.findByTypeValueOrderByIdAsc(StaffDiaryType.DAY_OFF.getDatabaseValue()).stream()
                .filter(entry -> entry.getStatus() == StaffDiaryStatus.REQUESTED)
                .map(entry -> entry.getRequestGroupId() == null ? "entry-" + entry.getId() : entry.getRequestGroupId())
                .distinct().count();
        if (count == 0) return;
        String subject = count + (count == 1 ? " day-off request" : " day-off requests") + " waiting";
        String message = "There " + (count == 1 ? "is 1 day-off request" : "are " + count + " day-off requests") + " waiting for review.";
        for (StaffMember manager : managers()) sendHtml(manager, manager.isNotifyRequestDecisions(), subject,
                brandedEmail(manager, subject, message,
                        detailRows(List.of(List.of("Outstanding requests", String.valueOf(count)))),
                        "Review day-off requests", "/staff/inbox"));
    }

    private List<StaffMember> managers() {
        return staff.findByActiveTrueOrderByNameAsc().stream().filter(member -> member.getRole() == StaffRole.MANAGER || member.getRole() == StaffRole.ADMIN).toList();
    }

    private void sendHtml(StaffMember recipient, boolean preference, String subject, String body) {
        if (!settings.getSettings().isNotificationsEnabled() || recipient == null || !recipient.isNotificationsEnabled()
                || !preference || !recipient.isEmailVerified() || recipient.getEmail() == null || recipient.getEmail().isBlank()) return;
        try { email.sendHtmlEmail(recipient.getEmail(), subject, body); } catch (RuntimeException ignored) { /* A notification must never break the user's action. */ }
    }
}
