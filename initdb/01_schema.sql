DROP TABLE IF EXISTS matches CASCADE;
DROP TABLE IF EXISTS teams CASCADE;
DROP TABLE IF EXISTS players CASCADE;
DROP TABLE IF EXISTS events CASCADE;

CREATE TABLE teams (
  id SERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL UNIQUE,
  short_name  VARCHAR(10)  NOT NULL UNIQUE,
  city        VARCHAR(100),
  created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE matches (
  id SERIAL PRIMARY KEY,
  home_team_id INT NOT NULL REFERENCES teams(id),
  away_team_id INT NOT NULL REFERENCES teams(id),
  kickoff      TIMESTAMP NOT NULL,
  venue        VARCHAR(120),
  home_goals   INT NOT NULL DEFAULT 0,
  away_goals   INT NOT NULL DEFAULT 0,
  status       VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
               CHECK (status IN ('SCHEDULED','LIVE','FT','POSTPONED','CANCELLED')),
  created_at   TIMESTAMP DEFAULT NOW(),

  CONSTRAINT chk_home_away_different CHECK (home_team_id <> away_team_id),

  CONSTRAINT chk_non_negative_scores CHECK (home_goals >= 0 AND away_goals >= 0)
);

CREATE INDEX idx_matches_kickoff ON matches(kickoff);
CREATE INDEX idx_matches_status  ON matches(status);
CREATE INDEX idx_matches_teams   ON matches(home_team_id, away_team_id);

CREATE UNIQUE INDEX ux_match_unique_pair_time
  ON matches(home_team_id, away_team_id, kickoff);
