package uk.co.rocketpub.staffportal.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.dto.CreateStaffDiaryEntryRequest;
import uk.co.rocketpub.staffportal.model.StaffDiaryEntry;
import uk.co.rocketpub.staffportal.model.StaffDiaryStatus;
import uk.co.rocketpub.staffportal.model.StaffDiaryType;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.repository.StaffDiaryEntryRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;

@Service
public class StaffDiaryService {

    private final StaffDiaryEntryRepository diaryRepository;
    private final StaffMemberRepository staffRepository;

    public StaffDiaryService(
            StaffDiaryEntryRepository diaryRepository,
            StaffMemberRepository staffRepository) {

        this.diaryRepository = diaryRepository;
        this.staffRepository = staffRepository;
    }

    // Returns all visible Diary entries.
    public List<StaffDiaryEntry> getAllEntries() {
        return diaryRepository.findAll()
                .stream()
                .filter(entry -> entry.getStatus() != StaffDiaryStatus.REJECTED)
                .filter(entry -> entry.getStatus() != StaffDiaryStatus.CANCELLED)
                .sorted(Comparator.comparing(StaffDiaryEntry::getEntryDate))
                .toList();
    }

    // Returns visible entries within a calendar range.
    public List<StaffDiaryEntry> getEntriesBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid diary date range");
        }

        return diaryRepository
                .findByEntryDateValueBetweenOrderByIdAsc(from.toString(), to.toString())
                .stream()
                .filter(entry -> entry.getStatus() != StaffDiaryStatus.REJECTED)
                .filter(entry -> entry.getStatus() != StaffDiaryStatus.CANCELLED)
                .toList();
    }

    // Creates either one entry or several selected dates.
    @Transactional
    public List<StaffDiaryEntry> createEntries(
            Long currentUserId,
            CreateStaffDiaryEntryRequest request) {

        StaffMember currentUser = findStaffMember(currentUserId);

        validateRequest(request);

        List<LocalDate> dates = resolveDates(request);

        if (request.getType() == StaffDiaryType.NO_ONE_OFF) {
            return createNoOneOffEntries(currentUser, request, dates);
        }

        StaffMember targetStaff = resolveTargetStaff(
                currentUser,
                request.getStaffMemberId());

        if (request.getType() == StaffDiaryType.AVAILABLE) {
            validateAvailability(request);
        }

        for (LocalDate date : dates) {
            validateConflicts(
                    targetStaff,
                    date,
                    request.getType());
        }

        String requestGroupId = request.getType() == StaffDiaryType.DAY_OFF
                ? UUID.randomUUID().toString()
                : null;

        List<StaffDiaryEntry> entries = new ArrayList<>();

        for (LocalDate date : dates) {
            StaffDiaryEntry entry = new StaffDiaryEntry();

            entry.setEntryDate(date);
            entry.setType(request.getType());
            entry.setStaffMember(targetStaff);
            entry.setCreatedBy(currentUser);
            entry.setCreatedAt(now());
            entry.setRequestGroupId(requestGroupId);
            entry.setNote(cleanNote(request.getNote()));

            configureEntry(currentUser, entry, request);

            entries.add(entry);
        }

        return diaryRepository.saveAll(entries);
    }

    // Approving one date approves every date in its grouped request.
    @Transactional
    public StaffDiaryEntry approveEntry(
            Long currentUserId,
            Long entryId) {

        StaffMember reviewer = requireManagerOrAdmin(currentUserId);
        StaffDiaryEntry selectedEntry = findEntry(entryId);

        List<StaffDiaryEntry> entries = getRequestEntries(selectedEntry);

        for (StaffDiaryEntry entry : entries) {
            if (entry.getStatus() != StaffDiaryStatus.REQUESTED) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Only requested diary entries can be approved");
            }
        }

        String reviewedAt = now();

        for (StaffDiaryEntry entry : entries) {
            entry.setStatus(StaffDiaryStatus.APPROVED);
            entry.setReviewedBy(reviewer);
            entry.setReviewedAt(reviewedAt);
        }

        diaryRepository.saveAll(entries);

        return selectedEntry;
    }

    // Rejecting one date rejects every date in its grouped request.
    @Transactional
    public void rejectEntry(
            Long currentUserId,
            Long entryId) {

        StaffMember reviewer = requireManagerOrAdmin(currentUserId);
        StaffDiaryEntry selectedEntry = findEntry(entryId);

        List<StaffDiaryEntry> entries = getRequestEntries(selectedEntry);

        for (StaffDiaryEntry entry : entries) {
            if (entry.getStatus() != StaffDiaryStatus.REQUESTED) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Only requested diary entries can be rejected");
            }
        }

        String reviewedAt = now();

        for (StaffDiaryEntry entry : entries) {
            entry.setStatus(StaffDiaryStatus.REJECTED);
            entry.setReviewedBy(reviewer);
            entry.setReviewedAt(reviewedAt);
        }

        diaryRepository.saveAll(entries);
    }

    /*
     * Staff and Managers can remove their own entries.
     * Admin can remove anyone's entries.
     *
     * A grouped day-off request is removed as one request.
     */
    @Transactional
    public void deleteEntry(
            Long currentUserId,
            Long entryId) {

        StaffMember currentUser = findStaffMember(currentUserId);
        StaffDiaryEntry selectedEntry = findEntry(entryId);

        if (selectedEntry.getType() == StaffDiaryType.NO_ONE_OFF) {
            if (currentUser.getRole() != StaffRole.MANAGER
                    && currentUser.getRole() != StaffRole.ADMIN) {

                throw new ResponseStatusException(
                        FORBIDDEN,
                        "Manager or Admin permission required");
            }

            diaryRepository.delete(selectedEntry);
            return;
        }

        StaffMember owner = selectedEntry.getStaffMember();

        if (currentUser.getRole() != StaffRole.ADMIN
                && (owner == null
                        || !owner.getId().equals(currentUser.getId()))) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "You can only delete your own Diary entries");
        }

        diaryRepository.deleteAll(getRequestEntries(selectedEntry));
    }

    private void configureEntry(
            StaffMember currentUser,
            StaffDiaryEntry entry,
            CreateStaffDiaryEntryRequest request) {

        if (request.getType() == StaffDiaryType.DAY_OFF) {
            if (request.isApproveImmediately()) {
                if (currentUser.getRole() != StaffRole.ADMIN) {
                    throw new ResponseStatusException(
                            FORBIDDEN,
                            "Only Admin can directly approve a day off");
                }

                entry.setStatus(StaffDiaryStatus.APPROVED);
                entry.setReviewedBy(currentUser);
                entry.setReviewedAt(now());

            } else {
                entry.setStatus(StaffDiaryStatus.REQUESTED);
            }

            entry.setAvailableFrom(null);
            entry.setAvailableTo(null);
            entry.setAvailableUntilFinish(false);
            return;
        }

        if (request.getType() == StaffDiaryType.AVAILABLE) {
            entry.setStatus(StaffDiaryStatus.INFO);
            entry.setAvailableFrom(request.getAvailableFrom());
            entry.setAvailableTo(request.getAvailableTo());
            entry.setAvailableUntilFinish(request.isAvailableUntilFinish());
            return;
        }

        entry.setStatus(StaffDiaryStatus.INFO);
        entry.setAvailableFrom(null);
        entry.setAvailableTo(null);
        entry.setAvailableUntilFinish(false);
    }

    private List<StaffDiaryEntry> createNoOneOffEntries(
            StaffMember currentUser,
            CreateStaffDiaryEntryRequest request,
            List<LocalDate> dates) {

        if (currentUser.getRole() != StaffRole.MANAGER
                && currentUser.getRole() != StaffRole.ADMIN) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Manager or Admin permission required");
        }

        for (LocalDate date : dates) {
            if (diaryRepository.existsByEntryDateValueAndTypeValue(
                    date.toString(),
                    StaffDiaryType.NO_ONE_OFF.getDatabaseValue())) {

                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "NO ONE OFF already exists for " + date);
            }
        }

        List<StaffDiaryEntry> entries = new ArrayList<>();

        for (LocalDate date : dates) {
            StaffDiaryEntry entry = new StaffDiaryEntry();

            entry.setEntryDate(date);
            entry.setType(StaffDiaryType.NO_ONE_OFF);
            entry.setStatus(StaffDiaryStatus.INFO);
            entry.setStaffMember(null);
            entry.setCreatedBy(currentUser);
            entry.setCreatedAt(now());
            entry.setNote(cleanNote(request.getNote()));
            entry.setAvailableFrom(null);
            entry.setAvailableTo(null);
            entry.setAvailableUntilFinish(false);

            entries.add(entry);
        }

        return diaryRepository.saveAll(entries);
    }

    private List<StaffDiaryEntry> getRequestEntries(
            StaffDiaryEntry selectedEntry) {

        if (selectedEntry.getRequestGroupId() == null
                || selectedEntry.getRequestGroupId().isBlank()) {

            return List.of(selectedEntry);
        }

        List<StaffDiaryEntry> grouped = diaryRepository.findByRequestGroupIdOrderByEntryDateValueAsc(
                selectedEntry.getRequestGroupId());

        return grouped.isEmpty()
                ? List.of(selectedEntry)
                : grouped;
    }

    private List<LocalDate> resolveDates(
            CreateStaffDiaryEntryRequest request) {

        List<LocalDate> dates = new ArrayList<>();

        if (request.getEntryDates() != null) {
            dates.addAll(
                    request.getEntryDates()
                            .stream()
                            .filter(date -> date != null)
                            .toList());
        }

        if (request.getEntryDate() != null) {
            dates.add(request.getEntryDate());
        }

        dates = dates.stream()
                .distinct()
                .sorted()
                .toList();

        if (dates.isEmpty()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "At least one Diary date is required");
        }

        return dates;
    }

    private StaffMember resolveTargetStaff(
            StaffMember currentUser,
            Long requestedStaffMemberId) {

        // Staff and Managers can create personal entries only for themselves.
        if (currentUser.getRole() != StaffRole.ADMIN) {
            return requireSchedulableStaff(currentUser);
        }

        if (requestedStaffMemberId == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Staff member is required");
        }

        return requireSchedulableStaff(
                findStaffMember(requestedStaffMemberId));
    }

    private void validateConflicts(
            StaffMember staffMember,
            LocalDate date,
            StaffDiaryType requestedType) {

        List<StaffDiaryEntry> existingEntries = diaryRepository.findByStaffMemberIdAndEntryDateValue(
                staffMember.getId(),
                date.toString());

        boolean hasDayOff = existingEntries.stream()
                .anyMatch(entry -> entry.getType() == StaffDiaryType.DAY_OFF
                        && entry.getStatus() != StaffDiaryStatus.REJECTED);

        boolean hasAvailability = existingEntries.stream()
                .anyMatch(entry -> entry.getType() == StaffDiaryType.AVAILABLE);

        if (requestedType == StaffDiaryType.DAY_OFF) {
            if (hasDayOff) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "A day-off request already exists for " + date);
            }

            if (hasAvailability) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Availability already exists for " + date);
            }

            if (diaryRepository.existsByEntryDateValueAndTypeValue(
                    date.toString(),
                    StaffDiaryType.NO_ONE_OFF.getDatabaseValue())) {

                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "No one can request " + date + " off");
            }
        }

        if (requestedType == StaffDiaryType.AVAILABLE) {
            if (hasAvailability) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Availability already exists for " + date);
            }

            if (hasDayOff) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "A day-off entry already exists for " + date);
            }
        }
    }

    private void validateAvailability(
            CreateStaffDiaryEntryRequest request) {

        if (request.getAvailableFrom() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Availability start time is required");
        }

        if (!request.isAvailableUntilFinish()
                && request.getAvailableTo() == null) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Availability end time is required");
        }

        if (!request.isAvailableUntilFinish()
                && !request.getAvailableTo().isAfter(request.getAvailableFrom())) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Availability end time must be after start time");
        }
    }

    private void validateRequest(
            CreateStaffDiaryEntryRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Diary request is required");
        }

        if (request.getType() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Diary entry type is required");
        }
    }

    private StaffMember requireManagerOrAdmin(Long currentUserId) {
        StaffMember currentUser = findStaffMember(currentUserId);

        if (currentUser.getRole() != StaffRole.MANAGER
                && currentUser.getRole() != StaffRole.ADMIN) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Manager or Admin permission required");
        }

        return currentUser;
    }

    private StaffMember requireSchedulableStaff(
            StaffMember staffMember) {

        if (staffMember.getRole() == StaffRole.ADMIN) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Admin cannot be added to the Staff Diary");
        }

        if (!staffMember.isActive()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Inactive staff cannot be added to the Staff Diary");
        }

        return staffMember;
    }

    private String cleanNote(String note) {
        if (note == null || note.trim().isEmpty())
            return null;

        String clean = note.trim();

        if (clean.length() > 400) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Diary note cannot be longer than 400 characters");
        }

        return clean;
    }

    private String now() {
        return LocalDateTime.now().withNano(0).toString();
    }

    private StaffMember findStaffMember(Long id) {
        if (id == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Current user is required");
        }

        return staffRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Staff member not found"));
    }

    private StaffDiaryEntry findEntry(Long entryId) {
        return diaryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Diary entry not found"));
    }
}
