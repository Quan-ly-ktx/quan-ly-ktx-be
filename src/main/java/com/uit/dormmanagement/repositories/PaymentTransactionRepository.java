package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
  Optional<PaymentTransaction> findByReceiptCode(String receiptCode);

  boolean existsByReceiptCode(String receiptCode);

  List<PaymentTransaction> findByInvoiceId(UUID invoiceId);
}
