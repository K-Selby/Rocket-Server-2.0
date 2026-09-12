package uk.co.rocketpub.staffportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.rocketpub.staffportal.model.RotaShift;

import java.util.List;

public interface RotaShiftRepository extends JpaRepository<RotaShift, Long> {

    List<RotaShift> findByRotaWeekWeekStart(String weekStart);
}
