package uk.co.rocketpub.staffportal.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;
import uk.co.rocketpub.staffportal.auth.AuthUser;

@Service
public class StaffMemberService {

    private final StaffMemberRepository staffMemberRepository;
    private final PasswordService passwordService;

    public StaffMemberService(
            StaffMemberRepository staffMemberRepository,
            PasswordService passwordService) {

        this.staffMemberRepository = staffMemberRepository;
        this.passwordService = passwordService;
    }

    // Returns active accounts for Staff Management.
    public List<StaffMember> getAllStaff() {
        return staffMemberRepository.findByActiveTrueOrderByNameAsc();
    }

    /*
     * Returns active Staff and Managers.
     * Individual rota pages decide whether activeOnRota is required.
     */
    public List<StaffMember> getRotaStaff() {
        return staffMemberRepository
                .findByActiveTrueAndRoleNotOrderByNameAsc(
                        StaffRole.ADMIN
                );
    }

    public StaffMember getStaffMember(Long id) {
        return staffMemberRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Staff member not found"
                        ));
    }

    public StaffMember createStaffMember(
            StaffMember staffMember) {

        String name = cleanName(
                staffMember.getName()
        );

        if (staffMemberRepository
                .existsByNameIgnoreCase(name)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A user with that name already exists"
            );
        }

        staffMember.setName(name);
        staffMember.setActive(true);
        staffMember.setActiveOnRota(true);
        staffMember.setMustChangePassword(true);

        if (staffMember.getRole() == null) {
            staffMember.setRole(StaffRole.STAFF);
        }

        // Same temporary password behaviour as Rocket Pub Server.
        staffMember.setPasswordHash(
                passwordService.generate(PasswordService.TEMPORARY_PASSWORD)
        );

        return staffMemberRepository.save(staffMember);
    }

    public StaffMember updateStaffMember(
            Long id,
            StaffMember updatedStaffMember) {

        StaffMember existing = getStaffMember(id);

        String name = cleanName(
                updatedStaffMember.getName()
        );

        if (staffMemberRepository
                .existsByNameIgnoreCaseAndIdNot(
                        name,
                        id
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A user with that name already exists"
            );
        }

        existing.setName(name);
        existing.setActive(
                updatedStaffMember.isActive()
        );
        existing.setActiveOnRota(
                updatedStaffMember.isActiveOnRota()
        );

        if (updatedStaffMember.getRole() != null) {
            existing.setRole(
                    updatedStaffMember.getRole()
            );
        }

        return staffMemberRepository.save(existing);
    }

    // Deactivates the account without destroying historical records.
    public StaffMember deactivateStaffMember(Long id) {
        StaffMember staffMember = getStaffMember(id);

        staffMember.setActive(false);
        staffMember.setActiveOnRota(false);

        return staffMemberRepository.save(staffMember);
    }

    public StaffMember resetPassword(Long id, AuthUser actor) {
        StaffMember target = getStaffMember(id);

        if (target.getRole() == StaffRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin passwords cannot be reset here"
            );
        }

        if (target.getId().equals(actor.id())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use Account Settings to change your own password"
            );
        }

        target.setPasswordHash(
                passwordService.generate(PasswordService.TEMPORARY_PASSWORD)
        );
        target.setMustChangePassword(true);
        return staffMemberRepository.save(target);
    }

    private String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Staff name is required"
            );
        }

        return name.trim();
    }
}
