package app.premierleague.domain;

import jakarta.persistence.*;

// Built using MatchService.recomputeStandigns()
@Entity @Table(name="standings")
public class Standing {
  @Id @Column(name="team_id") private Integer teamId;
  private int played; private int won; private int drawn; private int lost;
  private int gf; private int ga; private int gd; private int points;
  public Integer getTeamId(){return teamId;}
  public int getPlayed(){return played;} public int getWon(){return won;}
  public int getDrawn(){return drawn;}  public int getLost(){return lost;}
  public int getGf(){return gf;}        public int getGa(){return ga;}
  public int getGd(){return gd;}        public int getPoints(){return points;}
}
