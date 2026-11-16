package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "vehicles",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_vehicles_id_tenant_id", columnNames = {"id", "tenant_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Enumerated(EnumType.STRING)
  @Column(name = "vehicle_type", nullable = false)
  private VehicleType vehicleType;

  @Column(name = "license_plate")
  private String licensePlate;

  @Column(nullable = false)
  private String brand;

  @Column(nullable = false)
  private String color;

  @Column(name = "registration_front_file_key")
  private String registrationFrontFileKey;

  @Column(name = "registration_back_file_key")
  private String registrationBackFileKey;

  @Column(name = "card_code")
  private String cardCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private VehicleStatus status = VehicleStatus.PENDING_CARD;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rejected_by_staff_id")
  private StaffProfile rejectedByStaff;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "replaced_at")
  private Instant replacedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "replaced_by_vehicle_id")
  private Vehicle replacedByVehicle;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "replaced_vehicle_id")
  private Vehicle replacedVehicle;

  // Compatibility helpers
  public String getRegistrationFrontUrl() {
    return registrationFrontFileKey;
  }

  public String getRegistrationBackUrl() {
    return registrationBackFileKey;
  }
}
