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

DO $do$
DECLARE
  team_ids   int[];
  n          int;
  rounds     int;
  half       int;
  r          int;
  i          int;

  left_ids   int[];
  right_ids  int[];

  season_start timestamp := timestamp '2025-08-09 12:30:00';
  matchweek_start timestamp;
  matchweek_start2 timestamp;

  slot_day    int[]   := ARRAY[0,0,0,0,0,0, 1,1,1, 2];
  slot_hour   int[]   := ARRAY[12,15,15,15,15,17, 12,14,16, 20];
  slot_minute int[]   := ARRAY[30, 0, 0, 0, 0,30,  0,30,30,  0];

  slot_idx int;
  home_id int;
  away_id int;

  tmp int;
BEGIN
  SELECT array_agg(id ORDER BY short_name) INTO team_ids FROM teams;
  n := COALESCE(array_length(team_ids,1), 0);

  IF n <> 20 THEN
    RAISE EXCEPTION 'Expected 20 teams, found % (insert all teams first)', n;
  END IF;

  rounds := n - 1;
  half   := n / 2;

  left_ids  := team_ids[1:half];
  right_ids := team_ids[half+1:n];

  -- first half
  FOR r IN 0..(rounds-1) LOOP
    slot_idx := 1;
    matchweek_start := season_start + (r * interval '7 days');

    FOR i IN 1..half LOOP
      home_id := left_ids[i];
      away_id := right_ids[half - i + 1];

      INSERT INTO matches (home_team_id, away_team_id, kickoff, venue, status)
      VALUES (
        home_id,
        away_id,
        (date_trunc('day', matchweek_start)
           + make_interval(days => slot_day[slot_idx],
                           hours => slot_hour[slot_idx],
                           mins  => slot_minute[slot_idx])),
        'TBC',
        'SCHEDULED'
      );

      slot_idx := slot_idx + 1;
    END LOOP;

    IF half > 1 THEN
      tmp := left_ids[2];
      left_ids[2] := right_ids[1];
      FOR i IN 1..(half-1) LOOP
        right_ids[i] := right_ids[i+1];
      END LOOP;
      right_ids[half] := tmp;
    END IF;
  END LOOP;

  -- second half (reverse home/away)
  left_ids  := team_ids[1:half];
  right_ids := team_ids[half+1:n];

  FOR r IN 0..(rounds-1) LOOP
    slot_idx := 1;
    matchweek_start2 := season_start + ((r + rounds) * interval '7 days');

    FOR i IN 1..half LOOP
      home_id := right_ids[half - i + 1];
      away_id := left_ids[i];

      INSERT INTO matches (home_team_id, away_team_id, kickoff, venue, status)
      VALUES (
        home_id,
        away_id,
        (date_trunc('day', matchweek_start2)
           + make_interval(days => slot_day[slot_idx],
                           hours => slot_hour[slot_idx],
                           mins  => slot_minute[slot_idx])),
        'TBC',
        'SCHEDULED'
      );

      slot_idx := slot_idx + 1;
    END LOOP;

    IF half > 1 THEN
      tmp := left_ids[2];
      left_ids[2] := right_ids[1];
      FOR i IN 1..(half-1) LOOP
        right_ids[i] := right_ids[i+1];
      END LOOP;
      right_ids[half] := tmp;
    END IF;
  END LOOP;

  RAISE NOTICE 'Fixtures generated: % matches', (SELECT count(*) FROM matches);
END
$do$ LANGUAGE plpgsql
@@
