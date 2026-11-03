package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.ViolationLevel;
import com.uit.dormmanagement.enums.ViolationType;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "violations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "violation_code", nullable = false, unique = true)
  private String violationCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "issued_by_staff_id", nullable = false)
  private StaffProfile issuedByStaff;

  @Enumerated(EnumType.STRING)
  @Column(name = "violation_type", nullable = false)
  private ViolationType violationType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ViolationLevel level = ViolationLevel.REMINDER;

  @Column(name = "fine_amount", nullable = false, precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal fineAmount = BigDecimal.ZERO;

  @Column(columnDefinition = "TEXT")
  private String note;

  @Column(name = "occurred_date", nullable = false)
  private LocalDate occurredDate;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
