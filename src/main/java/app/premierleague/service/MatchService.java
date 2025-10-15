@Transactional
public void recomputeStandings() {
  // Clear current table
  jdbc.update("DELETE FROM standings");

  // One-shot insert: aggregate both sides, then left-join to teams to ensure 20 rows
  String sql = """
    WITH home AS (
      SELECT home_team_id AS team_id,
             COUNT(*)                                  AS played,
             SUM(CASE WHEN home_goals > away_goals THEN 1 ELSE 0 END) AS won,
             SUM(CASE WHEN home_goals = away_goals THEN 1 ELSE 0 END) AS drawn,
             SUM(CASE WHEN home_goals < away_goals THEN 1 ELSE 0 END) AS lost,
             SUM(home_goals)                           AS gf,
             SUM(away_goals)                           AS ga
      FROM matches
      WHERE status = 'FT'
      GROUP BY home_team_id
    ),
    away AS (
      SELECT away_team_id AS team_id,
             COUNT(*)                                  AS played,
             SUM(CASE WHEN away_goals > home_goals THEN 1 ELSE 0 END) AS won,
             SUM(CASE WHEN away_goals = home_goals THEN 1 ELSE 0 END) AS drawn,
             SUM(CASE WHEN away_goals < home_goals THEN 1 ELSE 0 END) AS lost,
             SUM(away_goals)                           AS gf,
             SUM(home_goals)                           AS ga
      FROM matches
      WHERE status = 'FT'
      GROUP BY away_team_id
    ),
    agg AS (
      SELECT team_id,
             SUM(played) AS played,
             SUM(won)    AS won,
             SUM(drawn)  AS drawn,
             SUM(lost)   AS lost,
             SUM(gf)     AS gf,
             SUM(ga)     AS ga
      FROM (
        SELECT * FROM home
        UNION ALL
        SELECT * FROM away
      ) x
      GROUP BY team_id
    )
    INSERT INTO standings (team_id, played, won, drawn, lost, gf, ga, gd, points, last_updated)
    SELECT t.id AS team_id,
           COALESCE(a.played, 0)                        AS played,
           COALESCE(a.won,    0)                        AS won,
           COALESCE(a.drawn,  0)                        AS drawn,
           COALESCE(a.lost,   0)                        AS lost,
           COALESCE(a.gf,     0)                        AS gf,
           COALESCE(a.ga,     0)                        AS ga,
           COALESCE(a.gf,0) - COALESCE(a.ga,0)          AS gd,
           COALESCE(a.won,0)*3 + COALESCE(a.drawn,0)    AS points,
           NOW()
    FROM teams t
    LEFT JOIN agg a ON a.team_id = t.id
    ORDER BY points DESC, (COALESCE(a.gf,0)-COALESCE(a.ga,0)) DESC, COALESCE(a.gf,0) DESC
    """;

  jdbc.update(sql);
}
