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

import uk.co.rocketpub.staffportal.model.PubEvent;
import uk.co.rocketpub.staffportal.service.PubEventService;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "http://localhost:3000")
public class PubEventController {

    private final PubEventService service;

    public PubEventController(PubEventService service) {
        this.service = service;
    }

    @GetMapping
    public List<PubEvent> getEvents(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        return service.getEventsBetween(from, to);
    }

    // currentUserId remains temporary until all APIs use the session user.
    @PostMapping
    public PubEvent createEvent(
            @RequestParam Long currentUserId,
            @RequestBody PubEvent event) {

        return service.createEvent(currentUserId, event);
    }

    @PutMapping("/{id}")
    public PubEvent updateEvent(
            @RequestParam Long currentUserId,
            @PathVariable Long id,
            @RequestBody PubEvent event) {

        return service.updateEvent(currentUserId, id, event);
    }

    @DeleteMapping("/{id}")
    public void deleteEvent(
            @RequestParam Long currentUserId,
            @PathVariable Long id) {

        service.deleteEvent(currentUserId, id);
    }
}
