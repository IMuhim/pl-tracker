package app.premierleague.controller;

import app.premierleague.domain.Team;
import app.premierleague.repository.TeamRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/teams")
public class TeamController {
  private final TeamRepository repo;

  public TeamController(TeamRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  public List<Team> all() {
    return repo.findAll();
  }

  @PatchMapping("/{id}/owner")
  public ResponseEntity<Team> setOwner(@PathVariable long id, @RequestBody Map<String,String> body) {
    var team = repo.findById((int) id)
        .orElseThrow(() -> new IllegalArgumentException("Team not found: " + id));
    team.setOwner(Objects.requireNonNull(body.get("owner"), "owner is required"));
    return ResponseEntity.ok(repo.save(team));
  }
}
