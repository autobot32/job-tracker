package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
  List<Application> findByUserIdOrderByLastUpdatedAtDesc(UUID userId);
}
