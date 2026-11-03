package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.TicketCategory;
import com.uit.dormmanagement.enums.TicketPriority;
import com.uit.dormmanagement.enums.TicketStatus;
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
import jakarta.persistence.Table;
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

@Entity
@Table(name = "maintenance_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceTicket {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "ticket_code", nullable = false, unique = true)
  private String ticketCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bed_id")
  private Bed bed;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reporter_tenant_id", nullable = false)
  private Tenant reporterTenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assignee_staff_id")
  private StaffProfile assigneeStaff;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TicketCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private TicketPriority priority = TicketPriority.NORMAL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private TicketStatus status = TicketStatus.OPEN;

  @Column private String deadline;

  @Column(name = "resolution_notes", columnDefinition = "TEXT")
  private String resolutionNotes;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<TicketReplacementPart> replacementParts = new ArrayList<>();
}
