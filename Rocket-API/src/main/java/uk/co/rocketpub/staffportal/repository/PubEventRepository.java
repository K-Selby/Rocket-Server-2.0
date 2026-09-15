package uk.co.rocketpub.staffportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.rocketpub.staffportal.model.PubEvent;

public interface PubEventRepository extends JpaRepository<PubEvent, Long> {

    List<PubEvent> findByEventDateValue(String eventDate);

    List<PubEvent> findByEventDateValueBetweenOrderByEventDateValueAsc(
            String startDate,
            String endDate
    );
}
