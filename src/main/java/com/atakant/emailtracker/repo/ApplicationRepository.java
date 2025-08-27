package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.OffsetDateTime;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
  List<Application> findByUserIdOrderByLastUpdatedAtDesc(UUID userId);

  // Potential use ?
  List<Application> findByUserIdAndNextDueAtBetweenOrderByNextDueAtAsc(
          UUID userId,
          OffsetDateTime start,
          OffsetDateTime end
  );

  Optional<Application> findFirstByUserIdAndCompanyIgnoreCaseAndRoleTitleIgnoreCase(
          UUID userId, String company, String roleTitle);

  Optional<Application> findByUserIdAndNormalizedCompanyAndNormalizedRoleTitle(
          UUID userId, String normalizedCompany, String normalizedRoleTitle);

}
