package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.TicketReplacementPart;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketReplacementPartRepository
    extends JpaRepository<TicketReplacementPart, UUID> {
  List<TicketReplacementPart> findByTicketId(UUID ticketId);
}
