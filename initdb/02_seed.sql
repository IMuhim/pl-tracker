INSERT INTO teams (name, short_name, city) VALUES
 ('Arsenal',                 'ARS', 'London'),
 ('Aston Villa',             'AVL', 'Birmingham'),
 ('Bournemouth',             'BOU', 'Bournemouth'),
 ('Brentford',               'BRE', 'London'),
 ('Brighton & Hove Albion',  'BHA', 'Brighton'),
 ('Chelsea',                 'CHE', 'London'),
 ('Crystal Palace',          'CRY', 'London'),
 ('Everton',                 'EVE', 'Liverpool'),
 ('Fulham',                  'FUL', 'London'),
 ('Ipswich Town',            'IPS', 'Ipswich'),
 ('Leicester City',          'LEI', 'Leicester'),
 ('Liverpool',               'LIV', 'Liverpool'),
 ('Manchester City',         'MCI', 'Manchester'),
 ('Manchester United',       'MUN', 'Manchester'),
 ('Newcastle United',        'NEW', 'Newcastle'),
 ('Nottingham Forest',       'NFO', 'Nottingham'),
 ('Southampton',             'SOU', 'Southampton'),
 ('Tottenham Hotspur',       'TOT', 'London'),
 ('West Ham United',         'WHU', 'London'),
 ('Wolverhampton Wanderers', 'WOL', 'Wolverhampton')
ON CONFLICT DO NOTHING
@@

TRUNCATE TABLE matches RESTART IDENTITY
@@
SET TIME ZONE 'UTC'
@@

DO $do$
DECLARE
  team_ids int[];
  a int[];
  n int;
  half int;
  r int;
  i int;

  season_start timestamptz := timestamptz '2025-08-09 12:30:00+00';
  mw_start  timestamptz;
  mw_start2 timestamptz;

  slot_day    int[] := ARRAY[0,0,0,0,0,0, 1,1,1, 2];
  slot_hour   int[] := ARRAY[12,15,15,15,15,17, 12,14,16, 20];
  slot_minute int[] := ARRAY[30, 0, 0, 0, 0,30,  0,30,30,  0];
  slot_idx int;

  home_id int;
  away_id int;

BEGIN
  SELECT array_agg(id ORDER BY short_name) INTO team_ids FROM teams;
  n := COALESCE(array_length(team_ids, 1), 0);
  IF n <> 20 THEN
    RAISE EXCEPTION 'Expected 20 teams, found %', n;
  END IF;

  half := n / 2;
  a := team_ids;

  -- FIRST HALF
  FOR r IN 0..(n-2) LOOP
    slot_idx := 1;
    mw_start := season_start + (r * interval '7 days');

    FOR i IN 1..half LOOP
      home_id := a[i];
      away_id := a[n - i + 1];

      INSERT INTO matches (home_team_id, away_team_id, kickoff, venue, status)
      VALUES (
        home_id,
        away_id,
        (date_trunc('day', mw_start)
           + make_interval(days => slot_day[slot_idx],
                           hours => slot_hour[slot_idx],
                           mins  => slot_minute[slot_idx])),
        'TBC',
        'SCHEDULED'
      )
      ON CONFLICT ON CONSTRAINT ux_match_unique_pair_time DO NOTHING;

      slot_idx := slot_idx + 1;
    END LOOP;

    a := ARRAY[a[1], a[n]] || a[2:n-1];
  END LOOP;

  a := team_ids;

  FOR r IN 0..(n-2) LOOP
    slot_idx := 1;
    mw_start2 := season_start + ((r + (n - 1)) * interval '7 days');

    FOR i IN 1..half LOOP
      home_id := a[n - i + 1];
      away_id := a[i];

      INSERT INTO matches (home_team_id, away_team_id, kickoff, venue, status)
      VALUES (
        home_id,
        away_id,
        (date_trunc('day', mw_start2)
           + make_interval(days => slot_day[slot_idx],
                           hours => slot_hour[slot_idx],
                           mins  => slot_minute[slot_idx])),
        'TBC',
        'SCHEDULED'
      )
      ON CONFLICT ON CONSTRAINT ux_match_unique_pair_time DO NOTHING;

      slot_idx := slot_idx + 1;
    END LOOP;

    a := ARRAY[a[1], a[n]] || a[2:n-1];
  END LOOP;

  RAISE NOTICE 'Fixtures now in matches: %', (SELECT COUNT(*) FROM matches);
END
$do$ LANGUAGE plpgsql
@@

