package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, UUID> {
  List<ContractDocument> findByContractId(UUID contractId);

  List<ContractDocument> findByContractIdAndDocumentCategory(
      UUID contractId, ContractDocumentCategory documentCategory);

  List<ContractDocument> findByContractIdOrderByUploadedAtDesc(UUID contractId);
}
