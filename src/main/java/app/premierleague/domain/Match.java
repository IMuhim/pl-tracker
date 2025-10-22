package app.premierleague.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Stores team ids, kickoff time, goals and status
@Entity
@Table(name = "matches")
public class Match {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "home_team_id", nullable = false)
  private Integer homeTeamId;

  @Column(name = "away_team_id", nullable = false)
  private Integer awayTeamId;

  @Column(nullable = false)
  private Instant kickoff;

  private String venue;

  @Column(name = "home_goals", nullable = false)
  private Integer homeGoals = 0;

  @Column(name = "away_goals", nullable = false)
  private Integer awayGoals = 0;

  @Column(nullable = false)
  private String status = "SCHEDULED";

  // getters/setters
  public Long getId() { return id; }
  public Integer getHomeTeamId() { return homeTeamId; }
  public void setHomeTeamId(Integer v) { this.homeTeamId = v; }
  public Integer getAwayTeamId() { return awayTeamId; }
  public void setAwayTeamId(Integer v) { this.awayTeamId = v; }
  public Instant getKickoff() { return kickoff; }
  public void setKickoff(Instant v) { this.kickoff = v; }
  public String getVenue() { return venue; }
  public void setVenue(String v) { this.venue = v; }
  public Integer getHomeGoals() { return homeGoals; }
  public void setHomeGoals(Integer v) { this.homeGoals = v; }
  public Integer getAwayGoals() { return awayGoals; }
  public void setAwayGoals(Integer v) { this.awayGoals = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
