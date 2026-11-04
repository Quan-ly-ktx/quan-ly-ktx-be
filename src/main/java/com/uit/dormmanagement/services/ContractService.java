package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
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

  ContractDetailResponse terminateContract(UUID id);

  List<ContractDocumentResponse> uploadDocuments(
      UUID contractId, ContractDocumentCategory category, List<MultipartFile> files);

  void deleteDocument(UUID contractId, UUID documentId);

  ContractDetailResponse getMyContract(UUID userId);
}
