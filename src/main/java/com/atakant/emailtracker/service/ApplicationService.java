package com.atakant.emailtracker.service;


import com.atakant.emailtracker.domain.Application;
import com.atakant.emailtracker.repo.ApplicationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ApplicationService {
    private final ApplicationRepository repository;

    public ApplicationService(ApplicationRepository repository) {
        this.repository = repository;
    }

    public List<Application> getApplicationsForUser(UUID userId) {
        return repository.findByUserIdOrderByLastUpdatedAtDesc(userId);
    }

    public Application create(Application app) {
        return repository.save(app);
    }
}
