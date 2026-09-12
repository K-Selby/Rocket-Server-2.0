package uk.co.rocketpub.staffportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.rocketpub.staffportal.model.ShiftSwapRequest;

public interface ShiftSwapRequestRepository
extends JpaRepository<ShiftSwapRequest, Long> {

    List<ShiftSwapRequest> findByTargetStaffMemberIdOrderByIdDesc(
            Long staffMemberId
    );

    List<ShiftSwapRequest> findByRequesterIdOrderByIdDesc(
            Long staffMemberId
    );

    List<ShiftSwapRequest> findByTargetStaffMemberIdAndStatusValueOrderByIdDesc(
            Long staffMemberId,
            String status
    );

    List<ShiftSwapRequest> findByStatusValueOrderByIdAsc(
            String status
    );
}
