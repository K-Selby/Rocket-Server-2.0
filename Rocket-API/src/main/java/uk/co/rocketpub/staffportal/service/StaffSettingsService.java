package uk.co.rocketpub.staffportal.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.model.StaffSettings;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.repository.StaffSettingsRepository;

@Service
public class StaffSettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final StaffSettingsRepository settingsRepository;
    private final StaffMemberRepository staffRepository;

    public StaffSettingsService(
            StaffSettingsRepository settingsRepository,
            StaffMemberRepository staffRepository) {

        this.settingsRepository = settingsRepository;
        this.staffRepository = staffRepository;
    }

    // Returns the shared Staff Portal settings row.
    public StaffSettings getSettings() {
        return settingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> settingsRepository.save(new StaffSettings(SETTINGS_ID)));
    }

    // Only Managers/Admin can change staff settings.
    public StaffSettings updateSettings(
            Long currentUserId,
            boolean requireManagerApproval) {

        StaffMember currentUser = staffRepository.findById(currentUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Current user not found"
                        )
                );

        if (currentUser.getRole() != StaffRole.MANAGER
                && currentUser.getRole() != StaffRole.ADMIN) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Manager access required"
            );
        }

        StaffSettings settings = getSettings();
        settings.setRequireManagerShiftSwapApproval(requireManagerApproval);

        return settingsRepository.save(settings);
    }
}
