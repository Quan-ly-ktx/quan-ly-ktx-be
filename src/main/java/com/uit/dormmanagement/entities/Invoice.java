package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.InvoiceKind;
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
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "invoices")
@SQLRestriction("deleted_at IS NULL")
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
  private String billingPeriod;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private InvoiceKind kind = InvoiceKind.ALL_INCLUSIVE_RENT;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private InvoiceStatus status = InvoiceStatus.UNPAID;

  @CreationTimestamp
  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
