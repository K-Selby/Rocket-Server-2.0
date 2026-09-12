package uk.co.rocketpub.staffportal.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.co.rocketpub.staffportal.model.StaffSettings;
import uk.co.rocketpub.staffportal.service.StaffSettingsService;

@RestController
@RequestMapping("/api/staff-settings")
@CrossOrigin(origins = "https://rocketpubserver.co.uk", allowCredentials = "true")
public class StaffSettingsController {

    private final StaffSettingsService service;

    public StaffSettingsController(StaffSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public StaffSettings getSettings() {
        return service.getSettings();
    }

    @PutMapping
    public StaffSettings updateSettings(
            @RequestParam Long currentUserId,
            @RequestBody StaffSettings request) {

        return service.updateSettings(
                currentUserId,
                request.isRequireManagerShiftSwapApproval()
        );
    }
}
