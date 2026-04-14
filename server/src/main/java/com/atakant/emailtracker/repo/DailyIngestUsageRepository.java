package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.DailyIngestUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyIngestUsageRepository extends JpaRepository<DailyIngestUsage, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
        from DailyIngestUsage d
        where d.userId = :userId
          and d.usageDate = :usageDate
        """)
    Optional<DailyIngestUsage> findForUpdate(@Param("userId") UUID userId,
                                             @Param("usageDate") LocalDate usageDate);
}
