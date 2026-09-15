package uk.co.rocketpub.staffportal.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.dto.CreateDayOffRequest;
import uk.co.rocketpub.staffportal.model.DayOffRequest;
import uk.co.rocketpub.staffportal.model.DayOffRequestStatus;
import uk.co.rocketpub.staffportal.model.StaffDiaryEntry;
import uk.co.rocketpub.staffportal.model.StaffDiaryStatus;
import uk.co.rocketpub.staffportal.model.StaffDiaryType;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.repository.StaffDiaryEntryRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;

@Service
public class DayOffRequestService {

    private final StaffDiaryEntryRepository diaryRepository;
    private final StaffMemberRepository staffRepository;

    public DayOffRequestService(
            StaffDiaryEntryRepository diaryRepository,
            StaffMemberRepository staffRepository) {

        this.diaryRepository = diaryRepository;
        this.staffRepository = staffRepository;
    }

    // Managers/Admin share the same day-off request history.
    public List<DayOffRequest> getManagerRequests(Long currentUserId) {
        requireManagerOrAdmin(currentUserId);

        List<StaffDiaryEntry> entries = diaryRepository.findByTypeValueOrderByIdAsc(
                StaffDiaryType.DAY_OFF.getDatabaseValue()
        );

        return buildRequests(entries);
    }

    // Staff see their own grouped requests and decisions.
    public List<DayOffRequest> getMyRequests(Long currentUserId) {
        StaffMember currentUser = findStaffMember(currentUserId);

        if (currentUser.getRole() == StaffRole.ADMIN) return List.of();

        List<StaffDiaryEntry> entries =
                diaryRepository.findByStaffMemberIdAndTypeValueOrderByIdAsc(
                        currentUser.getId(),
                        StaffDiaryType.DAY_OFF.getDatabaseValue()
                );

        return buildRequests(entries);
    }

    // Kept for compatibility with any frontend still using this endpoint.
    @Transactional
    public DayOffRequest createRequest(Long currentUserId, CreateDayOffRequest request) {
        StaffMember currentUser = findStaffMember(currentUserId);
        List<LocalDate> dates = normaliseDates(request.getDates());

        validateConsecutiveDates(dates);

        StaffMember requester = resolveRequester(currentUser, request.getStaffMemberId());

        for (LocalDate date : dates) {
            validateDay(requester, date);
        }

        boolean approveImmediately = request.isApproveImmediately();

        if (approveImmediately && currentUser.getRole() != StaffRole.ADMIN) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Only Admin can directly approve a day off"
            );
        }

        String groupId = UUID.randomUUID().toString();
        String now = now();

        List<StaffDiaryEntry> entries = new ArrayList<>();

        for (LocalDate date : dates) {
            StaffDiaryEntry entry = new StaffDiaryEntry();

            entry.setEntryDate(date);
            entry.setType(StaffDiaryType.DAY_OFF);
            entry.setStaffMember(requester);
            entry.setRequestGroupId(groupId);
            entry.setNote(cleanText(request.getNote()));
            entry.setCreatedBy(currentUser);
            entry.setCreatedAt(now);

            if (approveImmediately) {
                entry.setStatus(StaffDiaryStatus.APPROVED);
                entry.setReviewedBy(currentUser);
                entry.setReviewedAt(now);
            } else {
                entry.setStatus(StaffDiaryStatus.REQUESTED);
            }

            entry.setAvailableFrom(null);
            entry.setAvailableTo(null);
            entry.setAvailableUntilFinish(false);

            entries.add(entry);
        }

        return buildRequest(diaryRepository.saveAll(entries));
    }

    // Approves every date belonging to the grouped request.
    @Transactional
    public DayOffRequest approve(Long currentUserId, Long requestId) {
        StaffMember manager = requireManagerOrAdmin(currentUserId);
        List<StaffDiaryEntry> entries = findRequestEntries(requestId);

        requirePending(entries);

        String now = now();

        for (StaffDiaryEntry entry : entries) {
            entry.setStatus(StaffDiaryStatus.APPROVED);
            entry.setReviewedBy(manager);
            entry.setReviewedAt(now);
        }

        return buildRequest(diaryRepository.saveAll(entries));
    }

    // Declining keeps the request for Inbox history but removes it from the Diary.
    @Transactional
    public DayOffRequest decline(Long currentUserId, Long requestId) {
        StaffMember manager = requireManagerOrAdmin(currentUserId);
        List<StaffDiaryEntry> entries = findRequestEntries(requestId);

        requirePending(entries);

        String now = now();

        for (StaffDiaryEntry entry : entries) {
            entry.setStatus(StaffDiaryStatus.REJECTED);
            entry.setReviewedBy(manager);
            entry.setReviewedAt(now);
        }

        return buildRequest(diaryRepository.saveAll(entries));
    }

    // Cancelling also keeps the grouped request for personal Inbox history.
    @Transactional
    public DayOffRequest cancel(Long currentUserId, Long requestId) {
        StaffMember currentUser = findStaffMember(currentUserId);
        List<StaffDiaryEntry> entries = findRequestEntries(requestId);

        requirePending(entries);

        StaffMember requester = entries.get(0).getStaffMember();
        boolean owner = requester != null && requester.getId().equals(currentUser.getId());

        if (!owner && currentUser.getRole() != StaffRole.ADMIN) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "You can only cancel your own request"
            );
        }

        String now = now();

        for (StaffDiaryEntry entry : entries) {
            entry.setStatus(StaffDiaryStatus.CANCELLED);
            entry.setReviewedBy(currentUser);
            entry.setReviewedAt(now);
        }

        return buildRequest(diaryRepository.saveAll(entries));
    }

    // Groups the individual Diary rows into Inbox requests.
    private List<DayOffRequest> buildRequests(List<StaffDiaryEntry> entries) {
        Map<String, List<StaffDiaryEntry>> groups = new LinkedHashMap<>();

        for (StaffDiaryEntry entry : entries) {
            String key = entry.getRequestGroupId();

            // Older single-day rows without a group still become one Inbox request.
            if (key == null || key.isBlank()) key = "entry-" + entry.getId();

            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        return groups.values()
                .stream()
                .map(this::buildRequest)
                .sorted(Comparator.comparing(DayOffRequest::getCreatedAt).reversed())
                .toList();
    }

    private DayOffRequest buildRequest(List<StaffDiaryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Day-off request not found");
        }

        List<StaffDiaryEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(StaffDiaryEntry::getEntryDate))
                .toList();

        StaffDiaryEntry first = sorted.get(0);

        DayOffRequest request = new DayOffRequest();

        request.setId(sorted.stream().map(StaffDiaryEntry::getId).min(Long::compareTo).orElse(first.getId()));
        request.setRequester(first.getStaffMember());
        request.setDates(sorted.stream().map(StaffDiaryEntry::getEntryDate).toList());
        request.setStatus(resolveStatus(sorted));
        request.setNote(firstNonBlankNote(sorted));
        request.setCreatedAt(earliestCreatedAt(sorted));
        request.setActedBy(firstReviewedBy(sorted));
        request.setActedAt(latestReviewedAt(sorted));

        return request;
    }

    private DayOffRequestStatus resolveStatus(List<StaffDiaryEntry> entries) {
        boolean approved = entries.stream().allMatch(entry -> entry.getStatus() == StaffDiaryStatus.APPROVED);
        boolean rejected = entries.stream().allMatch(entry -> entry.getStatus() == StaffDiaryStatus.REJECTED);
        boolean cancelled = entries.stream().allMatch(entry -> entry.getStatus() == StaffDiaryStatus.CANCELLED);

        if (approved) return DayOffRequestStatus.APPROVED;
        if (rejected) return DayOffRequestStatus.DECLINED;
        if (cancelled) return DayOffRequestStatus.CANCELLED;

        return DayOffRequestStatus.PENDING;
    }

    private List<StaffDiaryEntry> findRequestEntries(Long requestId) {
        StaffDiaryEntry selected = diaryRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Day-off request not found"));

        if (selected.getType() != StaffDiaryType.DAY_OFF) {
            throw new ResponseStatusException(NOT_FOUND, "Day-off request not found");
        }

        String groupId = selected.getRequestGroupId();

        if (groupId == null || groupId.isBlank()) return List.of(selected);

        List<StaffDiaryEntry> entries =
                diaryRepository.findByRequestGroupIdOrderByEntryDateValueAsc(groupId);

        return entries.stream()
                .filter(entry -> entry.getType() == StaffDiaryType.DAY_OFF)
                .toList();
    }

    private void requirePending(List<StaffDiaryEntry> entries) {
        boolean pending = entries.stream()
                .allMatch(entry -> entry.getStatus() == StaffDiaryStatus.REQUESTED);

        if (!pending) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Day-off request is no longer pending"
            );
        }
    }

    private String firstNonBlankNote(List<StaffDiaryEntry> entries) {
        return entries.stream()
                .map(StaffDiaryEntry::getNote)
                .filter(note -> note != null && !note.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String earliestCreatedAt(List<StaffDiaryEntry> entries) {
        return entries.stream()
                .map(StaffDiaryEntry::getCreatedAt)
                .filter(value -> value != null && !value.isBlank())
                .min(String::compareTo)
                .orElse("");
    }

    private StaffMember firstReviewedBy(List<StaffDiaryEntry> entries) {
        return entries.stream()
                .map(StaffDiaryEntry::getReviewedBy)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private String latestReviewedAt(List<StaffDiaryEntry> entries) {
        return entries.stream()
                .map(StaffDiaryEntry::getReviewedAt)
                .filter(value -> value != null && !value.isBlank())
                .max(String::compareTo)
                .orElse(null);
    }

    private void validateDay(StaffMember staffMember, LocalDate date) {
        List<StaffDiaryEntry> existing =
                diaryRepository.findByStaffMemberIdAndEntryDateValue(
                        staffMember.getId(),
                        date.toString()
                );

        boolean hasDayOff = existing.stream().anyMatch(entry ->
                entry.getType() == StaffDiaryType.DAY_OFF
                        && entry.getStatus() != StaffDiaryStatus.REJECTED
                        && entry.getStatus() != StaffDiaryStatus.CANCELLED
        );

        boolean hasAvailability = existing.stream()
                .anyMatch(entry -> entry.getType() == StaffDiaryType.AVAILABLE);

        if (hasDayOff) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "A day-off request already exists for " + date
            );
        }

        if (hasAvailability) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Availability already exists for " + date
            );
        }

        if (diaryRepository.existsByEntryDateValueAndTypeValue(
                date.toString(),
                StaffDiaryType.NO_ONE_OFF.getDatabaseValue())) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "No one can request " + date + " off"
            );
        }
    }

    private List<LocalDate> normaliseDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "At least one date is required"
            );
        }

        return dates.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private void validateConsecutiveDates(List<LocalDate> dates) {
        for (int index = 1; index < dates.size(); index++) {
            LocalDate expected = dates.get(index - 1).plusDays(1);

            if (!dates.get(index).equals(expected)) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Day-off request dates must be consecutive"
                );
            }
        }
    }

    private StaffMember resolveRequester(
            StaffMember currentUser,
            Long requestedStaffMemberId) {

        if (currentUser.getRole() != StaffRole.ADMIN) {
            return requireSchedulableStaff(currentUser);
        }

        if (requestedStaffMemberId == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Staff member is required"
            );
        }

        return requireSchedulableStaff(findStaffMember(requestedStaffMemberId));
    }

    private StaffMember requireSchedulableStaff(StaffMember staffMember) {
        if (staffMember.getRole() == StaffRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Admin cannot request days off");
        }

        if (!staffMember.isActive()) {
            throw new ResponseStatusException(BAD_REQUEST, "Inactive staff cannot request days off");
        }

        return staffMember;
    }

    private StaffMember requireManagerOrAdmin(Long currentUserId) {
        StaffMember currentUser = findStaffMember(currentUserId);

        if (currentUser.getRole() != StaffRole.MANAGER && currentUser.getRole() != StaffRole.ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Manager or Admin permission required");
        }

        return currentUser;
    }

    private StaffMember findStaffMember(Long id) {
        if (id == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Current user is required");
        }

        return staffRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Staff member not found"));
    }

    private String cleanText(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        return value.trim();
    }

    private String now() {
        return LocalDateTime.now().withNano(0).toString();
    }
}
