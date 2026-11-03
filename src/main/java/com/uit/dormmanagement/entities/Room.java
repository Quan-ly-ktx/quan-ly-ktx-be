package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "rooms")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_number", nullable = false, unique = true)
  private String roomNumber;

  @Column(nullable = false)
  private int floor;

  @Enumerated(EnumType.STRING)
  @Column(name = "room_type", nullable = false)
  private RoomType roomType;

  @Enumerated(EnumType.STRING)
  @Column(name = "gender_policy", nullable = false)
  private GenderPolicy genderPolicy;

  @Column(name = "total_beds", nullable = false)
  private int totalBeds;

  @Column(name = "occupied_beds", nullable = false)
  @Builder.Default
  private int occupiedBeds = 0;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal basePrice;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Bed> beds = new ArrayList<>();
}
