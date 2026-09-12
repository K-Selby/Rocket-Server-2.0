package uk.co.rocketpub.staffportal.controller;

import java.time.LocalDate;
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

import uk.co.rocketpub.staffportal.dto.CreateStaffDiaryEntryRequest;
import uk.co.rocketpub.staffportal.model.StaffDiaryEntry;
import uk.co.rocketpub.staffportal.service.StaffDiaryService;

@RestController
@RequestMapping("/api/diary")
@CrossOrigin(origins = "http://localhost:3000")
public class StaffDiaryController {

    private final StaffDiaryService service;

    public StaffDiaryController(StaffDiaryService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffDiaryEntry> getAllEntries() {
        return service.getAllEntries();
    }

    @GetMapping("/range")
    public List<StaffDiaryEntry> getEntriesBetween(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        return service.getEntriesBetween(from, to);
    }

    // currentUserId remains temporary until all APIs use the session user.
    @PostMapping
    public List<StaffDiaryEntry> createEntries(
            @RequestParam Long currentUserId,
            @RequestBody CreateStaffDiaryEntryRequest request) {

        return service.createEntries(
                currentUserId,
                request
        );
    }

    @PutMapping("/{entryId}/approve")
    public StaffDiaryEntry approveEntry(
            @RequestParam Long currentUserId,
            @PathVariable Long entryId) {

        return service.approveEntry(
                currentUserId,
                entryId
        );
    }

    @PutMapping("/{entryId}/reject")
    public void rejectEntry(
            @RequestParam Long currentUserId,
            @PathVariable Long entryId) {

        service.rejectEntry(
                currentUserId,
                entryId
        );
    }

    @DeleteMapping("/{entryId}")
    public void deleteEntry(
            @RequestParam Long currentUserId,
            @PathVariable Long entryId) {

        service.deleteEntry(
                currentUserId,
                entryId
        );
    }
}
