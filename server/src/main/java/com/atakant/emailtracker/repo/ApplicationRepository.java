package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


public interface ApplicationRepository extends JpaRepository<Application, UUID> {
  List<Application> findByUserIdOrderByLastUpdatedAtDesc(UUID userId);

  void deleteByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query(value = """
INSERT INTO applications (
  user_id, normalized_company, normalized_role_title,
  canonical_key,          -- insert-once; never touched on updates
  company, role_title,    -- insert-once; never touched on updates
  location,               -- insert-once; never touched on updates
  status,                 -- may change over time
  first_seen_at, last_updated_at
) VALUES (
  :userId, :nc, :nr,
  :ck,
  :company, :role,
  :location,
  :status,
  NOW(), NOW()
)
ON CONFLICT (user_id, normalized_company, normalized_role_title)
DO UPDATE SET
  status          = EXCLUDED.status,
  last_updated_at = NOW()
""", nativeQuery = true)
    int upsert(@Param("userId") java.util.UUID userId,
               @Param("nc") String normalizedCompany,
               @Param("nr") String normalizedRoleTitle,
               @Param("ck") String canonicalKey,   // userId|nc|nr
               @Param("company") String company,   // display, insert-only
               @Param("role") String roleTitle,    // display, insert-only
               @Param("location") String location, // insert-only
               @Param("status") String status);


}
