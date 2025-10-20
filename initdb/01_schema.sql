DROP TABLE IF EXISTS standings CASCADE
@@
DROP TABLE IF EXISTS events CASCADE
@@
DROP TABLE IF EXISTS matches CASCADE
@@
DROP TABLE IF EXISTS teams CASCADE
@@

CREATE EXTENSION IF NOT EXISTS btree_gist
@@

CREATE TABLE teams (
  id SERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL UNIQUE,
  short_name  VARCHAR(10)  NOT NULL UNIQUE,
  city        VARCHAR(100),
  created_at  TIMESTAMPTZ DEFAULT NOW()
)
@@

CREATE TABLE matches (
  id SERIAL PRIMARY KEY,
  home_team_id INT NOT NULL REFERENCES teams(id),
  away_team_id INT NOT NULL REFERENCES teams(id),
  kickoff      TIMESTAMPTZ NOT NULL,
  venue        VARCHAR(120),
  home_goals   INT NOT NULL DEFAULT 0,
  away_goals   INT NOT NULL DEFAULT 0,
  status       VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
               CHECK (status IN ('SCHEDULED','LIVE','FT','POSTPONED','CANCELLED')),
  created_at   TIMESTAMPTZ DEFAULT NOW(),

  CONSTRAINT chk_home_away_different CHECK (home_team_id <> away_team_id),
  CONSTRAINT chk_non_negative_scores CHECK (home_goals >= 0 AND away_goals >= 0),

  CONSTRAINT ux_match_unique_pair_time UNIQUE (home_team_id, away_team_id, kickoff)
)
@@

CREATE INDEX idx_matches_kickoff ON matches(kickoff)
@@
CREATE INDEX idx_matches_status  ON matches(status)
@@
CREATE INDEX idx_matches_teams   ON matches(home_team_id, away_team_id)
@@

ALTER TABLE matches
  ADD CONSTRAINT ex_home_double_book
  EXCLUDE USING gist (home_team_id WITH =, kickoff WITH =)
@@
ALTER TABLE matches
  ADD CONSTRAINT ex_away_double_book
  EXCLUDE USING gist (away_team_id WITH =, kickoff WITH =)
@@

CREATE TABLE standings (
  team_id INT PRIMARY KEY REFERENCES teams(id) ON DELETE CASCADE,
  played  INT NOT NULL DEFAULT 0,
  won     INT NOT NULL DEFAULT 0,
  drawn   INT NOT NULL DEFAULT 0,
  lost    INT NOT NULL DEFAULT 0,
  gf      INT NOT NULL DEFAULT 0,
  ga      INT NOT NULL DEFAULT 0,
  gd      INT NOT NULL DEFAULT 0,
  points  INT NOT NULL DEFAULT 0,
  last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
)
@@
