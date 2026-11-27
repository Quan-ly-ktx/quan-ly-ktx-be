package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.enums.TicketPriority;
import com.uit.dormmanagement.enums.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, UUID> {
  Optional<MaintenanceTicket> findByTicketCode(String ticketCode);

  boolean existsByTicketCode(String ticketCode);

  List<MaintenanceTicket> findByRoomId(UUID roomId);

  boolean existsByRoomId(UUID roomId);

  boolean existsByBed_IdIn(java.util.Collection<UUID> bedIds);

  List<MaintenanceTicket> findByReporterTenantId(UUID reporterTenantId);

  List<MaintenanceTicket> findByAssigneeStaffId(UUID assigneeStaffId);

  List<MaintenanceTicket> findByStatus(TicketStatus status);

  List<MaintenanceTicket> findByPriority(TicketPriority priority);

  List<MaintenanceTicket> findByAssigneeStaffIdAndStatus(UUID assigneeStaffId, TicketStatus status);

  @Query(
      """
      SELECT DISTINCT mt FROM MaintenanceTicket mt
      LEFT JOIN FETCH mt.room r
      LEFT JOIN FETCH mt.replacementParts rp
      WHERE mt.status = com.uit.dormmanagement.enums.TicketStatus.COMPLETED
        AND mt.resolvedAt >= :startInclusive
        AND mt.resolvedAt < :endExclusive
      ORDER BY mt.resolvedAt DESC, mt.ticketCode ASC
      """)
  List<MaintenanceTicket> findCompletedTicketsBetweenWithParts(
      @Param("startInclusive") Instant startInclusive, @Param("endExclusive") Instant endExclusive);
}
