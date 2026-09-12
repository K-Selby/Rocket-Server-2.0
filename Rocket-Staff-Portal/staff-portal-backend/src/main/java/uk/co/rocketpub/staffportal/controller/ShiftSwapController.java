package uk.co.rocketpub.staffportal.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.co.rocketpub.staffportal.dto.CreateShiftSwapRequest;
import uk.co.rocketpub.staffportal.model.ShiftSwapRequest;
import uk.co.rocketpub.staffportal.service.ShiftSwapService;

@RestController
@RequestMapping("/api/shift-swaps")
@CrossOrigin(origins = "https://rocketpubserver.co.uk")
public class ShiftSwapController {

    private final ShiftSwapService service;

    public ShiftSwapController(ShiftSwapService service) {
        this.service = service;
    }

    @GetMapping("/incoming/{staffMemberId}")
    public List<ShiftSwapRequest> getIncoming(
            @PathVariable Long staffMemberId) {

        return service.getIncomingRequests(staffMemberId);
    }

    @GetMapping("/outgoing/{staffMemberId}")
    public List<ShiftSwapRequest> getOutgoing(
            @PathVariable Long staffMemberId) {

        return service.getOutgoingRequests(staffMemberId);
    }

    @GetMapping("/manager/pending")
    public List<ShiftSwapRequest> getManagerPending(
            @RequestParam Long currentUserId) {

        return service.getManagerRequests(currentUserId);
    }

    @PostMapping
    public ShiftSwapRequest createRequest(
            @RequestParam Long currentUserId,
            @RequestBody CreateShiftSwapRequest request) {

        return service.createRequest(
                currentUserId,
                request.getRequesterShiftId(),
                request.getTargetStaffMemberId(),
                request.getTargetShiftId(),
                request.getNote()
        );
    }

    @PutMapping("/{requestId}/approve")
    public ShiftSwapRequest approve(
            @PathVariable Long requestId,
            @RequestParam Long currentUserId) {

        return service.approveRequest(requestId, currentUserId);
    }

    @PutMapping("/{requestId}/reject")
    public ShiftSwapRequest reject(
            @PathVariable Long requestId,
            @RequestParam Long currentUserId) {

        return service.rejectRequest(requestId, currentUserId);
    }

    @PutMapping("/{requestId}/cancel")
    public ShiftSwapRequest cancel(
            @PathVariable Long requestId,
            @RequestParam Long currentUserId) {

        return service.cancelRequest(requestId, currentUserId);
    }

    @PutMapping("/{requestId}/manager-approve")
    public ShiftSwapRequest managerApprove(
            @PathVariable Long requestId,
            @RequestParam Long currentUserId) {

        return service.managerApprove(requestId, currentUserId);
    }

    @PutMapping("/{requestId}/manager-reject")
    public ShiftSwapRequest managerReject(
            @PathVariable Long requestId,
            @RequestParam Long currentUserId) {

        return service.managerReject(requestId, currentUserId);
    }
}
