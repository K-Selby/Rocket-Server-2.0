package uk.co.rocketpub.staffportal.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.dto.CreateRotaShiftRequest;
import uk.co.rocketpub.staffportal.dto.CreateRotaWeekRequest;
import uk.co.rocketpub.staffportal.dto.RotaShiftView;
import uk.co.rocketpub.staffportal.model.RotaShift;
import uk.co.rocketpub.staffportal.model.RotaWeek;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.repository.RotaShiftRepository;
import uk.co.rocketpub.staffportal.repository.RotaWeekRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RotaService {

    private final RotaWeekRepository rotaWeekRepository;
    private final RotaShiftRepository rotaShiftRepository;
    private final StaffMemberRepository staffMemberRepository;

    public RotaService(
            RotaWeekRepository rotaWeekRepository,
            RotaShiftRepository rotaShiftRepository,
            StaffMemberRepository staffMemberRepository) {

        this.rotaWeekRepository = rotaWeekRepository;
        this.rotaShiftRepository = rotaShiftRepository;
        this.staffMemberRepository = staffMemberRepository;
    }

    public List<RotaWeek> getRotaWeeks(Long currentUserId) {
        StaffMember user = findOptionalUser(currentUserId);

        if (user != null && canManageRota(user)) {
            return rotaWeekRepository.findAllByOrderByWeekStartAsc();
        }

        return rotaWeekRepository.findByPublishedTrueOrderByWeekStartAsc();
    }

    public RotaWeek getWeek(String weekStart, Long currentUserId) {
        RotaWeek week = getRotaWeek(weekStart);
        StaffMember user = findOptionalUser(currentUserId);

        if (!week.isPublished() && (user == null || !canManageRota(user))) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "No rota available for this week"
            );
        }

        return week;
    }

    public List<RotaShiftView> getShiftsForWeek(
            String weekStart,
            Long currentUserId) {

        RotaWeek week = getRotaWeek(weekStart);
        StaffMember user = findOptionalUser(currentUserId);

        boolean manager = user != null && canManageRota(user);

        if (!week.isPublished() && !manager) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "No rota available for this week"
            );
        }

        return rotaShiftRepository
                .findByRotaWeekWeekStart(weekStart)
                .stream()
                .filter(shift ->
                        manager
                                ? !shift.isWorkingDeleted()
                                : shift.isHasPublishedVersion()
                )
                .map(shift ->
                        manager
                                ? workingView(shift)
                                : publishedView(shift)
                )
                .toList();
    }

    public RotaWeek getRotaWeek(String weekStart) {
        validateWeekStart(weekStart);

        return rotaWeekRepository.findById(weekStart)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Rota week not found"
                ));
    }

    public RotaShiftView createShift(
            Long currentUserId,
            String weekStart,
            CreateRotaShiftRequest request) {

        requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);
        requireEditable(week);

        StaffMember staff = findStaff(request.getStaffMemberId());

        validateShiftRequest(week, request);

        RotaShift shift = new RotaShift();
        shift.setRotaWeek(week);

        applyWorkingValues(shift, staff, request);

        return workingView(rotaShiftRepository.save(shift));
    }

    public RotaShiftView updateShift(
            Long currentUserId,
            Long shiftId,
            CreateRotaShiftRequest request) {

        requireManagerOrAdmin(currentUserId);

        RotaShift shift = findShift(shiftId);

        requireEditable(shift.getRotaWeek());

        StaffMember staff = findStaff(request.getStaffMemberId());

        validateShiftRequest(shift.getRotaWeek(), request);

        shift.setWorkingDeleted(false);

        applyWorkingValues(shift, staff, request);

        return workingView(rotaShiftRepository.save(shift));
    }

    public void deleteShift(Long currentUserId, Long shiftId) {
        requireManagerOrAdmin(currentUserId);

        RotaShift shift = findShift(shiftId);
        RotaWeek week = shift.getRotaWeek();

        requireEditable(week);

        if (week.isPublished() && shift.isHasPublishedVersion()) {
            shift.setWorkingDeleted(true);
            rotaShiftRepository.save(shift);
            return;
        }

        rotaShiftRepository.delete(shift);
    }

    public RotaWeek createRotaWeek(
            Long currentUserId,
            CreateRotaWeekRequest request) {

        requireManagerOrAdmin(currentUserId);

        if (request.getWeekStart() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Week start date is required"
            );
        }

        LocalDate start = request.getWeekStart();

        if (start.getDayOfWeek() != DayOfWeek.SUNDAY) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Rota week must start on a Sunday"
            );
        }

        String weekStart = start.toString();

        if (rotaWeekRepository.existsById(weekStart)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "A rota already exists for this week"
            );
        }

        RotaWeek week = new RotaWeek();

        week.setWeekStart(weekStart);
        week.setPublished(false);
        week.setEditing(true);
        week.setNotes(request.getNotes());

        return rotaWeekRepository.save(week);
    }

    public RotaWeek startEditing(
            Long currentUserId,
            String weekStart) {

        requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);

        if (!week.isPublished()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This rota is already a draft"
            );
        }

        week.setEditing(true);

        return rotaWeekRepository.save(week);
    }

    public RotaWeek publishRotaWeek(
            Long currentUserId,
            String weekStart) {

        StaffMember manager = requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);

        requireEditable(week);

        List<RotaShift> shifts =
                rotaShiftRepository.findByRotaWeekWeekStart(weekStart);

        for (RotaShift shift : shifts) {
            if (shift.isWorkingDeleted()) {
                rotaShiftRepository.delete(shift);
                continue;
            }

            copyWorkingToPublished(shift);

            rotaShiftRepository.save(shift);
        }

        week.setPublishedHiddenStaffIds(
                new HashSet<>(week.getHiddenStaffIds())
        );

        week.setPublished(true);
        week.setEditing(false);
        week.setPublishedAt(LocalDateTime.now());
        week.setPublishedBy(manager);

        return rotaWeekRepository.save(week);
    }

    public RotaWeek hideStaff(
            Long currentUserId,
            String weekStart,
            Long staffMemberId) {

        requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);
        requireEditable(week);

        StaffMember staff = findStaff(staffMemberId);

        if (staff.getRole() == StaffRole.ADMIN) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Admin is not part of the rota"
            );
        }

        week.getHiddenStaffIds().add(staffMemberId);

        return rotaWeekRepository.save(week);
    }

    public RotaWeek showStaff(
            Long currentUserId,
            String weekStart,
            Long staffMemberId) {

        requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);
        requireEditable(week);

        week.getHiddenStaffIds().remove(staffMemberId);

        return rotaWeekRepository.save(week);
    }

    public RotaWeek dismissAvailability(
            Long currentUserId,
            String weekStart,
            Long diaryEntryId) {

        requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);
        requireEditable(week);

        week.getDismissedAvailabilityIds().add(diaryEntryId);

        return rotaWeekRepository.save(week);
    }

    public RotaWeek restoreAvailability(
            Long currentUserId,
            String weekStart,
            Long diaryEntryId) {

        requireManagerOrAdmin(currentUserId);

        RotaWeek week = getRotaWeek(weekStart);
        requireEditable(week);

        week.getDismissedAvailabilityIds().remove(diaryEntryId);

        return rotaWeekRepository.save(week);
    }

    private void applyWorkingValues(
            RotaShift shift,
            StaffMember staff,
            CreateRotaShiftRequest request) {

        shift.setStaffMember(staff);

        shift.setShiftDate(
                request.getShiftDate() == null
                        ? null
                        : request.getShiftDate().toString()
        );

        shift.setStartTime(formatTime(request.getStartTime()));
        shift.setEndTime(formatTime(request.getEndTime()));

        shift.setFinishShift(request.isFinishShift());
        shift.setKitchenShift(request.isKitchenShift());
        shift.setNote(request.getNote());
    }

    private void copyWorkingToPublished(RotaShift shift) {
        shift.setHasPublishedVersion(true);
        shift.setWorkingDeleted(false);

        shift.setPublishedStaffMember(shift.getStaffMember());
        shift.setPublishedShiftDate(shift.getShiftDate());
        shift.setPublishedStartTime(shift.getStartTime());
        shift.setPublishedEndTime(shift.getEndTime());

        shift.setPublishedFinishShift(shift.isFinishShift());
        shift.setPublishedKitchenShift(shift.isKitchenShift());

        shift.setPublishedNote(shift.getNote());
    }

    private RotaShiftView workingView(RotaShift shift) {
        return new RotaShiftView(
                shift.getId(),
                parseDate(shift.getShiftDate()),
                parseTime(shift.getStartTime()),
                parseTime(shift.getEndTime()),
                shift.isFinishShift(),
                shift.isKitchenShift(),
                shift.getNote(),
                shift.getStaffMember()
        );
    }

    private RotaShiftView publishedView(RotaShift shift) {
        return new RotaShiftView(
                shift.getId(),
                parseDate(shift.getPublishedShiftDate()),
                parseTime(shift.getPublishedStartTime()),
                parseTime(shift.getPublishedEndTime()),
                shift.isPublishedFinishShift(),
                shift.isPublishedKitchenShift(),
                shift.getPublishedNote(),
                shift.getPublishedStaffMember()
        );
    }

    private void validateShiftRequest(
            RotaWeek week,
            CreateRotaShiftRequest request) {

        LocalDate weekStart = LocalDate.parse(week.getWeekStart());

        if (request.getShiftDate() == null
                || request.getShiftDate().isBefore(weekStart)
                || request.getShiftDate().isAfter(weekStart.plusDays(6))) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Shift date must be within the selected rota week"
            );
        }

        if (request.isKitchenShift()) {
            if (request.getStartTime() != null
                    || request.getEndTime() != null
                    || request.isFinishShift()) {

                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Kitchen shifts cannot contain start/end times or finish shift"
                );
            }

            return;
        }

        if (request.getStartTime() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Normal shifts require a start time"
            );
        }

        if (request.getEndTime() != null && request.isFinishShift()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Shift cannot have both an end time and finish shift"
            );
        }

        if (request.getEndTime() == null && !request.isFinishShift()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Shift requires either an end time or finish shift"
            );
        }

        if (request.getEndTime() != null
                && !request.getEndTime().isAfter(request.getStartTime())) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Shift end time must be after start time"
            );
        }
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.withNano(0).toString();
    }

    private LocalDate parseDate(String date) {
        return date == null || date.isBlank()
                ? null
                : LocalDate.parse(date);
    }

    private LocalTime parseTime(String time) {
        return time == null || time.isBlank()
                ? null
                : LocalTime.parse(time);
    }

    private void validateWeekStart(String weekStart) {
        try {
            LocalDate date = LocalDate.parse(weekStart);

            if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Rota week must start on a Sunday"
                );
            }
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid rota week date"
            );
        }
    }

    private void requireEditable(RotaWeek week) {
        if (!week.isEditing()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Open the rota for editing first"
            );
        }
    }

    private StaffMember requireManagerOrAdmin(Long currentUserId) {
        StaffMember user = findStaff(currentUserId);

        if (!canManageRota(user)) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Manager or Admin permission required"
            );
        }

        return user;
    }

    private boolean canManageRota(StaffMember user) {
        return user.getRole() == StaffRole.MANAGER
                || user.getRole() == StaffRole.ADMIN;
    }

    private StaffMember findStaff(Long id) {
        return staffMemberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Staff member not found"
                ));
    }

    private RotaShift findShift(Long id) {
        return rotaShiftRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Rota shift not found"
                ));
    }

    private StaffMember findOptionalUser(Long id) {
        return id == null
                ? null
                : staffMemberRepository.findById(id).orElse(null);
    }
}
