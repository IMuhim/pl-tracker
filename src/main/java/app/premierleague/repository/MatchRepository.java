package app.premierleague.repository;

import app.premierleague.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
  List<Match> findAllByOrderByKickoffAsc();
  List<Match> findByStatusOrderByKickoffAsc(String status);
}
