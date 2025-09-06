package com.atakant.emailtracker.controller;

import com.atakant.emailtracker.domain.Application;
import com.atakant.emailtracker.repo.ApplicationRepository;
import com.atakant.emailtracker.service.ApplicationService;
import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/applications")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:3000"
})
public class ApplicationController {

  private final ApplicationRepository applications;
  private final UserRepository users;
  private final ApplicationService applicationService;

  public ApplicationController(ApplicationRepository applications, UserRepository users, ApplicationService applicationService) {
    this.applications = applications;
    this.users = users;
    this.applicationService = applicationService;
  }

  /**
   * GET /applications  -> current user's applications
   * Alias GET /applications/all -> same behavior (no admin/global data).
   */
  @GetMapping
  public List<Application> list(@AuthenticationPrincipal OAuth2User principal) {
    User me = requireUser(principal);
    return applications.findByUserIdOrderByLastUpdatedAtDesc(me.getId());
  }

  @GetMapping("/all")
  public List<Application> listAll(@AuthenticationPrincipal OAuth2User principal) {
    // Alias to the same user-scoped list
    return list(principal);
  }

  /**
   * POST /applications  -> create/update for the current user.
   * Any incoming userId is ignored; we force ownership to the caller.
   */
  @PostMapping
  public Application create(
          @AuthenticationPrincipal OAuth2User principal,
          @RequestBody Application body
  ) {
    User me = requireUser(principal);

    // Force ownership to authenticated user
    body.setUserId(me.getId());

    // Ensure id and timestamps
    if (body.getId() == null) {
      body.setId(UUID.randomUUID());
    }
    body.setLastUpdatedAt(OffsetDateTime.now());

    return applications.save(body);
  }

  /**
   * DELETE /applications  -> delete all for the current user.
   */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT) // 204
  public void deleteAll(@AuthenticationPrincipal OAuth2User principal) {
    User me = requireUser(principal);
    applicationService.deleteAllForUser(me.getId());
  }

  private User requireUser(OAuth2User principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String email = principal.getAttribute("email");
    if (email == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No email from OAuth provider");
    }
    return users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not registered"));
  }
}
