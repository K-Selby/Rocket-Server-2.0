package uk.co.rocketpub.staffportal.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.co.rocketpub.staffportal.model.LargeParty;
import uk.co.rocketpub.staffportal.service.LargePartyService;

@RestController
@RequestMapping("/api/large-parties")
@CrossOrigin(origins = "https://rocketpubserver.co.uk")
public class LargePartyController {

    private final LargePartyService service;

    public LargePartyController(LargePartyService service) {
        this.service = service;
    }

    @GetMapping
    public List<LargeParty> getLargeParties(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        return service.getLargePartiesBetween(from, to);
    }
}
