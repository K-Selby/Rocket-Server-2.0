package uk.co.rocketpub.staffportal.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class StaffMember {

    private static final DateTimeFormatter SQLITE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Rocket username is also the person's displayed staff name.
    @Column(name = "username", nullable = false, unique = true, length = 80)
    private String name;

    // Password hashes are never returned through the API.
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email")
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @JsonIgnore
    @Column(name = "pending_email")
    private String pendingEmail;

    @JsonIgnore
    @Column(name = "email_verification_code_hash")
    private String emailVerificationCodeHash;

    @JsonIgnore
    @Column(name = "email_verification_expires_at")
    private String emailVerificationExpiresAtValue;

    // Controls whether the account can sign in.
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // Controls whether the person participates in the rota.
    @Column(name = "active_on_rota", nullable = false)
    private boolean activeOnRota = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    // SQLite stores timestamps as text rather than Hibernate temporal values.
    @Column(name = "last_login_at")
    private String lastLoginAtValue;

    // Java uses STAFF/MANAGER/ADMIN while SQLite stores lowercase values.
    @Convert(converter = StaffRoleConverter.class)
    @Column(name = "role", nullable = false, length = 20)
    private StaffRole role = StaffRole.STAFF;

    public StaffMember() {
    }

    public StaffMember(String name, boolean active) {
        this(name, active, StaffRole.STAFF);
    }

    public StaffMember(String name, boolean active, StaffRole role) {
        this.name = name;
        this.active = active;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String pendingEmail) {
        this.pendingEmail = pendingEmail;
    }

    public String getEmailVerificationCodeHash() {
        return emailVerificationCodeHash;
    }

    public void setEmailVerificationCodeHash(String emailVerificationCodeHash) {
        this.emailVerificationCodeHash = emailVerificationCodeHash;
    }

    public LocalDateTime getEmailVerificationExpiresAt() {
        return parseDateTime(emailVerificationExpiresAtValue);
    }

    public void setEmailVerificationExpiresAt(LocalDateTime expiresAt) {
        this.emailVerificationExpiresAtValue = formatDateTime(expiresAt);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActiveOnRota() {
        return activeOnRota;
    }

    public void setActiveOnRota(boolean activeOnRota) {
        this.activeOnRota = activeOnRota;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public LocalDateTime getLastLoginAt() {
        return parseDateTime(lastLoginAtValue);
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAtValue = formatDateTime(lastLoginAt);
    }

    public StaffRole getRole() {
        return role;
    }

    public void setRole(StaffRole role) {
        this.role = role;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;

        return LocalDateTime.parse(value.trim().replace(' ', 'T'));
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(SQLITE_DATE_TIME);
    }
}
