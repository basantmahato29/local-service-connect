package com.localserviceconnect.repository;

import com.localserviceconnect.entity.DocumentType;
import com.localserviceconnect.entity.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, Long> {
    List<VerificationDocument> findByApplicationIdOrderByUploadedAtAsc(Long applicationId);
    List<VerificationDocument> findByApplicationIdAndDocumentType(Long applicationId, DocumentType documentType);
    void deleteByApplicationIdAndDocumentType(Long applicationId, DocumentType documentType);
}
