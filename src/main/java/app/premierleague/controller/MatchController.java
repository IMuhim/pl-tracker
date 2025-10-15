package app.premierleague.controller;

import app.premierleague.domain.Match;
import app.premierleague.repository.MatchRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MatchController {
  private final MatchRepository repo;
  public MatchController(MatchRepository repo) { this.repo = repo; }

  @GetMapping("/matches")
  public List<Match> list(@RequestParam(required = false) String status) {
    if (status == null || status.isBlank()) return repo.findAllByOrderByKickoffAsc();
    return repo.findByStatusOrderByKickoffAsc(status);
  }

  @GetMapping("/fixtures")
  public List<Match> fixtures() {
    return repo.findByStatusOrderByKickoffAsc("SCHEDULED");
  }
}
