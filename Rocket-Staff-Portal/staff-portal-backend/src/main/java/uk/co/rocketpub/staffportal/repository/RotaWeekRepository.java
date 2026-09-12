package uk.co.rocketpub.staffportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.rocketpub.staffportal.model.RotaWeek;

import java.util.List;

public interface RotaWeekRepository extends JpaRepository<RotaWeek, String> {

    List<RotaWeek> findByPublishedTrueOrderByWeekStartAsc();

    List<RotaWeek> findAllByOrderByWeekStartAsc();
}
