package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  interface InvoiceSummaryProjection {
    InvoiceStatus getStatus();

    LocalDate getDueOn();

    BigDecimal getAmount();
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Invoice i WHERE i.id = :id")
  Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

  Optional<Invoice> findByInvoiceCode(String invoiceCode);

  boolean existsByInvoiceCode(String invoiceCode);

  List<Invoice> findByContractId(UUID contractId);

  List<Invoice> findByBillingPeriod(LocalDate billingPeriod);

  List<Invoice> findByStatus(InvoiceStatus status);

  List<Invoice> findByBillingPeriodAndStatus(LocalDate billingPeriod, InvoiceStatus status);

  Optional<Invoice> findByContractIdAndBillingPeriod(UUID contractId, LocalDate billingPeriod);

  boolean existsByContractTenantIdAndStatusIn(UUID tenantId, Collection<InvoiceStatus> statuses);

  List<Invoice> findByContractTenantIdOrderByBillingPeriodDescIssuedAtDesc(UUID tenantId);

  List<Invoice> findByContractTenantIdAndStatusOrderByBillingPeriodDescIssuedAtDesc(
      UUID tenantId, InvoiceStatus status);

  @Query(
      value =
          """
          SELECT i FROM Invoice i
          JOIN i.contract c
          JOIN c.tenant t
          JOIN t.user u
          WHERE (:status IS NULL OR i.status = :status)
            AND (CAST(:billingPeriod AS LocalDate) IS NULL OR i.billingPeriod = :billingPeriod)
            AND (:overdueOnly = FALSE
                 OR (i.status = com.uit.dormmanagement.enums.InvoiceStatus.DEBT
                     AND i.dueOn < :today))
            AND (:search IS NULL OR :search = ''
                 OR LOWER(i.invoiceCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.roomCodeSnapshot) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.idCardNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
          """,
      countQuery =
          """
          SELECT COUNT(i) FROM Invoice i
          JOIN i.contract c
          JOIN c.tenant t
          JOIN t.user u
          WHERE (:status IS NULL OR i.status = :status)
            AND (CAST(:billingPeriod AS LocalDate) IS NULL OR i.billingPeriod = :billingPeriod)
            AND (:overdueOnly = FALSE
                 OR (i.status = com.uit.dormmanagement.enums.InvoiceStatus.DEBT
                     AND i.dueOn < :today))
            AND (:search IS NULL OR :search = ''
                 OR LOWER(i.invoiceCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.roomCodeSnapshot) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.idCardNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
          """)
  Page<Invoice> findInvoicesWithFilters(
      @Param("search") String search,
      @Param("status") InvoiceStatus status,
      @Param("billingPeriod") LocalDate billingPeriod,
      @Param("overdueOnly") boolean overdueOnly,
      @Param("today") LocalDate today,
      Pageable pageable);

  @Query(
      """
      SELECT i.status AS status, i.dueOn AS dueOn, i.amount AS amount
      FROM Invoice i
      WHERE (CAST(:billingPeriod AS LocalDate) IS NULL OR i.billingPeriod = :billingPeriod)
      """)
  List<InvoiceSummaryProjection> findAllForSummary(@Param("billingPeriod") LocalDate billingPeriod);
}
