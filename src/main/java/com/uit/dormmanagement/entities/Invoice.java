package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.InvoiceStatus;
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
    name = "invoices",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_invoices_contract_period",
          columnNames = {"contract_id", "billing_period"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_code", nullable = false, unique = true)
  private String invoiceCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @Column(name = "billing_period", nullable = false)
  private LocalDate billingPeriod;

  @Column(nullable = false, precision = 14, scale = 0)
  private BigDecimal amount;

  @Column(name = "due_on", nullable = false)
  private LocalDate dueOn;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private InvoiceStatus status = InvoiceStatus.DEBT;

  @Column(name = "proof_file_key")
  private String proofFileKey;

  @Column(name = "proof_submitted_at")
  private Instant proofSubmittedAt;

  @CreationTimestamp
  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @OneToOne(mappedBy = "invoice")
  private PaymentTransaction paymentTransaction;

  // Compatibility helpers
  public LocalDate getDueDate() {
    return dueOn;
  }
}
