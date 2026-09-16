package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserDirectoryPage;
import com.ibm.consulting.sim.identity.domain.UserDirectoryQuery;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataUserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByActiveTrueAndRole(UserRole role);
    long countByRoleAndActive(UserRole role, boolean active);
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
    @Override public long countByRoleAndActive(UserRole role, boolean active) { return repo.countByRoleAndActive(role, active); }
    @Override public List<User> findAll() { return repo.findAll(); }
    @Override public void delete(User user) { repo.delete(user); }
    @Override public UserDirectoryPage findDirectory(UserDirectoryQuery query) {
        Page<User> page = repo.findAll(directorySpecification(query),
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new UserDirectoryPage(
                page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }

    private Specification<User> directorySpecification(UserDirectoryQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.search() != null) {
                String pattern = "%" + query.search() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern)));
            }
            if (query.role() != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), query.role()));
            }
            if (query.active() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), query.active()));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
    @Override public List<User> findAllActiveByRole(UserRole role) {
        return repo.findByActiveTrueAndRole(role);
    }
}
