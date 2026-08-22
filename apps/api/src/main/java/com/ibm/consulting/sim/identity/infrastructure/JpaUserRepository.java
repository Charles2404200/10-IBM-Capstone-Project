package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataUserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByActiveTrueAndRole(UserRole role);
}

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository repo;

    JpaUserRepository(SpringDataUserRepository repo) {
        this.repo = repo;
    }

    @Override public User save(User user) { return repo.save(user); }
    @Override public Optional<User> findById(UUID id) { return repo.findById(id); }
    @Override public Optional<User> findByEmail(String email) { return repo.findByEmail(email); }
    @Override public boolean existsByEmail(String email) { return repo.existsByEmail(email); }
    @Override public List<User> findAll() { return repo.findAll(); }
    @Override public List<User> findAllActiveByRole(UserRole role) {
        return repo.findByActiveTrueAndRole(role);
    }
}
