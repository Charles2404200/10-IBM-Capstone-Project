package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserDirectoryPage;
import com.ibm.consulting.sim.identity.domain.UserDirectoryQuery;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataUserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("""
            select user from User user
            where (:search is null
                   or lower(user.email) like concat('%', :search, '%')
                   or lower(user.displayName) like concat('%', :search, '%'))
              and (:role is null or user.role = :role)
              and (:active is null or user.active = :active)
            """)
    Page<User> findDirectory(@Param("search") String search,
                             @Param("role") UserRole role,
                             @Param("active") Boolean active,
                             org.springframework.data.domain.Pageable pageable);
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
    @Override public UserDirectoryPage findDirectory(UserDirectoryQuery query) {
        Page<User> page = repo.findDirectory(
                query.search(), query.role(), query.active(),
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new UserDirectoryPage(
                page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }
}
