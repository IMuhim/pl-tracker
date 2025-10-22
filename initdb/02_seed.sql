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

SET TIME ZONE 'UTC'
@@
