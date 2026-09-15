package uk.co.rocketpub.staffportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.rocketpub.staffportal.model.StaffDiaryEntry;

public interface StaffDiaryEntryRepository
extends JpaRepository<StaffDiaryEntry, Long> {

    List<StaffDiaryEntry> findByEntryDateValue(String entryDate);

    List<StaffDiaryEntry> findByStaffMemberId(Long staffMemberId);

    List<StaffDiaryEntry> findByEntryDateValueBetweenOrderByIdAsc(
            String startDate,
            String endDate
    );

    List<StaffDiaryEntry> findByStaffMemberIdAndEntryDateValue(
            Long staffMemberId,
            String entryDate
    );

    boolean existsByEntryDateValueAndTypeValue(
            String entryDate,
            String type
    );

    List<StaffDiaryEntry> findByRequestGroupIdOrderByEntryDateValueAsc(
            String requestGroupId
    );

    List<StaffDiaryEntry> findByTypeValueOrderByIdAsc(String type);

    List<StaffDiaryEntry> findByStaffMemberIdAndTypeValueOrderByIdAsc(
            Long staffMemberId,
            String type
    );
}
