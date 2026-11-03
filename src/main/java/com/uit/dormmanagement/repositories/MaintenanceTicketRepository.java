package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.enums.TicketPriority;
import com.uit.dormmanagement.enums.TicketStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, UUID> {
  Optional<MaintenanceTicket> findByTicketCode(String ticketCode);

  boolean existsByTicketCode(String ticketCode);

  List<MaintenanceTicket> findByRoomId(UUID roomId);

  List<MaintenanceTicket> findByReporterTenantId(UUID reporterTenantId);

  List<MaintenanceTicket> findByAssigneeStaffId(UUID assigneeStaffId);

  List<MaintenanceTicket> findByStatus(TicketStatus status);

  List<MaintenanceTicket> findByPriority(TicketPriority priority);

  List<MaintenanceTicket> findByAssigneeStaffIdAndStatus(UUID assigneeStaffId, TicketStatus status);
}
