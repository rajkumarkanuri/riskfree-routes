package com.riskfreeroutes.backend.repository;

import com.riskfreeroutes.backend.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * UserRepository — Spring Data JPA gives us free CRUD methods just by extending JpaRepository.
 * We add custom queries here for looking up users by Firebase UID or email.
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByFirebaseUid(String firebaseUid);
    Optional<AppUser> findByEmail(String email);
    boolean existsByFirebaseUid(String firebaseUid);
}
