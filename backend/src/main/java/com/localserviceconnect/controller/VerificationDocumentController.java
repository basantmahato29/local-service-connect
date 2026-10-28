package com.localserviceconnect.controller;

import com.localserviceconnect.entity.User;
import com.localserviceconnect.entity.VerificationDocument;
import com.localserviceconnect.repository.UserRepository;
import com.localserviceconnect.service.VerificationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/verification/documents")
public class VerificationDocumentController {

    private final VerificationService verificationService;
    private final UserRepository userRepository;

    public VerificationDocumentController(VerificationService verificationService, UserRepository userRepository) {
        this.verificationService = verificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> getDocumentFile(
            Authentication auth,
            @PathVariable Long documentId
    ) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to access documents");
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        Resource resource = verificationService.getDocumentResource(documentId, user);
        VerificationDocument doc = verificationService.getDocumentMetadata(documentId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(doc.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }
}
