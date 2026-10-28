package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.Provider;
import com.localserviceconnect.repository.ProviderRepository;
import com.localserviceconnect.service.VerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/provider/verification")
public class ProviderVerificationController {

    private final VerificationService verificationService;
    private final ProviderRepository providerRepository;

    public ProviderVerificationController(VerificationService verificationService, ProviderRepository providerRepository) {
        this.verificationService = verificationService;
        this.providerRepository = providerRepository;
    }

    private Provider requireProvider(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return providerRepository.findByUserEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider account not found"));
    }

    @GetMapping("/application")
    public ResponseEntity<VerificationApplicationDto> getApplication(Authentication auth) {
        Provider provider = requireProvider(auth);
        return ResponseEntity.ok(verificationService.getOrCreateApplication(provider));
    }

    @PostMapping("/draft")
    public ResponseEntity<VerificationApplicationDto> saveDraft(
            Authentication auth,
            @RequestBody SaveVerificationDraftRequest request
    ) {
        Provider provider = requireProvider(auth);
        return ResponseEntity.ok(verificationService.saveDraft(provider, request));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationDocumentDto> uploadDocument(
            Authentication auth,
            @RequestParam("type") String docType,
            @RequestParam("file") MultipartFile file
    ) {
        Provider provider = requireProvider(auth);
        String cleanType = docType != null ? docType.split(",")[0].trim() : "";
        return ResponseEntity.ok(verificationService.uploadDocument(provider, cleanType, file));
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            Authentication auth,
            @PathVariable Long documentId
    ) {
        Provider provider = requireProvider(auth);
        verificationService.deleteDocument(provider, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/submit")
    public ResponseEntity<VerificationApplicationDto> submitApplication(
            Authentication auth,
            @RequestBody(required = false) SubmitVerificationRequest request
    ) {
        Provider provider = requireProvider(auth);
        return ResponseEntity.ok(verificationService.submitApplication(provider, request));
    }
}
