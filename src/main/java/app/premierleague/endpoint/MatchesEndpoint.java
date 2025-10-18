package app.premierleague.endpoint;

import app.premierleague.service.MatchService;
import app.premierleague.ws.RecordResultRequest;
import app.premierleague.ws.RecordResultResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

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
    resp.setStatus(updated.getStatus());          // should be "FT" per your service
    resp.setHomeScore(updated.getHomeGoals());
    resp.setAwayScore(updated.getAwayGoals());
    resp.setMessage("OK");
    return resp;
  }
}
