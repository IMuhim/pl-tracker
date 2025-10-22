package app.premierleague.cli;

import app.premierleague.ws.RecordResultByTeamsRequest;
import app.premierleague.ws.RecordResultByTeamsResponse;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class SubmitResultCli {

  public static void main(String[] args) throws Exception {
    // --- URLs (with sensible defaults for local dev) ---
    String endpoint    = System.getProperty("endpoint",   "http://localhost:8080/ws");
    String teamsUrl    = System.getProperty("teamsUrl",   "http://localhost:8080/teams");
    String matchesUrl  = System.getProperty("matchesUrl", "http://localhost:8080/matches");

    // --- Fast-path actions (non-interactive) ---
    String action = System.getProperty("action");
    if (action != null) {
      switch (action.toLowerCase()) {
        case "addowner" -> {
          long teamId = Long.parseLong(reqProp("team"));
          String owner = reqProp("owner");
          String json = "{\"owner\":\"" + escapeJson(owner) + "\"}";
          String res = httpPatchJson(teamsUrl + "/" + teamId + "/owner", json);
          System.out.println("Owner set for team " + teamId + " -> " + owner);
          if (res != null && !res.isBlank()) System.out.println("Response: " + res);
          return;
        }
        case "addmatch" -> {
          long home    = Long.parseLong(reqProp("home"));
          long away    = Long.parseLong(reqProp("away"));
          String kickoff = reqProp("kickoff"); // ISO 8601 e.g. 2025-08-09T15:00:00Z
          OffsetDateTime.parse(kickoff);       // early validation

          String venue = System.getProperty("venue");
          String json = (venue == null || venue.isBlank())
              ? String.format("{\"homeTeamId\":%d,\"awayTeamId\":%d,\"kickoff\":\"%s\"}", home, away, kickoff)
              : String.format("{\"homeTeamId\":%d,\"awayTeamId\":%d,\"kickoff\":\"%s\",\"venue\":\"%s\"}",
                  home, away, kickoff, escapeJson(venue));
          String body = httpPostJson(matchesUrl, json);

          System.out.println("Match created: " + home + " vs " + away + " at " + kickoff +
              (venue != null && !venue.isBlank() ? " (" + venue + ")" : ""));
          Integer id = parseIdFromJson(body);
          if (id != null) System.out.println("Created match id: " + id);
          else System.out.println("Response: " + body);
          return;
        }
        case "submitresult" -> {
          long matchId = Long.parseLong(reqProp("matchId"));
          int home = parseNonNegInt(reqProp("home"), "home");
          int away = parseNonNegInt(reqProp("away"), "away");
          String json = String.format("{\"homeGoals\":%d,\"awayGoals\":%d}", home, away);
          String res = httpPatchJson(matchesUrl + "/" + matchId + "/result", json);
          System.out.println("Result saved for match " + matchId + ": " + home + "-" + away);
          if (res != null && !res.isBlank()) System.out.println("Response: " + res);
          return;
        }
        default -> {
          System.err.println("Unknown -Daction: " + action);
          printActionUsage();
          System.exit(2);
        }
      }
    }

    // --- Original flow (interactive SOAP by team names) ---
    if (teamsUrl == null || teamsUrl.isBlank()) {
      System.err.println("Please provide -DteamsUrl, e.g.:");
      System.err.println("  ./gradlew submitOne -Dendpoint=http://localhost:8080/ws -DteamsUrl=http://localhost:8080/teams");
      System.exit(2);
    }

    Map<String,Integer> teamIds = fetchTeamMap(teamsUrl);
    System.out.println("Loaded " + teamIds.size() + " teams from: " + teamsUrl);

    Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath("app.premierleague.ws");
    WebServiceTemplate ws = new WebServiceTemplate(marshaller, marshaller);
    ws.setDefaultUri(endpoint);

    // property fast-path for existing submitter
    String homeNameProp = System.getProperty("homeName");
    String awayNameProp = System.getProperty("awayName");
    String homeScoreProp = System.getProperty("home");
    String awayScoreProp = System.getProperty("away");
    if (homeNameProp != null && awayNameProp != null && homeScoreProp != null && awayScoreProp != null) {
      int homeId = resolveTeamId(teamIds, homeNameProp);
      int awayId = resolveTeamId(teamIds, awayNameProp);
      int home = parseNonNegInt(homeScoreProp, "home");
      int away = parseNonNegInt(awayScoreProp, "away");
      callByTeams(ws, homeId, awayId, home, away);
      return;
    }

    // interactive mode
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("\nPremier League Result Submitter (by team names)");
    System.out.println("SOAP endpoint: " + endpoint);
    System.out.println("Teams URL    : " + teamsUrl);
    System.out.println("Available teams:");
    teamIds.keySet().forEach(n -> System.out.println(" - " + n));

    int homeId = pickTeam(in, "Home team", teamIds);
    int awayId = pickTeamDifferent(in, "Away team", homeId, teamIds);
    int home = askNonNegativeInt(in, "Home score");
    int away = askNonNegativeInt(in, "Away score");

    callByTeams(ws, homeId, awayId, home, away);
  }

  // ----------------- helpers (existing + new) -----------------

  private static Map<String,Integer> fetchTeamMap(String url) throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("Failed to load teams: HTTP " + res.statusCode() + " from " + url);
    }
    String body = res.body();
    Map<String,Integer> map = new LinkedHashMap<>();
    String arr = body.trim();
    if (!arr.startsWith("[")) throw new IllegalStateException("Unexpected payload (not an array) from " + url);
    String inner = arr.substring(1, arr.length()-1).trim();
    if (!inner.isEmpty()) {
      for (String item : inner.split("\\},\\s*\\{")) {
        String obj = item.replace("{","").replace("}","");
        Integer id = null; String name = null;
        for (String kv : obj.split(",\\s*\"")) {
          String s = kv.trim();
          if (!s.startsWith("\"")) s = "\"" + s;
          int colon = s.indexOf(':');
          if (colon < 0) continue;
          String key = s.substring(1, colon).replace("\"","").trim();
          String val = s.substring(colon + 1).trim();
          if ("id".equals(key)) {
            val = val.replaceAll("[^0-9-]", "");
            if (!val.isEmpty()) id = Integer.valueOf(val);
          } else if ("name".equals(key)) {
            if (val.startsWith("\"")) val = val.substring(1);
            if (val.endsWith("\"")) val = val.substring(0, val.length()-1);
            name = val;
          }
        }
        if (id != null && name != null && !name.isBlank()) map.put(name, id);
      }
    }
    if (map.isEmpty()) throw new IllegalStateException("Parsed empty team list from " + url);
    return map;
  }

  private static int resolveTeamId(Map<String,Integer> teamIds, String name) {
    Integer id = teamIds.get(name);
    if (id == null) {
      System.err.println("Unknown team: " + name);
      teamIds.keySet().forEach(t -> System.err.println(" - " + t));
      System.exit(2);
    }
    return id;
  }

  private static int pickTeam(BufferedReader in, String label, Map<String,Integer> teamIds) throws Exception {
    while (true) {
      System.out.print(label + " (name): ");
      String name = in.readLine();
      if (name == null) throw new IllegalStateException("No console input available");
      name = name.trim();
      Integer id = teamIds.get(name);
      if (id != null) return id;
      System.out.println("Unknown team. Please pick from the list above.");
    }
  }

  private static int pickTeamDifferent(BufferedReader in, String label, int notId, Map<String,Integer> teamIds) throws Exception {
    while (true) {
      int id = pickTeam(in, label, teamIds);
      if (id != notId) return id;
      System.out.println("Away team cannot be the same as home.");
    }
  }

  private static int askNonNegativeInt(BufferedReader in, String label) throws Exception {
    while (true) {
      System.out.print(label + ": ");
      String s = in.readLine();
      if (s == null) throw new IllegalStateException("No console input available");
      try {
        int v = Integer.parseInt(s.trim());
        if (v < 0) throw new IllegalArgumentException();
        return v;
      } catch (Exception e) {
        System.out.println("Enter a non-negative whole number.");
      }
    }
  }

  private static int parseNonNegInt(String prop, String name) {
    try {
      int v = Integer.parseInt(prop.trim());
      if (v < 0) throw new IllegalArgumentException();
      return v;
    } catch (Exception e) {
      System.err.println("Invalid non-negative integer for " + name + ": " + prop);
      System.exit(2);
      return 0;
    }
  }

  // ---- HTTP helpers (now return response bodies) ----

  private static String httpPostJson(String url, String json) throws Exception {
    var http = HttpClient.newHttpClient();
    var req = HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) {
      throw new IllegalStateException("POST " + url + " failed " + res.statusCode() + ": " + res.body());
    }
    return res.body();
  }

  private static String httpPatchJson(String url, String json) throws Exception {
    var http = HttpClient.newHttpClient();
    var req = HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) {
      throw new IllegalStateException("PATCH " + url + " failed " + res.statusCode() + ": " + res.body());
    }
    return res.body();
  }

  private static Integer parseIdFromJson(String body) {
    if (body == null) return null;
    // naive parse: look for "id": <number>
    int idx = body.indexOf("\"id\"");
    if (idx < 0) return null;
    int colon = body.indexOf(':', idx);
    if (colon < 0) return null;
    int i = colon + 1;
    while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
    int start = i;
    while (i < body.length() && Character.isDigit(body.charAt(i))) i++;
    try {
      return Integer.valueOf(body.substring(start, i));
    } catch (Exception e) {
      return null;
    }
  }

  private static String reqProp(String name) {
    String v = System.getProperty(name);
    if (v == null || v.isBlank()) {
      printActionUsage();
      throw new IllegalArgumentException("Missing -D" + name);
    }
    return v;
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static void printActionUsage() {
    System.out.println("""
      Actions (use -D, not -P):
        Create fixture:
          -Daction=addMatch -Dhome=<id> -Daway=<id> -Dkickoff=2025-08-09T15:00:00Z [-Dvenue="..."] [-DmatchesUrl=http://localhost:8080/matches]
        Submit result:
          -Daction=submitResult -DmatchId=<id> -Dhome=<n> -Daway=<n> [-DmatchesUrl=http://localhost:8080/matches]
        Set/change owner:
          -Daction=addOwner -Dteam=<id> -Downer="<name>" [-DteamsUrl=http://localhost:8080/teams]
      """);
  }

  // ---- SOAP call (existing) ----
  private static void callByTeams(WebServiceTemplate ws, int homeId, int awayId, int home, int away) {
    RecordResultByTeamsRequest req = new RecordResultByTeamsRequest();
    req.setHomeTeamId(homeId);
    req.setAwayTeamId(awayId);
    req.setHomeScore(home);
    req.setAwayScore(away);
    try {
      RecordResultByTeamsResponse resp = (RecordResultByTeamsResponse) ws.marshalSendAndReceive(req);
      System.out.println("\nServer response:");
      System.out.println("  updatedId = " + resp.getUpdatedId());
      System.out.println("  status    = " + resp.getStatus());
      System.out.println("  score     = " + resp.getHomeScore() + "-" + resp.getAwayScore());
      System.out.println("  message   = " + resp.getMessage());
    } catch (Exception e) {
      System.out.println("\nFAILED to submit:");
      e.printStackTrace(System.out);
      System.exit(1);
    }
  }
}
