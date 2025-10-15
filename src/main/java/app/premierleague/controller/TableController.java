package app.premierleague.controller;

import app.premierleague.domain.Standing;
import app.premierleague.repository.StandingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class TableController {
  private final StandingRepository repo;
  public TableController(StandingRepository repo){ this.repo = repo; }

  @GetMapping("/table")
  public List<Standing> table() {
    return repo.findAllByOrderByPointsDescGdDescGfDesc();
  }
}
