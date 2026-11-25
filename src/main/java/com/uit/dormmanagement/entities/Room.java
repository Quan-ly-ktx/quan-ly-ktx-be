package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.GenderPolicy;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "rooms")
@SQLRestriction("archived_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_number", nullable = false)
  private String roomNumber;

  @Column(nullable = false)
  private int floor;

  @Enumerated(EnumType.STRING)
  @Column(name = "gender_policy", nullable = false)
  private GenderPolicy genderPolicy;

  @Column(name = "total_beds", nullable = false, columnDefinition = "smallint")
  private short totalBeds;

  @Column(name = "base_price", nullable = false, precision = 14, scale = 0)
  private BigDecimal basePrice;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "room")
  @Builder.Default
  private List<Bed> beds = new ArrayList<>();
}
