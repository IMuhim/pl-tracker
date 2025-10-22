package app.premierleague.controller;

import app.premierleague.domain.Match;
import app.premierleague.repository.MatchRepository;
import app.premierleague.repository.TeamRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
public class MatchController {

  private final MatchRepository matchRepo;
  private final TeamRepository teamRepo;

  public MatchController(MatchRepository matchRepo, TeamRepository teamRepo) {
    this.matchRepo = matchRepo;
    this.teamRepo = teamRepo;
  }

  @GetMapping("/matches")
  public List<Match> list(@RequestParam(required = false) String status) {
    if (status == null || status.isBlank()) return matchRepo.findAllByOrderByKickoffAsc();
    return matchRepo.findByStatusOrderByKickoffAsc(status);
  }

  @GetMapping("/fixtures")
  public List<Match> fixtures() {
    return matchRepo.findByStatusOrderByKickoffAsc("SCHEDULED");
  }

  @GetMapping("/teams/{teamId}/fixtures")
  public List<Match> fixturesForTeam(@PathVariable Integer teamId,
                                     @RequestParam(required = false) String status) {
    return matchRepo.findTeamMatches(teamId, status);
  }

  // 1) Create a fixture (no DTO)
  @PostMapping("/matches")
  public ResponseEntity<Match> create(@RequestBody Match match) {
    if (Objects.equals(match.getHomeTeamId(), match.getAwayTeamId())) {
      throw new IllegalArgumentException("home and away must differ");
    }
    teamRepo.findById(match.getHomeTeamId()).orElseThrow();
    teamRepo.findById(match.getAwayTeamId()).orElseThrow();

    match.setHomeGoals(0);
    match.setAwayGoals(0);
    match.setStatus("SCHEDULED");
    return ResponseEntity.ok(matchRepo.save(match));
  }

  // 2) Submit the result (set scores and mark FT)
  @PatchMapping("/matches/{id}/result")
  public ResponseEntity<Match> submitResult(@PathVariable Long id,
                                            @RequestBody Match body) {
    var m = matchRepo.findById(id).orElseThrow();
    if (body.getHomeGoals() == null || body.getAwayGoals() == null) {
      throw new IllegalArgumentException("homeGoals and awayGoals are required");
    }
    if (body.getHomeGoals() < 0 || body.getAwayGoals() < 0) {
      throw new IllegalArgumentException("scores must be non-negative");
    }
    m.setHomeGoals(body.getHomeGoals());
    m.setAwayGoals(body.getAwayGoals());
    m.setStatus("FT"); // finished
    return ResponseEntity.ok(matchRepo.save(m));
  }
}
