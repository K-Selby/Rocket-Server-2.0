package uk.co.rocketpub.staffportal.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.co.rocketpub.staffportal.dto.CreateDayOffRequest;
import uk.co.rocketpub.staffportal.model.DayOffRequest;
import uk.co.rocketpub.staffportal.service.DayOffRequestService;

@RestController
@RequestMapping("/api/day-off-requests")
@CrossOrigin(origins = "https://rocketpubserver.co.uk")
public class DayOffRequestController {

    private final DayOffRequestService service;

    public DayOffRequestController(DayOffRequestService service) {
        this.service = service;
    }

    @GetMapping("/manager")
    public List<DayOffRequest> getManagerRequests(
            @RequestParam Long currentUserId) {

        return service.getManagerRequests(currentUserId);
    }

    @GetMapping("/mine")
    public List<DayOffRequest> getMyRequests(
            @RequestParam Long currentUserId) {

        return service.getMyRequests(currentUserId);
    }

    @PostMapping
    public DayOffRequest createRequest(
            @RequestParam Long currentUserId,
            @RequestBody CreateDayOffRequest request) {

        return service.createRequest(currentUserId, request);
    }

    @PutMapping("/{requestId}/approve")
    public DayOffRequest approve(
            @RequestParam Long currentUserId,
            @PathVariable Long requestId) {

        return service.approve(currentUserId, requestId);
    }

    @PutMapping("/{requestId}/decline")
    public DayOffRequest decline(
            @RequestParam Long currentUserId,
            @PathVariable Long requestId) {

        return service.decline(currentUserId, requestId);
    }

    @DeleteMapping("/{requestId}")
    public DayOffRequest cancel(
            @RequestParam Long currentUserId,
            @PathVariable Long requestId) {

        return service.cancel(currentUserId, requestId);
    }
}
