package app.premierleague.controller;

import app.premierleague.domain.Match;
import app.premierleague.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

record ResultReq(int homeGoals, int awayGoals) {}

// Tells Spring this class uses HTTP requests
@RestController
@RequestMapping("/matches")
public class ResultController {
  private final MatchService service;
  public ResultController(MatchService service){ this.service = service; }

  // POST request to update the result of a game
  @PostMapping("/{id}/result")
  @ResponseStatus(HttpStatus.OK)
  public Match record(@PathVariable long id, @RequestBody ResultReq body) {
    return service.recordResult(id, body.homeGoals(), body.awayGoals());
  }
}
