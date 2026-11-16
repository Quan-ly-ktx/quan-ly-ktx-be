package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.ContractStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "contract_code", nullable = false, unique = true)
  private String contractCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bed_id", nullable = false)
  private Bed bed;

  @Column(name = "room_code_snapshot", nullable = false)
  private String roomCodeSnapshot;

  @Column(name = "bed_code_snapshot", nullable = false)
  private String bedCodeSnapshot;

  @Column(name = "start_on", nullable = false)
  private LocalDate startOn;

  @Column(name = "original_end_on", nullable = false)
  private LocalDate originalEndOn;

  @Column(name = "original_monthly_rent", nullable = false, precision = 14, scale = 0)
  private BigDecimal originalMonthlyRent;

  @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 0)
  private BigDecimal depositAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ContractStatus status = ContractStatus.ACTIVE;

  @Column(name = "checked_out_at")
  private Instant checkedOutAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<ContractDocument> documents = new ArrayList<>();

  @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sequenceNo ASC")
  @Builder.Default
  private List<ContractRenewal> renewals = new ArrayList<>();

  @OneToMany(mappedBy = "contract")
  @Builder.Default
  private List<Invoice> invoices = new ArrayList<>();

  // Convenience methods
  public LocalDate getStartDate() {
    return startOn;
  }

  public LocalDate getEndDate() {
    if (renewals != null && !renewals.isEmpty()) {
      return renewals.getLast().getNewEndOn();
    }
    return originalEndOn;
  }

  public BigDecimal getMonthlyRent() {
    if (renewals != null && !renewals.isEmpty()) {
      return renewals.getLast().getMonthlyRent();
    }
    return originalMonthlyRent;
  }

  public String getSlotKeySnapshot() {
    return roomCodeSnapshot + "-" + bedCodeSnapshot;
  }

  public Instant getSignedAt() {
    return createdAt;
  }

  public String getContractDocumentUrl() {
    return null;
  }
}
