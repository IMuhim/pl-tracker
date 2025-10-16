const els = {
  baseUrl: null,
  refreshBtn: null,
  autoBtn: null,
  standingsMsg: null,
  standingsTable: null,
  sortBy: null,
  resultsList: null,
  teamFilter: null,
  statusFilter: null
};

let autoTimer = null;

function bindEls(){
  els.baseUrl = document.getElementById('baseUrl');
  els.refreshBtn = document.getElementById('refreshBtn');
  els.autoBtn = document.getElementById('autoBtn');
  els.standingsMsg = document.getElementById('standingsMsg');
  els.standingsTable = document.querySelector('#standingsTable tbody');
  els.sortBy = document.getElementById('sortBy');
  els.resultsList = document.getElementById('resultsList');
  els.teamFilter = document.getElementById('teamFilter');
  els.statusFilter = document.getElementById('statusFilter');
}

function api(base, path){
  const cleanBase = base.replace(/\/$/, '');
  const cleanPath = path.startsWith('/') ? path : '/' + path;
  return `${cleanBase}${cleanPath}`;
}

async function getJSON(url){
  const res = await fetch(url);
  if(!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json();
}

function guessTeamsEndpoint(){ return '/teams'; }
function guessMatchesEndpoint(){ return '/matches'; }
function guessStandingsEndpoint(){ return '/table'; }

function buildTeamMap(teams){
  const map = new Map();
  for(const t of teams){
    const id = t.id ?? t.teamId ?? t.team_id;
    map.set(String(id), {
      id: String(id),
      name: t.name ?? t.teamName ?? t.short_name ?? t.shortName ?? `Team ${id}`
    });
  }
  return map;
}

function normalizeMatch(m){
  return {
    id: m.id,
    homeId: String(m.homeTeamId ?? m.home_team_id ?? m.homeId),
    awayId: String(m.awayTeamId ?? m.away_team_id ?? m.awayId),
    homeGoals: Number(m.homeGoals ?? m.home_goals ?? 0),
    awayGoals: Number(m.awayGoals ?? m.away_goals ?? 0),
    status: (m.status ?? 'SCHEDULED').toUpperCase(),
    kickoff: m.kickoff ?? m.kickoff_at ?? m.date
  };
}

function resultFor(match){
  if(match.status !== 'FT' && match.status !== 'LIVE') return { type: 'pending' };
  if(match.homeGoals > match.awayGoals) return { type: 'home' };
  if(match.homeGoals < match.awayGoals) return { type: 'away' };
  return { type: 'draw' };
}

function computeStandings(matches, teamMap){
  const table = new Map();
  for(const [,team] of teamMap) table.set(team.id, { teamId: team.id, team: team.name, p:0,w:0,d:0,l:0,gf:0,ga:0,gd:0,pts:0 });
  for(const raw of matches){
    const m = normalizeMatch(raw);
    if(!(m.homeId && m.awayId)) continue;
    const home = table.get(m.homeId); const away = table.get(m.awayId);
    if(!home || !away) continue;
    if(m.status !== 'FT') continue; // only finished in table

    home.p++; away.p++;
    home.gf += m.homeGoals; home.ga += m.awayGoals;
    away.gf += m.awayGoals; away.ga += m.homeGoals;
    home.gd = home.gf - home.ga; away.gd = away.gf - away.ga;

    if(m.homeGoals > m.awayGoals){ home.w++; away.l++; home.pts += 3; }
    else if(m.homeGoals < m.awayGoals){ away.w++; home.l++; away.pts += 3; }
    else { home.d++; away.d++; home.pts += 1; away.pts += 1; }
  }
  return Array.from(table.values());
}

function normalizeStandingsFromAPI(rows, teamMap){
  return rows.map(r => {
    const id = String(r.teamId ?? r.team_id ?? r.id);
    const name = r.team ?? r.name ?? teamMap.get(id)?.name ?? `Team ${id}`;
    const p = r.p ?? r.played ?? r.playedGames ?? r.P ?? 0;
    const w = r.w ?? r.won ?? r.W ?? 0;
    const d = r.d ?? r.draw ?? r.D ?? 0;
    const l = r.l ?? r.lost ?? r.L ?? 0;
    const gf = r.gf ?? r.goalsFor ?? r.F ?? 0;
    const ga = r.ga ?? r.goalsAgainst ?? r.A ?? 0;
    const gd = r.gd ?? r.goalDifference ?? r.GD ?? (gf - ga);
    const pts = r.pts ?? r.points ?? r.Pts ?? 0;
    return { teamId: id, team: name, p, w, d, l, gf, ga, gd, pts };
  });
}

function renderStandings(rows){
  const key = els.sortBy.value;
  const sorted = [...rows];
  if(key === 'points-gd-gs'){
    sorted.sort((a,b) => b.pts - a.pts || b.gd - a.gd || b.gf - a.gf || a.team.localeCompare(b.team));
  } else {
    sorted.sort((a,b) => a.team.localeCompare(b.team));
  }
  els.standingsTable.innerHTML = sorted.map((r,i)=>`
    <tr>
      <td class="mono">${i+1}</td>
      <td>${escapeHtml(r.team)}</td>
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

function renderResults(matches, teamMap){
  const teamSel = els.teamFilter.value;
  const statusSel = els.statusFilter.value;
  const items = matches.map(m => normalizeMatch(m)).filter(m => {
    const teamOk = !teamSel || m.homeId === teamSel || m.awayId === teamSel;
    const statusOk = !statusSel || m.status === statusSel;
    return teamOk && statusOk;
  }).sort((a,b)=> new Date(b.kickoff) - new Date(a.kickoff));

  els.resultsList.innerHTML = items.map(m => {
    const home = teamMap.get(m.homeId)?.name ?? `Home ${m.homeId}`;
    const away = teamMap.get(m.awayId)?.name ?? `Away ${m.awayId}`;
    const r = resultFor(m);
    let label = '<span class="badge">'+m.status+'</span>';
    if(r.type === 'home') label = `<span class="badge winner">Home win</span>`;
    else if(r.type === 'away') label = `<span class="badge winner">Away win</span>`;
    else if(r.type === 'draw') label = `<span class="badge draw">Draw</span>`;
    const date = m.kickoff ? new Date(m.kickoff) : null;
    const when = date ? date.toLocaleString() : '';
    return `
    <div class="row">
      <div class="teams">
        ${label}
        <strong>${escapeHtml(home)}</strong>
        <span class="muted">vs</span>
        <strong>${escapeHtml(away)}</strong>
      </div>
      <div class="mono score">${m.homeGoals} - ${m.awayGoals}</div>
      <div class="tiny full">${when ? when : ''}</div>
    </div>`;
  }).join('');
}

function populateTeamFilter(teams){
  const opts = teams.map(t => ({ id: String(t.id ?? t.teamId ?? t.team_id), name: t.name ?? t.teamName ?? t.short_name ?? t.shortName }))
    .sort((a,b)=> a.name.localeCompare(b.name))
    .map(t => `<option value="${t.id}">${escapeHtml(t.name)}</option>`).join('');
  const current = els.teamFilter.value;
  els.teamFilter.innerHTML = `<option value="">All teams</option>` + opts;
  if(current) els.teamFilter.value = current;
}

function escapeHtml(str){
  return String(str).replace(/[&<>"']/g, s => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[s]));
}

async function loadData(){
  const base = els.baseUrl.value.trim();
  els.standingsMsg.textContent = 'Loading…';
  try {
    const [teams, matches] = await Promise.all([
      getJSON(api(base, guessTeamsEndpoint(base))),
      getJSON(api(base, guessMatchesEndpoint(base)))
    ]);
    const teamMap = buildTeamMap(teams);
    populateTeamFilter(teams);

    let standings;
    try {
      standings = await getJSON(api(base, guessStandingsEndpoint(base)));
      standings = normalizeStandingsFromAPI(standings, teamMap);
      els.standingsMsg.textContent = 'Using /standings from API';
    } catch(e){
      const normMatches = matches.map(m => normalizeMatch(m));
      standings = computeStandings(normMatches, teamMap);
      els.standingsMsg.textContent = 'Computed from /matches';
    }

    renderStandings(standings);
    renderResults(matches, teamMap);
  } catch (err){
    console.error(err);
    els.standingsMsg.innerHTML = `<span class="error">${err.message}. Check your API base & CORS.</span>`;
    els.standingsTable.innerHTML = '';
    els.resultsList.innerHTML = '';
  }
}

function setupEvents(){
  els.refreshBtn.addEventListener('click', loadData);
  els.sortBy.addEventListener('change', () => renderStandings(window.__lastStandings || []));
  els.teamFilter.addEventListener('change', () => loadData());
  els.statusFilter.addEventListener('change', () => loadData());
  els.autoBtn.addEventListener('click', () => {
    if(autoTimer){ clearInterval(autoTimer); autoTimer = null; els.autoBtn.textContent = 'Auto-refresh: Off'; return; }
    autoTimer = setInterval(loadData, 8000);
    els.autoBtn.textContent = 'Auto-refresh: On';
  });
}

const _renderStandings = renderStandings;
renderStandings = function(rows){ window.__lastStandings = rows; _renderStandings(rows); };

window.addEventListener('DOMContentLoaded', () => {
  bindEls();
  setupEvents();
  loadData();
});
