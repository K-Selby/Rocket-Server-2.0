package uk.co.rocketpub.staffportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.rocketpub.staffportal.model.StaffSettings;

public interface StaffSettingsRepository
extends JpaRepository<StaffSettings, Long> {
}
