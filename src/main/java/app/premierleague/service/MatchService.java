package app.premierleague.service;

import app.premierleague.domain.Match;
import app.premierleague.repository.MatchRepository;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MatchService {
  private final MatchRepository matchRepo;
  private final JdbcTemplate jdbc;

  public MatchService(MatchRepository matchRepo, JdbcTemplate jdbc) {
    this.matchRepo = matchRepo; this.jdbc = jdbc;
  }

  @Transactional
  public Match recordResult(long matchId, int homeGoals, int awayGoals) {
    if (homeGoals < 0 || awayGoals < 0) throw new IllegalArgumentException("Scores must be ≥ 0");

    Match m = matchRepo.findById(matchId)
        .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    if ("FT".equals(m.getStatus())) {
    }
    m.setHomeGoals(homeGoals);
    m.setAwayGoals(awayGoals);
    m.setStatus("FT");
    matchRepo.save(m);

    recomputeStandings();
    return m;
  }

  @Transactional
  public void recomputeStandings() {
    jdbc.update("TRUNCATE TABLE standings");

    String home = """
      SELECT home_team_id AS team_id,
             COUNT(*) played,
             SUM(CASE WHEN home_goals>away_goals THEN 1 ELSE 0 END) won,
             SUM(CASE WHEN home_goals=away_goals THEN 1 ELSE 0 END) drawn,
             SUM(CASE WHEN home_goals<away_goals THEN 1 ELSE 0 END) lost,
             SUM(home_goals) gf, SUM(away_goals) ga
      FROM matches WHERE status='FT' GROUP BY home_team_id
    """;
    String away = """
      SELECT away_team_id AS team_id,
             COUNT(*) played,
             SUM(CASE WHEN away_goals>home_goals THEN 1 ELSE 0 END) won,
             SUM(CASE WHEN away_goals=home_goals THEN 1 ELSE 0 END) drawn,
             SUM(CASE WHEN away_goals<home_goals THEN 1 ELSE 0 END) lost,
             SUM(away_goals) gf, SUM(home_goals) ga
      FROM matches WHERE status='FT' GROUP BY away_team_id
    """;
    String upsert = """
      INSERT INTO standings (team_id, played, won, drawn, lost, gf, ga, gd, points, last_updated)
      SELECT team_id,
             SUM(played), SUM(won), SUM(drawn), SUM(lost),
             SUM(gf), SUM(ga),
             SUM(gf)-SUM(ga) AS gd,
             SUM(won)*3 + SUM(drawn) AS points,
             NOW()
      FROM ( %s UNION ALL %s ) t GROUP BY team_id
      ON CONFLICT (team_id) DO UPDATE SET
        played=EXCLUDED.played, won=EXCLUDED.won, drawn=EXCLUDED.drawn,
        lost=EXCLUDED.lost, gf=EXCLUDED.gf, ga=EXCLUDED.ga,
        gd=EXCLUDED.gd, points=EXCLUDED.points, last_updated=EXCLUDED.last_updated
    """.formatted(home, away);
    jdbc.update(upsert);

    // ensure teams with 0 games still show
    jdbc.update("""
      INSERT INTO standings (team_id) 
      SELECT id FROM teams t
      WHERE NOT EXISTS (SELECT 1 FROM standings s WHERE s.team_id=t.id)
      ON CONFLICT DO NOTHING
    """);
  }
}
