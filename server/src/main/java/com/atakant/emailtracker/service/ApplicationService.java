package com.atakant.emailtracker.service;

import com.atakant.emailtracker.repo.ApplicationRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applications;

    @Transactional
    public void deleteAllForUser(UUID userId) {
        applications.deleteByUserId(userId);
    }
}

