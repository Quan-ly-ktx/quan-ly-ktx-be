package com.uit.dormmanagement.entities;

import com.uit.dormmanagement.enums.ContractDocumentCategory;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "contract_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "renewal_id")
  private ContractRenewal renewal;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  @Builder.Default
  private ContractDocumentCategory documentType = ContractDocumentCategory.ORIGINAL_CONTRACT;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "mime_type", nullable = false)
  private String mimeType;

  @Column(name = "storage_file_key", nullable = false, unique = true)
  private String storageFileKey;

  @CreationTimestamp
  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  // Compatibility helpers
  public String getFileName() {
    return originalFilename;
  }

  public String getFileUrl() {
    return storageFileKey;
  }

  public ContractDocumentCategory getDocumentCategory() {
    return documentType;
  }

  public String getFileType() {
    return mimeType;
  }

  public Long getFileSize() {
    return null;
  }
}
