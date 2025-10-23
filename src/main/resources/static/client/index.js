const els = {
  standingsBody: null,
  gamesBody: null,
  teamSelect: null,
  msg: null
};

function api(path){
  const base = window.location.origin;
  const clean = path.startsWith('/') ? path : '/' + path;
  return base + clean;
}
async function jget(path){
  const r = await fetch(api(path));
  if(!r.ok) throw new Error(`${r.status} ${r.statusText}`);
  return r.json();
}

function buildTeamMap(teams){
  const m = new Map();
  for(const t of teams){
    const id = String(t.id ?? t.teamId ?? t.team_id);
    const name = t.name ?? t.teamName ?? t.short_name ?? t.shortName ?? `Team ${id}`;
    m.set(id, name);
  }
  return m;
}

function normalizeMatch(m){
  return {
    id: m.id ?? m.matchId ?? m.match_id,
    homeId: String(m.homeId ?? m.homeTeamId ?? m.home_team_id),
    awayId: String(m.awayId ?? m.awayTeamId ?? m.away_team_id),
    homeGoals: Number(m.homeGoals ?? m.home_goals ?? 0),
    awayGoals: Number(m.awayGoals ?? m.away_goals ?? 0),
    status: String(m.status ?? 'SCHEDULED').trim().toUpperCase(),
    kickoff: m.kickoff ?? m.kickoff_at ?? m.date ?? null
  };
}

function computeStandings(matches, teamMap){
  const table = new Map();
  for(const [id,name] of teamMap){
    table.set(id, { teamId:id, team:name, p:0,w:0,d:0,l:0,gf:0,ga:0,gd:0,pts:0 });
  }
  for(const raw of matches){
    const m = normalizeMatch(raw);
    if(m.status !== 'FT') continue;
    const home = table.get(m.homeId), away = table.get(m.awayId);
    if(!home || !away) continue;

    home.p++; away.p++;
    home.gf += m.homeGoals; home.ga += m.awayGoals;
    away.gf += m.awayGoals; away.ga += m.homeGoals;
    home.gd = home.gf - home.ga; away.gd = away.gf - away.ga;

    if(m.homeGoals > m.awayGoals){ home.w++; away.l++; home.pts += 3; }
    else if(m.homeGoals < m.awayGoals){ away.w++; home.l++; away.pts += 3; }
    else { home.d++; away.d++; home.pts++; away.pts++; }
  }
  return Array.from(table.values())
    .sort((a,b)=> b.pts - a.pts || b.gd - a.gd || b.gf - a.gf || a.team.localeCompare(b.team));
}

function renderStandings(rows){
  els.standingsBody.innerHTML = rows.map((r,i)=>`
    <tr>
      <td class="mono">${i+1}</td>
      <td>${esc(r.team)}</td>
      <td class="right mono">${r.p}</td>
      <td class="right mono">${r.w}</td>
      <td class="right mono">${r.d}</td>
      <td class="right mono">${r.l}</td>
      <td class="right mono">${r.gf}</td>
      <td class="right mono">${r.ga}</td>
      <td class="right mono">${r.gd}</td>
      <td class="right mono">${r.pts}</td>
    </tr>
  `).join('');
}

function gameRow(teamId, m, teamMap){
  const isHome = m.homeId === teamId;
  const opponentId = isHome ? m.awayId : m.homeId;
  const opponent = teamMap.get(opponentId) ?? `Team ${opponentId}`;
  const teamGoals = isHome ? m.homeGoals : m.awayGoals;
  const oppGoals  = isHome ? m.awayGoals : m.homeGoals;

  const score = (m.status === 'FT') ? `${teamGoals} - ${oppGoals}` : '0 - 0';

  let result = '—';
  if (m.status === 'FT') {
    if (teamGoals > oppGoals) result = 'Win';
    else if (teamGoals < oppGoals) result = 'Loss';
    else result = 'Draw';
  }

  return `
    <tr>
      <td>${esc(opponent)}</td>
      <td class="right mono">${score}</td>
      <td class="right">${result}</td>
    </tr>
  `;
}

function esc(s){
  return String(s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[ch]));
}


async function loadFixturesForTeam(teamId, globalMatches){
  // 1) Prefer the server’s filtered endpoint
  try {
    const dto = await jget(`/teams/${teamId}/fixtures?status=FT`);  // <-- add status=FT
    if (Array.isArray(dto) && dto.length) {
      // If your API returns plain matches:
      if ('homeTeamId' in dto[0] || 'home_team_id' in dto[0]) {
        return dto.map(normalizeMatch);
      }
      // If it returns a custom shape with "opponent" (older DTOs)
      if ('opponent' in dto[0]) {
        return dto.map(x => ({
          id: x.id ?? x.matchId ?? x.match_id ?? undefined,
          homeId: x.home === true ? String(teamId) : 'OPP',
          awayId: x.home === true ? 'OPP' : String(teamId),
          homeGoals: x.home === true ? Number(x.goalsFor ?? 0) : Number(x.goalsAgainst ?? 0),
          awayGoals: x.home === true ? Number(x.goalsAgainst ?? 0) : Number(x.goalsFor ?? 0),
          status: String(x.status ?? 'FT').trim().toUpperCase(),
          kickoff: x.kickoff ?? null,
          _opponentName: x.opponent
        }));
      }
    }
  } catch { /* fall through to fallback */ }

  // 2) Fallback: filter from the global match list we already loaded
  return globalMatches
    .map(normalizeMatch)
    .filter(m =>
      (m.homeId === String(teamId) || m.awayId === String(teamId)) &&
      m.status === 'FT'
    );
}


function renderTeamGamesFromList(teamId, items, teamMap){
  const tid = String(teamId); // normalize
  items.sort((a,b)=> new Date(a.kickoff || 0) - new Date(b.kickoff || 0));

  const rows = items.map(m => {
    if (m._opponentName) {
      const teamGoals = (m.homeId === tid) ? m.homeGoals : m.awayGoals;
      const oppGoals  = (m.homeId === tid) ? m.awayGoals : m.homeGoals;
      const score = (m.status === 'FT') ? `${teamGoals} - ${oppGoals}` : '0 - 0';
      let result = '—';
      if (m.status === 'FT') {
        if (teamGoals > oppGoals) result = 'Win';
        else if (teamGoals < oppGoals) result = 'Loss';
        else result = 'Draw';
      }
      return `
        <tr>
          <td>${esc(m._opponentName)}</td>
          <td class="right mono">${score}</td>
          <td class="right">${result}</td>
        </tr>
      `;
    }
    return gameRow(tid, m, teamMap);
  }).join('');

  els.gamesBody.innerHTML = rows || `
    <tr>
      <td colspan="3" class="right" style="text-align:center; opacity:.7">No past games</td>
    </tr>
  `;
}

async function main(){
  els.standingsBody = document.querySelector('#standingsTable tbody');
  els.gamesBody     = document.querySelector('#gamesTable tbody');
  els.teamSelect    = document.getElementById('teamSelect');
  els.msg           = document.getElementById('msg');

  try {
    const [teams, matches] = await Promise.all([
      jget('/teams'),
      jget('/matches')
    ]);

    const teamMap = buildTeamMap(teams);

    // Populate dropdown (alphabetical)
    const sortedTeams = [...teamMap.entries()]
      .map(([id,name]) => ({ id: String(id), name }))
      .sort((a,b)=> a.name.localeCompare(b.name));
    els.teamSelect.innerHTML = sortedTeams
      .map(t => `<option value="${t.id}">${esc(t.name)}</option>`)
      .join('');

    // ALWAYS compute standings from matches so it's never stale
    const standings = computeStandings(matches, teamMap);
    renderStandings(standings);

    // Initial render
    const initialTeamId = String(sortedTeams[0]?.id || '');
    if (initialTeamId) {
      els.teamSelect.value = initialTeamId;
      const fixtures = await loadFixturesForTeam(initialTeamId, matches);
      renderTeamGamesFromList(initialTeamId, fixtures, teamMap);
    }

    // On change
    els.teamSelect.addEventListener('change', async () => {
      const teamId = String(els.teamSelect.value);
      const fixtures = await loadFixturesForTeam(teamId, matches);
      renderTeamGamesFromList(teamId, fixtures, teamMap);
    });

  } catch (e){
    console.error(e);
    els.msg.textContent = 'Failed to load data.';
  }
}

window.addEventListener('DOMContentLoaded', main);
