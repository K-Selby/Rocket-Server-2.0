package uk.co.rocketpub.staffportal.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.model.PubEvent;
import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;
import uk.co.rocketpub.staffportal.repository.PubEventRepository;
import uk.co.rocketpub.staffportal.repository.StaffMemberRepository;

@Service
public class PubEventService {

    private final PubEventRepository repository;
    private final StaffMemberRepository staffRepository;

    public PubEventService(
            PubEventRepository repository,
            StaffMemberRepository staffRepository) {

        this.repository = repository;
        this.staffRepository = staffRepository;
    }

    // Returns events within the selected Diary range.
    public List<PubEvent> getEventsBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid event date range");
        }

        return repository.findByEventDateValueBetweenOrderByEventDateValueAsc(
                from.toString(),
                to.toString()
        );
    }

    // Managers and Admin can create pub events.
    public PubEvent createEvent(Long currentUserId, PubEvent event) {
        StaffMember currentUser = requireManagerOrAdmin(currentUserId);

        validateEvent(event);
        cleanEvent(event);

        event.setCreatedBy(currentUser);
        event.setCreatedAt(LocalDateTime.now().withNano(0).toString());

        return repository.save(event);
    }

    // Managers and Admin can edit existing events.
    public PubEvent updateEvent(Long currentUserId, Long id, PubEvent updatedEvent) {
        requireManagerOrAdmin(currentUserId);
        validateEvent(updatedEvent);

        PubEvent event = findEvent(id);

        event.setEventDate(updatedEvent.getEventDate());
        event.setEventTime(updatedEvent.getEventTime());
        event.setTitle(updatedEvent.getTitle().trim());
        event.setEventType(updatedEvent.getEventType());
        event.setNote(cleanText(updatedEvent.getNote()));

        return repository.save(event);
    }

    // Managers and Admin can remove events.
    public void deleteEvent(Long currentUserId, Long id) {
        requireManagerOrAdmin(currentUserId);
        repository.delete(findEvent(id));
    }

    private void validateEvent(PubEvent event) {
        if (event == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Event is required");
        }

        if (event.getEventDate() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Event date is required");
        }

        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Event title is required");
        }

        if (event.getEventType() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Event type is required");
        }
    }

    private void cleanEvent(PubEvent event) {
        event.setTitle(event.getTitle().trim());
        event.setNote(cleanText(event.getNote()));
    }

    private String cleanText(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        return value.trim();
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

    private PubEvent findEvent(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pub event not found"));
    }
}
