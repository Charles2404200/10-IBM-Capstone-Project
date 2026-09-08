package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataUserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
    boolean existsByEmail(String email);
}

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository repo;

    JpaUserRepository(SpringDataUserRepository repo) {
        this.repo = repo;
    }

    @Override public User save(User user) { return repo.save(user); }
    @Override public User saveAndFlush(User user) { return repo.saveAndFlush(user); }
    @Override public Optional<User> findById(UUID id) { return repo.findById(id); }
    @Override public Optional<User> findByIdForUpdate(UUID id) { return repo.findByIdForUpdate(id); }
    @Override public Optional<User> findByEmail(String email) { return repo.findByEmail(email); }
    @Override public Optional<User> findByEmailForUpdate(String email) { return repo.findByEmailForUpdate(email); }
    @Override public boolean existsByEmail(String email) { return repo.existsByEmail(email); }
    @Override public List<User> findAll() { return repo.findAll(); }
}
