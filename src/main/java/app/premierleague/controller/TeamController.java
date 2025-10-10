package app.premierleague.controller;

import app.premierleague.domain.Team;
import app.premierleague.repository.TeamRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class TeamController {
  private final TeamRepository repo;
  public TeamController(TeamRepository repo) { this.repo = repo; }

  @GetMapping("/teams")
  public List<Team> all() {
    return repo.findAll();
  }
}
