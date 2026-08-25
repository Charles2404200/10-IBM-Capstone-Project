package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class User extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean emailVerified = true;

    private Instant emailVerifiedAt;

    protected User() {}

    public static User create(String email, String passwordHash, String displayName, UserRole role) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.displayName = displayName;
        user.role = role;
        return user;
    }

    /** Creates a learner account which cannot authenticate until its inbox is verified. */
    public static User createUnverified(String email, String passwordHash, String displayName) {
        User user = create(email, passwordHash, displayName, UserRole.LEARNER);
        user.emailVerified = false;
        user.emailVerifiedAt = null;
        return user;
    }

    public void deactivate() { this.active = false; }
    public void reactivate() { this.active = true; }
    public void changeRole(UserRole newRole) { this.role = newRole; }
    public void verifyEmail(Instant verifiedAt) {
        this.emailVerified = true;
        this.emailVerifiedAt = verifiedAt;
    }
    public void changePasswordHash(String newPasswordHash) { this.passwordHash = newPasswordHash; }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
}
