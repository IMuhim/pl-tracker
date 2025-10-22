package app.premierleague.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(name = "short_name", nullable = false, unique = true)
  private String shortName;

  @Column(length = 120)
  private String owner;

  private String city;

  public Integer getId() { return id; }
  public String getName() { return name; }
  public String getShortName() { return shortName; }
  public String getCity() { return city; }
  public String getOwner() { return owner; }

  public void setId(Integer id) { this.id = id; }
  public void setName(String name) { this.name = name; }
  public void setShortName(String shortName) { this.shortName = shortName; }
  public void setCity(String city) { this.city = city; }
  public void setOwner(String owner) { this.owner = owner; }
}
