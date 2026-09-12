package uk.co.rocketpub.staffportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import uk.co.rocketpub.staffportal.model.StaffMember;
import uk.co.rocketpub.staffportal.model.StaffRole;

public interface StaffMemberRepository
        extends JpaRepository<StaffMember, Long> {

    List<StaffMember> findByActiveTrueOrderByNameAsc();

    // Admin never appears in rota, diary or shift selectors.
    List<StaffMember> findByActiveTrueAndRoleNotOrderByNameAsc(
            StaffRole role);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(
            String name,
            Long id);

    /*
     * Login accepts the username or a verified email address.
     * Disabled accounts are deliberately excluded.
     */
    @Query("""
            select user
            from StaffMember user
            where user.active = true
              and (
                    lower(user.name) = lower(:login)
                    or (
                        user.emailVerified = true
                        and user.email is not null
                        and lower(user.email) = lower(:login)
                    )
                  )
            """)
    Optional<StaffMember> findActiveLogin(
            @Param("login") String login);

    @Query("""
            select user
            from StaffMember user
            where user.active = true
              and user.emailVerified = true
              and lower(user.email) = lower(:email)
            """)
    Optional<StaffMember> findActiveVerifiedEmail(
            @Param("email") String email);

    @Query("""
            select (count(user) > 0)
            from StaffMember user
            where user.id <> :userId
              and (
                    lower(user.email) = lower(:email)
                    or lower(user.pendingEmail) = lower(:email)
                  )
            """)
    boolean emailUsedByAnotherUser(
            @Param("email") String email,
            @Param("userId") Long userId);

}
