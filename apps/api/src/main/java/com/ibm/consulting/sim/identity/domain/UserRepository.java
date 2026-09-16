package com.ibm.consulting.sim.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    User saveAndFlush(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByIdForUpdate(UUID id);
    Optional<User> findByEmail(String email);
    /** Serializes security-credential issuance scoped to one account. */
    Optional<User> findByEmailForUpdate(String email);
    boolean existsByEmail(String email);
    long countByRoleAndActive(UserRole role, boolean active);
    List<User> findAll();
    UserDirectoryPage findDirectory(UserDirectoryQuery query);
    void delete(User user);
}
