package app.premierleague.repository;

import app.premierleague.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
  List<Match> findAllByOrderByKickoffAsc();
  List<Match> findByStatusOrderByKickoffAsc(String status);
  List<Match> findByHomeTeamIdOrderByKickoffAsc(Integer homeTeamId);

  List<Match> findByHomeTeamIdAndAwayTeamIdAndStatusNotOrderByKickoffAsc(
      Integer homeTeamId, Integer awayTeamId, String statusToExclude);
  List<Match> findByHomeTeamIdOrAwayTeamIdOrderByKickoffAsc(Integer homeTeamId, Integer awayTeamId);

  @Query("""
    select m
    from Match m
    where (m.homeTeamId = :teamId or m.awayTeamId = :teamId)
      and (:status is null or m.status = :status)
    order by m.kickoff asc
  """)
  List<Match> findTeamMatches(@Param("teamId") Integer teamId,
                              @Param("status") String status);
}
