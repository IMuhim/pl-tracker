package app.premierleague.repository;

import app.premierleague.domain.Standing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StandingRepository extends JpaRepository<Standing, Integer> {
  List<Standing> findAllByOrderByPointsDescGdDescGfDesc();
}
