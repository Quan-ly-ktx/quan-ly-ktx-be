package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentContent;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ContractService {

  Page<ContractSummaryResponse> getContracts(
      Pageable pageable, String search, ContractStatus status);

  ContractDetailResponse getContractById(UUID id);

  ContractDetailResponse renewContract(
      UUID id, RenewContractRequest request, List<MultipartFile> annexDocuments);

  ContractDetailResponse terminateContract(
      UUID managerUserId, UUID id, CheckoutContractRequest request);

  default ContractDetailResponse terminateContract(UUID id, CheckoutContractRequest request) {
    return terminateContract(null, id, request);
  }

  List<ContractDocumentResponse> uploadDocuments(
      UUID contractId,
      ContractDocumentCategory category,
      UUID renewalId,
      List<MultipartFile> files);

  default List<ContractDocumentResponse> uploadDocuments(
      UUID contractId, ContractDocumentCategory category, List<MultipartFile> files) {
    return uploadDocuments(contractId, category, null, files);
  }

  void deleteDocument(UUID contractId, UUID documentId);

  ContractDetailResponse getMyContract(UUID userId);

  List<ContractDetailResponse> getMyContractHistory(UUID userId);

  ContractDetailResponse getMyContractById(UUID userId, UUID contractId);

  ContractDocumentContent loadDocumentContentForAdmin(UUID contractId, UUID documentId);

  ContractDocumentContent loadDocumentContentForTenant(
      UUID tenantUserId, UUID contractId, UUID documentId);
}
