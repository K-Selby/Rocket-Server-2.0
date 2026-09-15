package uk.co.rocketpub.staffportal.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.model.RotaShift;
import uk.co.rocketpub.staffportal.model.RotaWeek;
import uk.co.rocketpub.staffportal.model.ShiftSwapRequest;
import uk.co.rocketpub.staffportal.model.ShiftSwapStatus;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.repository.RotaShiftRepository;
import uk.co.rocketpub.staffportal.repository.ShiftSwapRequestRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;

@Service
public class ShiftSwapService {

    private final ShiftSwapRequestRepository swapRepository;
    private final RotaShiftRepository shiftRepository;
    private final StaffMemberRepository staffRepository;
    private final StaffSettingsService settingsService;

    public ShiftSwapService(
            ShiftSwapRequestRepository swapRepository,
            RotaShiftRepository shiftRepository,
            StaffMemberRepository staffRepository,
            StaffSettingsService settingsService) {

        this.swapRepository = swapRepository;
        this.shiftRepository = shiftRepository;
        this.staffRepository = staffRepository;
        this.settingsService = settingsService;
    }

    public List<ShiftSwapRequest> getIncomingRequests(Long staffMemberId) {
        findStaff(staffMemberId);

        return swapRepository
                .findByTargetStaffMemberIdOrderByIdDesc(
                        staffMemberId
                );
    }

    public List<ShiftSwapRequest> getOutgoingRequests(Long staffMemberId) {
        findStaff(staffMemberId);

        return swapRepository
                .findByRequesterIdOrderByIdDesc(
                        staffMemberId
                );
    }

    // Requests waiting for a Manager/Admin decision.
    public List<ShiftSwapRequest> getManagerRequests(Long currentUserId) {
        requireManager(currentUserId);

        return swapRepository.findByStatusValueOrderByIdAsc(
                ShiftSwapStatus.AWAITING_MANAGER_APPROVAL
                        .getDatabaseValue()
        );
    }

    // Creates a request asking another member of staff to cover a shift.
    @Transactional
    public ShiftSwapRequest createRequest(
            Long currentUserId,
            Long requesterShiftId,
            Long targetStaffMemberId,
            Long targetShiftId,
            String note) {

        StaffMember currentUser = findStaff(currentUserId);
        RotaShift requesterShift = findShift(requesterShiftId);

        validatePublishedRota(
                requesterShift.getRotaWeek()
        );

        StaffMember requester =
                publishedOwner(requesterShift);

        if (!requester.getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "You can only request cover for your own shift"
            );
        }

        StaffMember targetStaff =
                findStaff(targetStaffMemberId);

        if (requester.getId().equals(targetStaff.getId())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "You cannot send your shift to yourself"
            );
        }

        if (!targetStaff.isActive()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This staff member is no longer active"
            );
        }

        if (!targetStaff.isActiveOnRota()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This staff member is not active on the rota"
            );
        }

        if (targetStaff.getRole() == StaffRole.ADMIN) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Admin cannot be assigned rota shifts"
            );
        }

        if (targetShiftId != null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Cover requests should not contain a target shift"
            );
        }

        boolean alreadyPending = swapRepository
                .findByRequesterIdOrderByIdDesc(
                        requester.getId()
                )
                .stream()
                .anyMatch(request ->
                        request.getRequesterShift()
                                .getId()
                                .equals(requesterShiftId)
                        && (
                                request.getStatus()
                                        == ShiftSwapStatus.PENDING
                                || request.getStatus()
                                        == ShiftSwapStatus.AWAITING_MANAGER_APPROVAL
                        )
                );

        if (alreadyPending) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This shift already has an active cover request"
            );
        }

        ShiftSwapRequest request =
                new ShiftSwapRequest();

        request.setRequesterShift(requesterShift);
        request.setRequester(requester);
        request.setTargetStaffMember(targetStaff);
        request.setTargetShift(null);
        request.setStatus(ShiftSwapStatus.PENDING);
        request.setNote(cleanNote(note));

        return swapRepository.save(request);
    }

    /*
     * The person receiving the shift accepts it.
     * It either completes immediately or waits for a Manager.
     */
    @Transactional
    public ShiftSwapRequest approveRequest(
            Long requestId,
            Long currentUserId) {

        ShiftSwapRequest request =
                findPendingRequest(requestId);

        if (!request.getTargetStaffMember()
                .getId()
                .equals(currentUserId)) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Only the person receiving this request can accept it"
            );
        }

        validateRequestStillValid(request);

        request.setTargetRespondedAt(now());

        boolean managerApproval =
                settingsService
                        .getSettings()
                        .isRequireManagerShiftSwapApproval();

        if (managerApproval) {
            request.setStatus(
                    ShiftSwapStatus.AWAITING_MANAGER_APPROVAL
            );

            return swapRepository.save(request);
        }

        transferShift(request);

        request.setStatus(
                ShiftSwapStatus.APPROVED
        );

        return swapRepository.save(request);
    }

    // Recipient declines a pending request.
    @Transactional
    public ShiftSwapRequest rejectRequest(
            Long requestId,
            Long currentUserId) {

        ShiftSwapRequest request =
                findPendingRequest(requestId);

        if (!request.getTargetStaffMember()
                .getId()
                .equals(currentUserId)) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Only the person receiving this request can decline it"
            );
        }

        request.setStatus(
                ShiftSwapStatus.REJECTED
        );
        request.setTargetRespondedAt(now());

        return swapRepository.save(request);
    }

    // Requester may cancel while it is still awaiting a decision.
    @Transactional
    public ShiftSwapRequest cancelRequest(
            Long requestId,
            Long currentUserId) {

        ShiftSwapRequest request =
                findRequest(requestId);

        if (!request.getRequester()
                .getId()
                .equals(currentUserId)) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Only the requester can cancel this request"
            );
        }

        if (request.getStatus() != ShiftSwapStatus.PENDING
                && request.getStatus()
                != ShiftSwapStatus.AWAITING_MANAGER_APPROVAL) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This request can no longer be cancelled"
            );
        }

        request.setStatus(
                ShiftSwapStatus.CANCELLED
        );

        return swapRepository.save(request);
    }

    // Manager gives final approval and the shift moves.
    @Transactional
    public ShiftSwapRequest managerApprove(
            Long requestId,
            Long currentUserId) {

        StaffMember manager =
                requireManager(currentUserId);

        ShiftSwapRequest request =
                findAwaitingManagerRequest(requestId);

        validateRequestStillValid(request);
        transferShift(request);

        request.setStatus(
                ShiftSwapStatus.APPROVED
        );
        request.setManagerActedBy(manager);
        request.setManagerActedAt(now());

        return swapRepository.save(request);
    }

    // Manager stops an accepted cover request.
    @Transactional
    public ShiftSwapRequest managerReject(
            Long requestId,
            Long currentUserId) {

        StaffMember manager =
                requireManager(currentUserId);

        ShiftSwapRequest request =
                findAwaitingManagerRequest(requestId);

        request.setStatus(
                ShiftSwapStatus.REJECTED
        );
        request.setManagerActedBy(manager);
        request.setManagerActedAt(now());

        return swapRepository.save(request);
    }

    // Transfers both working and published ownership of the shift.
    private void transferShift(
            ShiftSwapRequest request) {

        RotaShift shift =
                request.getRequesterShift();

        StaffMember newOwner =
                request.getTargetStaffMember();

        shift.setStaffMember(newOwner);
        shift.setPublishedStaffMember(newOwner);

        shiftRepository.save(shift);
    }

    private void validateRequestStillValid(
            ShiftSwapRequest request) {

        RotaShift shift =
                request.getRequesterShift();

        validatePublishedRota(
                shift.getRotaWeek()
        );

        StaffMember currentOwner =
                publishedOwner(shift);

        if (currentOwner == null
                || !currentOwner.getId()
                .equals(request.getRequester().getId())) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This shift has changed since the request was sent"
            );
        }

        if (!request.getTargetStaffMember().isActive()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "The selected staff member is no longer active"
            );
        }

        if (!request.getTargetStaffMember().isActiveOnRota()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "The selected staff member is no longer active on the rota"
            );
        }
    }

    private ShiftSwapRequest findPendingRequest(
            Long requestId) {

        ShiftSwapRequest request =
                findRequest(requestId);

        if (request.getStatus()
                != ShiftSwapStatus.PENDING) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This request is no longer pending"
            );
        }

        return request;
    }

    private ShiftSwapRequest findAwaitingManagerRequest(
            Long requestId) {

        ShiftSwapRequest request =
                findRequest(requestId);

        if (request.getStatus()
                != ShiftSwapStatus.AWAITING_MANAGER_APPROVAL) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This request is not waiting for manager approval"
            );
        }

        return request;
    }

    private ShiftSwapRequest findRequest(
            Long requestId) {

        return swapRepository.findById(requestId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Shift cover request not found"
                        )
                );
    }

    private StaffMember requireManager(
            Long currentUserId) {

        StaffMember currentUser =
                findStaff(currentUserId);

        if (currentUser.getRole() != StaffRole.MANAGER
                && currentUser.getRole() != StaffRole.ADMIN) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Manager access required"
            );
        }

        return currentUser;
    }

    private RotaShift findShift(Long id) {
        if (id == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Rota shift is required"
            );
        }

        return shiftRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Rota shift not found"
                        )
                );
    }

    private StaffMember findStaff(Long id) {
        if (id == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Staff member is required"
            );
        }

        return staffRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Staff member not found"
                        )
                );
    }

    private void validatePublishedRota(
            RotaWeek week) {

        if (week == null || !week.isPublished()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This rota has not been published yet"
            );
        }

        if (week.isEditing()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This rota is currently being edited"
            );
        }
    }

    private StaffMember publishedOwner(
            RotaShift shift) {

        if (shift.isHasPublishedVersion()
                && shift.getPublishedStaffMember() != null) {

            return shift.getPublishedStaffMember();
        }

        return shift.getStaffMember();
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) return null;

        String cleaned = note.trim();

        if (cleaned.length() > 300) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Note must be 300 characters or fewer"
            );
        }

        return cleaned;
    }

    private String now() {
        return LocalDateTime.now()
                .withNano(0)
                .toString();
    }
}