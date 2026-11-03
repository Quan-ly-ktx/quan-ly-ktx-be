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
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_replacement_parts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketReplacementPart {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticket_id", nullable = false)
  private MaintenanceTicket ticket;

  @Column(name = "part_name", nullable = false)
  private String partName;

  @Column(nullable = false)
  @Builder.Default
  private int quantity = 1;

  @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "total_cost", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalCost;
}
