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
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SubmitResultCli {

  public static void main(String[] args) throws Exception {
    String endpoint    = System.getProperty("endpoint",   "http://localhost:8080/ws");
    String teamsUrl    = System.getProperty("teamsUrl",   "http://localhost:8080/teams");
    String matchesUrl  = System.getProperty("matchesUrl", "http://localhost:8080/matches");

    String action = System.getProperty("action");
    if (action != null) {
      switch (action.toLowerCase()) {
        case "addowner" -> {
          long teamId = Long.parseLong(reqProp("team"));
          String owner = reqProp("owner");
          String json = "{\"owner\":\"" + escapeJson(owner) + "\"}";
          String res = httpPatchJson(teamsUrl + "/" + teamId + "/owner", json);
          System.out.println("Owner set for team " + teamId + " -> " + owner);
          if (!res.isBlank()) System.out.println("Response: " + res);
          return;
        }
        case "addmatch" -> {
          long home = Long.parseLong(reqProp("home"));
          long away = Long.parseLong(reqProp("away"));
          OffsetDateTime userKickoff = OffsetDateTime.parse(reqProp("kickoff"));
          String kickoffIso = userKickoff.toInstant().toString();
          String json = String.format("{\"homeTeamId\":%d,\"awayTeamId\":%d,\"kickoff\":\"%s\"}",
              home, away, kickoffIso);
          String body = httpPostJson(matchesUrl, json);
          System.out.println("Match created: " + home + " vs " + away + " at " + kickoffIso);
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
          if (!res.isBlank()) System.out.println("Response: " + res);
          return;
        }
        case "wizard" -> runWizard(teamsUrl, matchesUrl);
        default -> {
          System.err.println("Unknown -Daction: " + action);
          printUsage();
          System.exit(2);
        }
      }
      return;
    }

    runWizard(teamsUrl, matchesUrl);
  }

  private static void runWizard(String teamsUrl, String matchesUrl) throws Exception {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("\nPL Tracker v2 Wizard (create fixture → set owners if missing → submit result)");
    System.out.println("Teams API   : " + teamsUrl);
    System.out.println("Matches API : " + matchesUrl);

    // Load teams
    var teamIds = fetchTeamMap(teamsUrl);
    var teamOwners = fetchTeamOwners(teamsUrl);
    System.out.println("Loaded " + teamIds.size() + " teams.");

    // Pick teams by name
    int homeId = pickTeamByName(in, "Home team (name)", teamIds);
    int awayId = pickTeamDifferentByName(in, "Away team (name)", homeId, teamIds);

    OffsetDateTime kickoff = askKickoff(in);
    String kickoffIso = kickoff.toInstant().toString();

    // Create the fixture
    String json = String.format(
        "{\"homeTeamId\":%d,\"awayTeamId\":%d,\"kickoff\":\"%s\"}",
        homeId, awayId, kickoffIso
    );
    String createRes = httpPostJson(matchesUrl, json);
    Integer matchId = parseIdFromJson(createRes);
    if (matchId == null) {
      System.out.println("Created fixture, response: " + createRes);
      System.out.println("Could not parse match id; cannot continue to result step.");
      return;
    }
    System.out.println("Fixture created. Match ID: " + matchId);

    // Owners: prompt ONLY if missing
    ensureOwnerIfMissing(in, teamsUrl, homeId, teamOwners);
    ensureOwnerIfMissing(in, teamsUrl, awayId, teamOwners);

    // Scores → submit result
    int home = askNonNegativeInt(in, "Home score");
    int away = askNonNegativeInt(in, "Away score");
    String resultBody = String.format("{\"homeGoals\":%d,\"awayGoals\":%d}", home, away);
    String patchRes = httpPatchJson(matchesUrl + "/" + matchId + "/result", resultBody);
    System.out.println("Result saved for match " + matchId + ": " + home + "-" + away);
    if (!patchRes.isBlank()) System.out.println("Response: " + patchRes);
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

  private static void ensureOwnerIfMissing(BufferedReader in, String teamsUrl, int teamId,
                                           Map<Integer,String> owners) throws Exception {
    String current = owners.get(teamId);
    if (current != null && !current.isBlank() && !"null".equalsIgnoreCase(current)) {
      System.out.println("Owner already set for team " + teamId + " → " + current + " (skip)");
      return;
    }
    System.out.print("Owner for team " + teamId + " not set. Enter owner (or leave blank to skip): ");
    String ans = in.readLine();
    if (ans != null) ans = ans.trim();
    if (ans != null && !ans.isBlank()) {
      String body = "{\"owner\":\"" + escapeJson(ans) + "\"}";
      String res = httpPatchJson(teamsUrl + "/" + teamId + "/owner", body);
      System.out.println("Owner set for team " + teamId + " -> " + ans);
      if (!res.isBlank()) System.out.println("Response: " + res);
      owners.put(teamId, ans);
    } else {
      System.out.println("Skipped setting owner for team " + teamId + ".");
    }
  }

  private static Map<String,Integer> fetchTeamMap(String url) throws Exception {
    String body = httpGet(url);
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
          int colon = s.indexOf(':'); if (colon < 0) continue;
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
    System.out.println("Available teams:");
    map.keySet().forEach(n -> System.out.println(" - " + n));
    return map;
  }

  private static Map<Integer,String> fetchTeamOwners(String url) throws Exception {
    String body = httpGet(url);
    Map<Integer,String> map = new LinkedHashMap<>();
    String arr = body.trim();
    if (!arr.startsWith("[")) throw new IllegalStateException("Unexpected payload (not an array) from " + url);
    String inner = arr.substring(1, arr.length()-1).trim();
    if (!inner.isEmpty()) {
      for (String item : inner.split("\\},\\s*\\{")) {
        String obj = item.replace("{","").replace("}","");
        Integer id = null; String owner = null;
        for (String kv : obj.split(",\\s*\"")) {
          String s = kv.trim();
          if (!s.startsWith("\"")) s = "\"" + s;
          int colon = s.indexOf(':'); if (colon < 0) continue;
          String key = s.substring(1, colon).replace("\"","").trim();
          String val = s.substring(colon + 1).trim();
          if ("id".equals(key)) {
            val = val.replaceAll("[^0-9-]", "");
            if (!val.isEmpty()) id = Integer.valueOf(val);
          } else if ("owner".equals(key)) {
            if (val.equals("null")) owner = null;
            else {
              if (val.startsWith("\"")) val = val.substring(1);
              if (val.endsWith("\"")) val = val.substring(0, val.length()-1);
              owner = val;
            }
          }
        }
        if (id != null) map.put(id, owner);
      }
    }
    return map;
  }

  private static int pickTeamByName(BufferedReader in, String label, Map<String,Integer> teamIds) throws Exception {
    while (true) {
      System.out.print(label + ": ");
      String name = in.readLine();
      if (name == null) throw new IllegalStateException("No console input available");
      name = name.trim();
      Integer id = teamIds.get(name);
      if (id != null) return id;
      System.out.println("Unknown team. Please pick from the list above.");
    }
  }

  private static int pickTeamDifferentByName(BufferedReader in, String label, int notId, Map<String,Integer> teamIds) throws Exception {
    while (true) {
      int id = pickTeamByName(in, label, teamIds);
      if (id != notId) return id;
      System.out.println("Away team cannot be the same as home.");
    }
  }

  private static OffsetDateTime askKickoff(BufferedReader in) throws Exception {
    while (true) {
      String def = OffsetDateTime.now(ZoneOffset.UTC).withSecond(0).withNano(0).toString();
      System.out.print("Kickoff (ISO-8601, default " + def + "): ");
      String s = in.readLine();
      if (s == null || s.isBlank()) return OffsetDateTime.parse(def);
      try { return OffsetDateTime.parse(s.trim()); }
      catch (DateTimeParseException e) { System.out.println("Please enter ISO-8601, e.g. 2025-08-09T15:00:00Z or 2025-08-09T16:00:00+01:00"); }
    }
  }

  private static String httpGet(String url) throws Exception {
    var http = HttpClient.newHttpClient();
    var req = HttpRequest.newBuilder(URI.create(url)).GET().build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) throw new IllegalStateException("GET " + url + " failed " + res.statusCode() + ": " + res.body());
    return res.body();
  }

  private static String httpPostJson(String url, String json) throws Exception {
    var http = HttpClient.newHttpClient();
    var req = HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) throw new IllegalStateException("POST " + url + " failed " + res.statusCode() + ": " + res.body());
    return res.body();
  }

  private static String httpPatchJson(String url, String json) throws Exception {
    var http = HttpClient.newHttpClient();
    var req = HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) throw new IllegalStateException("PATCH " + url + " failed " + res.statusCode() + ": " + res.body());
    return res.body();
  }

  private static Integer parseIdFromJson(String body) {
    if (body == null) return null;
    int idx = body.indexOf("\"id\""); if (idx < 0) return null;
    int colon = body.indexOf(':', idx); if (colon < 0) return null;
    int i = colon + 1;
    while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
    int start = i;
    while (i < body.length() && Character.isDigit(body.charAt(i))) i++;
    try { return Integer.valueOf(body.substring(start, i)); } catch (Exception e) { return null; }
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

  private static String reqProp(String name) {
    String v = System.getProperty(name);
    if (v == null || v.isBlank()) {
      printUsage();
      throw new IllegalArgumentException("Missing -D" + name);
    }
    return v;
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static void printUsage() {
    System.out.println("""
      Usage:

        # Interactive wizard (recommended):
        ./gradlew submitOne -Daction=wizard -DteamsUrl=http://localhost:8080/teams -DmatchesUrl=http://localhost:8080/matches
        # or simply:
        ./gradlew submitOne   # defaults to wizard

        # Non-interactive actions:
        -Daction=addMatch     -Dhome=<id> -Daway=<id> -Dkickoff=2025-08-09T15:00:00Z
        -Daction=submitResult -DmatchId=<id> -Dhome=<n> -Daway=<n>
        -Daction=addOwner     -Dteam=<id> -Downer="<name>"
      """);
  }

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
