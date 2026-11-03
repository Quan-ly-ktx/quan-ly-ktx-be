package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.enums.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
  Optional<Invoice> findByInvoiceCode(String invoiceCode);

  boolean existsByInvoiceCode(String invoiceCode);

  List<Invoice> findByContractId(UUID contractId);

  List<Invoice> findByBillingPeriod(String billingPeriod);

  List<Invoice> findByStatus(InvoiceStatus status);

  List<Invoice> findByBillingPeriodAndStatus(String billingPeriod, InvoiceStatus status);
}
