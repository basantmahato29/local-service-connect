package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.User;
import com.localserviceconnect.repository.UserRepository;
import com.localserviceconnect.service.VerificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/verification")
public class AdminVerificationController {

    private final VerificationService verificationService;
    private final UserRepository userRepository;

    public AdminVerificationController(VerificationService verificationService, UserRepository userRepository) {
        this.verificationService = verificationService;
        this.userRepository = userRepository;
    }

    private User requireAdmin(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user account not found"));
    }

    @GetMapping("/applications")
    public ResponseEntity<?> listApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        if (page != null || size != null) {
            int pageIndex = (page != null && page >= 0) ? page : 0;
            int pageSize = (size != null && size > 0) ? size : 10;
            Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt", "id");
            if (StringUtils.hasText(sortBy)) {
                Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
                if ("legalName".equalsIgnoreCase(sortBy)) {
                    sort = Sort.by(dir, "legalName").and(Sort.by(Sort.Direction.DESC, "id"));
                } else if ("submittedAt".equalsIgnoreCase(sortBy)) {
                    sort = Sort.by(dir, "submittedAt").and(Sort.by(Sort.Direction.DESC, "id"));
                } else if ("createdAt".equalsIgnoreCase(sortBy)) {
                    sort = Sort.by(dir, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
                } else if ("updatedAt".equalsIgnoreCase(sortBy)) {
                    sort = Sort.by(dir, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"));
                }
            }
            Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
            return ResponseEntity.ok(verificationService.searchApplications(status, type, search, pageable));
        }
        return ResponseEntity.ok(verificationService.searchApplications(status, type, search));
    }

    @GetMapping("/applications/summary-counts")
    public ResponseEntity<Map<String, Long>> getSummaryCounts() {
        return ResponseEntity.ok(verificationService.getSummaryCounts());
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<VerificationApplicationDto> getApplicationDetails(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(verificationService.getApplicationById(applicationId));
    }

    @PutMapping("/applications/{applicationId}/checklist")
    public ResponseEntity<VerificationChecklistDto> updateChecklist(
            Authentication auth,
            @PathVariable Long applicationId,
            @RequestBody AdminChecklistUpdateRequest request
    ) {
        User adminUser = requireAdmin(auth);
        return ResponseEntity.ok(verificationService.updateChecklist(applicationId, request, adminUser));
    }

    @PostMapping("/applications/{applicationId}/action")
    public ResponseEntity<VerificationApplicationDto> performAction(
            Authentication auth,
            @PathVariable Long applicationId,
            @RequestBody AdminVerificationActionRequest request
    ) {
        User adminUser = requireAdmin(auth);
        return ResponseEntity.ok(verificationService.performAdminAction(applicationId, request, adminUser));
    }
}
