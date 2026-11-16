package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "vehicle_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleChangeRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_vehicle_id", nullable = false)
  private Vehicle currentVehicle;

  @Enumerated(EnumType.STRING)
  @Column(name = "proposed_vehicle_type", nullable = false)
  private VehicleType proposedVehicleType;

  @Column(name = "proposed_license_plate")
  private String proposedLicensePlate;

  @Column(name = "proposed_brand", nullable = false)
  private String proposedBrand;

  @Column(name = "proposed_color", nullable = false)
  private String proposedColor;

  @Column(name = "proposed_registration_front_file_key")
  private String proposedRegistrationFrontFileKey;

  @Column(name = "proposed_registration_back_file_key")
  private String proposedRegistrationBackFileKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private VehicleChangeRequestStatus status = VehicleChangeRequestStatus.PENDING;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "processed_by_staff_id")
  private StaffProfile processedByStaff;

  @Column(name = "processed_at")
  private Instant processedAt;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "new_vehicle_id", unique = true)
  private Vehicle newVehicle;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
