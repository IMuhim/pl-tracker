package app.premierleague.endpoint;

import app.premierleague.service.MatchService;
import app.premierleague.ws.RecordResultRequest;
import app.premierleague.ws.RecordResultResponse;
import app.premierleague.ws.RecordResultByTeamsRequest;
import app.premierleague.ws.RecordResultByTeamsResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

// Class is a SOAP endpoint that requests to MatchService adn returns SOAP responses
// Updates the matches
@Endpoint
public class MatchesEndpoint {

  private static final String NS = "http://pltracker.com/match";
  private final MatchService matchService;

  public MatchesEndpoint(MatchService matchService) {
    this.matchService = matchService;
  }

  @PayloadRoot(namespace = NS, localPart = "recordResultRequest")
  @ResponsePayload
  public RecordResultResponse record(@RequestPayload RecordResultRequest req) {
    var updated = matchService.recordResult(
        req.getDbMatchId(),
        req.getHomeScore(),
        req.getAwayScore()
    );
    var resp = new RecordResultResponse();
    resp.setUpdatedId(updated.getId());
    resp.setStatus(updated.getStatus());
    resp.setHomeScore(updated.getHomeGoals());
    resp.setAwayScore(updated.getAwayGoals());
    resp.setMessage("OK");
    return resp;
  }

  @PayloadRoot(namespace = NS, localPart = "recordResultByTeamsRequest")
  @ResponsePayload
  public RecordResultByTeamsResponse recordByTeams(@RequestPayload RecordResultByTeamsRequest req) {
    var updated = matchService.recordResultByTeams(
        (int) req.getHomeTeamId(),
        (int) req.getAwayTeamId(),
        req.getHomeScore(),
        req.getAwayScore()
    );
    var resp = new RecordResultByTeamsResponse();
    resp.setUpdatedId(updated.getId());
    resp.setStatus(updated.getStatus());
    resp.setHomeScore(updated.getHomeGoals());
    resp.setAwayScore(updated.getAwayGoals());
    resp.setMessage("OK");
    return resp;
  }
}
