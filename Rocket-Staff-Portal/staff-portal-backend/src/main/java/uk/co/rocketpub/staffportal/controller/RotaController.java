package uk.co.rocketpub.staffportal.controller;

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

import uk.co.rocketpub.staffportal.dto.CreateRotaShiftRequest;
import uk.co.rocketpub.staffportal.dto.CreateRotaWeekRequest;
import uk.co.rocketpub.staffportal.dto.RotaShiftView;
import uk.co.rocketpub.staffportal.model.RotaWeek;
import uk.co.rocketpub.staffportal.service.RotaService;

import java.util.List;

@RestController
@RequestMapping("/api/rota")
@CrossOrigin(origins = "http://localhost:3000")
public class RotaController {

    private final RotaService service;

    public RotaController(RotaService service) {
        this.service = service;
    }

    @GetMapping("/weeks")
    public List<RotaWeek> getRotaWeeks(
            @RequestParam(required = false) Long currentUserId) {

        return service.getRotaWeeks(currentUserId);
    }

    @GetMapping("/weeks/{weekStart}")
    public RotaWeek getWeek(
            @PathVariable String weekStart,
            @RequestParam(required = false) Long currentUserId) {

        return service.getWeek(weekStart, currentUserId);
    }

    @GetMapping("/weeks/{weekStart}/shifts")
    public List<RotaShiftView> getShifts(
            @PathVariable String weekStart,
            @RequestParam(required = false) Long currentUserId) {

        return service.getShiftsForWeek(weekStart, currentUserId);
    }

    @PostMapping("/weeks/{weekStart}/shifts")
    public RotaShiftView createShift(
            @PathVariable String weekStart,
            @RequestParam Long currentUserId,
            @RequestBody CreateRotaShiftRequest request) {

        return service.createShift(currentUserId, weekStart, request);
    }

    @PutMapping("/shifts/{shiftId}")
    public RotaShiftView updateShift(
            @PathVariable Long shiftId,
            @RequestParam Long currentUserId,
            @RequestBody CreateRotaShiftRequest request) {

        return service.updateShift(currentUserId, shiftId, request);
    }

    @DeleteMapping("/shifts/{shiftId}")
    public void deleteShift(
            @PathVariable Long shiftId,
            @RequestParam Long currentUserId) {

        service.deleteShift(currentUserId, shiftId);
    }

    @PostMapping("/weeks")
    public RotaWeek createRotaWeek(
            @RequestParam Long currentUserId,
            @RequestBody CreateRotaWeekRequest request) {

        return service.createRotaWeek(currentUserId, request);
    }

    @PutMapping("/weeks/{weekStart}/edit")
    public RotaWeek startEditing(
            @PathVariable String weekStart,
            @RequestParam Long currentUserId) {

        return service.startEditing(currentUserId, weekStart);
    }

    @PutMapping("/weeks/{weekStart}/publish")
    public RotaWeek publishRotaWeek(
            @PathVariable String weekStart,
            @RequestParam Long currentUserId) {

        return service.publishRotaWeek(currentUserId, weekStart);
    }

    @PutMapping("/weeks/{weekStart}/hidden-staff/{staffMemberId}")
    public RotaWeek hideStaff(
            @PathVariable String weekStart,
            @PathVariable Long staffMemberId,
            @RequestParam Long currentUserId) {

        return service.hideStaff(currentUserId, weekStart, staffMemberId);
    }

    @DeleteMapping("/weeks/{weekStart}/hidden-staff/{staffMemberId}")
    public RotaWeek showStaff(
            @PathVariable String weekStart,
            @PathVariable Long staffMemberId,
            @RequestParam Long currentUserId) {

        return service.showStaff(currentUserId, weekStart, staffMemberId);
    }

    @PutMapping("/weeks/{weekStart}/dismissed-availability/{diaryEntryId}")
    public RotaWeek dismissAvailability(
            @PathVariable String weekStart,
            @PathVariable Long diaryEntryId,
            @RequestParam Long currentUserId) {

        return service.dismissAvailability(
                currentUserId,
                weekStart,
                diaryEntryId
        );
    }

    @DeleteMapping("/weeks/{weekStart}/dismissed-availability/{diaryEntryId}")
    public RotaWeek restoreAvailability(
            @PathVariable String weekStart,
            @PathVariable Long diaryEntryId,
            @RequestParam Long currentUserId) {

        return service.restoreAvailability(
                currentUserId,
                weekStart,
                diaryEntryId
        );
    }
}
