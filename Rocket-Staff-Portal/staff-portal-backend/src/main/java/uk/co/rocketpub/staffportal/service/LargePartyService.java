package uk.co.rocketpub.staffportal.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import uk.co.rocketpub.staffportal.model.LargeParty;
import uk.co.rocketpub.staffportal.repository.LargePartyRepository;

@Service
public class LargePartyService {

    private final LargePartyRepository repository;

    public LargePartyService(LargePartyRepository repository) {
        this.repository = repository;
    }

    // Large parties are owned by the Booking Portal and read here for the Diary.
    public List<LargeParty> getLargePartiesBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid large-party date range");
        }

        return repository
                .findByEventDateValueBetweenOrderByEventDateValueAsc(
                        from.toString(),
                        to.toString()
                )
                .stream()
                .filter(party -> party.getStatus() == null || !party.getStatus().equalsIgnoreCase("Cancelled"))
                .toList();
    }
}
