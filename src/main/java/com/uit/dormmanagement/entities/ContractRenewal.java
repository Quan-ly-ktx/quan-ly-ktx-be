package com.uit.dormmanagement.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "contract_renewals",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_contract_renewals_sequence", columnNames = {"contract_id", "sequence_no"}),
      @UniqueConstraint(name = "uq_contract_renewals_effective", columnNames = {"contract_id", "effective_from"}),
      @UniqueConstraint(name = "uq_contract_renewals_id_contract", columnNames = {"id", "contract_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractRenewal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @Column(name = "sequence_no", nullable = false)
  private Integer sequenceNo;

  @Column(name = "signed_on", nullable = false)
  private LocalDate signedOn;

  @Column(name = "previous_end_on", nullable = false)
  private LocalDate previousEndOn;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "new_end_on", nullable = false)
  private LocalDate newEndOn;

  @Column(name = "monthly_rent", nullable = false, precision = 14, scale = 0)
  private BigDecimal monthlyRent;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
