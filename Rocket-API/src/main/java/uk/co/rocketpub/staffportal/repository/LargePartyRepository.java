package uk.co.rocketpub.staffportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.rocketpub.staffportal.model.LargeParty;

public interface LargePartyRepository extends JpaRepository<LargeParty, Long> {

    List<LargeParty> findByEventDateValue(String eventDate);

    List<LargeParty> findByEventDateValueBetweenOrderByEventDateValueAsc(
            String startDate,
            String endDate
    );
}
