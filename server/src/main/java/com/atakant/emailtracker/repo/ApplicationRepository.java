package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.OffsetDateTime;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
  List<Application> findByUserIdOrderByLastUpdatedAtDesc(UUID userId);

  Optional<Application> findByUserIdAndNormalizedCompanyAndNormalizedRoleTitle(
          UUID userId, String normalizedCompany, String normalizedRoleTitle);



}
