package com.atakant.emailtracker.controller;

import com.atakant.emailtracker.domain.Application;
import com.atakant.emailtracker.repo.ApplicationRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/applications")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:3000"
})
public class ApplicationController {
  private final ApplicationRepository repo;
  public ApplicationController(ApplicationRepository repo) { this.repo = repo; }

  @GetMapping
  public List<Application> list(@RequestParam UUID userId) {
    return repo.findByUserIdOrderByLastUpdatedAtDesc(userId);
  }

  @PostMapping
  public Application create(@RequestBody Application a) {
    if (a.getId() == null) a.setId(UUID.randomUUID());
    a.setLastUpdatedAt(java.time.OffsetDateTime.now());
    return repo.save(a);
  }

  @GetMapping("/all")
  public List<Application> listAll() {
    return repo.findAll();
  }

}
