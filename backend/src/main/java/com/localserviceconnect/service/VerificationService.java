package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB per file
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path storageRoot = Paths.get("data", "verification-docs").toAbsolutePath().normalize();

    private final VerificationApplicationRepository applicationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final VerificationChecklistRepository checklistRepository;
    private final VerificationHistoryRepository historyRepository;
    private final ProviderRepository providerRepository;
    private final NotificationService notificationService;
    private final AdminActivityLogService adminActivityLogService;

    @Autowired
    public VerificationService(
            VerificationApplicationRepository applicationRepository,
            VerificationDocumentRepository documentRepository,
            VerificationChecklistRepository checklistRepository,
            VerificationHistoryRepository historyRepository,
            ProviderRepository providerRepository,
            NotificationService notificationService,
            AdminActivityLogService adminActivityLogService
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.checklistRepository = checklistRepository;
        this.historyRepository = historyRepository;
        this.providerRepository = providerRepository;
        this.notificationService = notificationService;
        this.adminActivityLogService = adminActivityLogService;
    }

    public VerificationService(
            VerificationApplicationRepository applicationRepository,
            VerificationDocumentRepository documentRepository,
            VerificationChecklistRepository checklistRepository,
            VerificationHistoryRepository historyRepository,
            ProviderRepository providerRepository,
            NotificationService notificationService
    ) {
        this(applicationRepository, documentRepository, checklistRepository, historyRepository, providerRepository, notificationService, null);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageRoot);
            log.info("Initialized secure verification document storage at: {}", storageRoot);
        } catch (IOException e) {
            log.error("Failed to initialize verification document storage root directory", e);
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    /**
     * Get or create verification application for the provider.
     */
    @Transactional
    public VerificationApplicationDto getOrCreateApplication(Provider provider) {
        VerificationApplication app = getOrCreateEntity(provider);
        return toDto(app);
    }


    /**
     * Save draft verification data.
     */
    @Transactional
    public VerificationApplicationDto saveDraft(Provider provider, SaveVerificationDraftRequest req) {
        VerificationApplication app = getOrCreateEntity(provider);

        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.ACTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application is currently " + app.getStatus() + " and cannot be modified as a draft.");
        }

        applyRequestToApplication(app, req);
        app.setUpdatedAt(LocalDateTime.now());

        VerificationApplication saved = applicationRepository.save(app);
        return toDto(saved);
    }

    /**
     * Upload verification document or photo.
     */
    @Transactional
    public VerificationDocumentDto uploadDocument(Provider provider, String docTypeStr, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds maximum size limit of 5 MB");
        }

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(docTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type: " + docTypeStr);
        }

        VerificationApplication app = getOrCreateEntity(provider);
        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.ACTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Documents cannot be uploaded while application is in status " + app.getStatus());
        }

        // Validate MIME type & Magic Bytes
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "document"));
        if (originalFilename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename path sequence");
        }

        String validatedMimeType = validateMagicBytesAndMime(file, docType);

        try {
            Path appFolder = storageRoot.resolve("app_" + app.getId());
            Files.createDirectories(appFolder);

            String ext = getExtension(originalFilename, validatedMimeType);
            String storedFileName = docType.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path destination = appFolder.resolve(storedFileName);

            // If single-instance doc (IDENTITY_PROOF, LOCATION_PROOF, BUSINESS_REGISTRATION), delete existing
            if (docType == DocumentType.IDENTITY_PROOF || docType == DocumentType.LOCATION_PROOF || docType == DocumentType.BUSINESS_REGISTRATION) {
                List<VerificationDocument> existingDocs = documentRepository.findByApplicationIdAndDocumentType(app.getId(), docType);
                for (VerificationDocument d : existingDocs) {
                    try {
                        Files.deleteIfExists(Paths.get(d.getStoredPath()));
                    } catch (IOException ignored) {}
                    app.getDocuments().remove(d);
                    documentRepository.delete(d);
                }
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            VerificationDocument doc = new VerificationDocument();
            doc.setApplication(app);
            doc.setDocumentType(docType);
            doc.setFileName(originalFilename);
            doc.setStoredFileName(storedFileName);
            doc.setStoredPath(destination.toString());
            doc.setContentType(validatedMimeType);
            doc.setFileSizeBytes(file.getSize());
            doc.setUploadedAt(LocalDateTime.now());
            doc.setVerified(false);

            VerificationDocument savedDoc = documentRepository.save(doc);
            app.getDocuments().add(savedDoc);
            app.setUpdatedAt(LocalDateTime.now());
            applicationRepository.save(app);

            return toDocDto(savedDoc);
        } catch (IOException e) {
            log.error("Failed to store uploaded file for application {}", app.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded document");
        }
    }

    /**
     * Delete an uploaded document before submission.
     */
    @Transactional
    public void deleteDocument(Provider provider, Long documentId) {
        VerificationDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        VerificationApplication app = doc.getApplication();
        if (!app.getProvider().getId().equals(provider.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to document");
        }
        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.ACTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete documents for application in status " + app.getStatus());
        }

        try {
            Files.deleteIfExists(Paths.get(doc.getStoredPath()));
        } catch (IOException ignored) {}

        app.getDocuments().remove(doc);
        documentRepository.delete(doc);
        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);
    }

    /**
     * Submit verification application.
     */
    @Transactional
    public VerificationApplicationDto submitApplication(Provider provider, SubmitVerificationRequest req) {
        VerificationApplication app = getOrCreateEntity(provider);

        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.ACTION_REQUIRED) {
            if (app.getStatus() == ApplicationStatus.SUBMITTED || app.getStatus() == ApplicationStatus.UNDER_REVIEW) {
                return toDto(app);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application cannot be submitted while in status " + app.getStatus());
        }

        applyRequestToApplication(app, req);

        // Validation Rules:
        if (!StringUtils.hasText(app.getLegalName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full legal name is required.");
        }
        if (!StringUtils.hasText(app.getIdType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identification document type is required.");
        }
        if (!StringUtils.hasText(app.getIdNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identification number is required.");
        }
        if (!app.isPrivacyConsent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You must consent to the identity and business verification process.");
        }

        boolean hasIdentityProof = app.getDocuments().stream()
                .anyMatch(d -> d.getDocumentType() == DocumentType.IDENTITY_PROOF);
        if (!hasIdentityProof) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload your government-issued identity document.");
        }

        if (app.getProviderType() == ProviderType.BUSINESS) {
            if (!StringUtils.hasText(app.getBusinessName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business or shop name is required.");
            }
            if (!StringUtils.hasText(app.getBusinessAddress())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business address is required.");
            }
            boolean hasExteriorPhoto = app.getDocuments().stream()
                    .anyMatch(d -> d.getDocumentType() == DocumentType.SHOP_EXTERIOR);
            if (!hasExteriorPhoto) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload at least one exterior photograph of your shop or business.");
            }
            boolean hasLocationProof = app.getDocuments().stream()
                    .anyMatch(d -> d.getDocumentType() == DocumentType.LOCATION_PROOF);
            if (!hasLocationProof) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Please upload a business-location proof (ownership document, rent agreement, or approved alternative).");
            }
        }

        ApplicationStatus previousStatus = app.getStatus();
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());

        String action = previousStatus == ApplicationStatus.ACTION_REQUIRED ? "RESUBMITTED" : "SUBMITTED";
        String note = previousStatus == ApplicationStatus.ACTION_REQUIRED
                ? "Provider resubmitted corrections for requested action"
                : "Application submitted for admin review";

        VerificationHistory hist = new VerificationHistory(app, previousStatus, ApplicationStatus.SUBMITTED, provider.getUser(), action, note);
        app.getHistory().add(hist);

        // Clear action required indicators upon resubmission
        app.setActionRequiredReason(null);
        app.setActionRequiredFields(null);

        VerificationApplication saved = applicationRepository.save(app);

        // Notify provider
        notificationService.sendVerificationNotification(
                provider.getUser(),
                "SYSTEM",
                "Verification Application Submitted",
                "Your application (Ref: " + saved.getReferenceNumber() + ") has been submitted successfully and is awaiting review."
        );

        // Notify administrators of submitted verification application
        String appName = app.getBusinessName() != null ? app.getBusinessName() : app.getLegalName();
        notificationService.notifyAdmins(
                "PROVIDER_VERIFICATION_SUBMITTED",
                "Verification Application Submitted: " + appName,
                "Application " + saved.getReferenceNumber() + " for '" + appName + "' was " + action.toLowerCase() + " for administrator review.",
                "VERIFICATION_APP",
                saved.getId(),
                "/admin/providers.html?status=SUBMITTED",
                "HIGH",
                "VERIF_SUBMIT_" + saved.getId() + "_" + (saved.getUpdatedAt() != null ? saved.getUpdatedAt().toString() : "0")
        );

        return toDto(saved);
    }

    /**
     * Admin: Search and filter verification applications.
     */
    @Transactional(readOnly = true)
    public List<VerificationApplicationDto> searchApplications(String statusStr, String typeStr, String search) {
        ApplicationStatus status = null;
        if (StringUtils.hasText(statusStr)) {
            try {
                if ("PENDING".equalsIgnoreCase(statusStr.trim())) {
                    status = ApplicationStatus.SUBMITTED;
                } else {
                    status = ApplicationStatus.valueOf(statusStr.toUpperCase());
                }
            } catch (IllegalArgumentException ignored) {}
        }

        ProviderType providerType = null;
        if (StringUtils.hasText(typeStr)) {
            try {
                providerType = ProviderType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        List<VerificationApplication> list = applicationRepository.searchApplications(
                status, providerType, StringUtils.hasText(search) ? search.trim() : null
        );
        return list.stream().map(this::toDto).toList();
    }

    /**
     * Admin: Search and filter verification applications with pagination.
     */
    @Transactional(readOnly = true)
    public Page<VerificationApplicationDto> searchApplications(
            String statusStr, String typeStr, String search, Pageable pageable) {
        ApplicationStatus status = null;
        if (StringUtils.hasText(statusStr)) {
            try {
                if ("PENDING".equalsIgnoreCase(statusStr.trim())) {
                    status = ApplicationStatus.SUBMITTED;
                } else {
                    status = ApplicationStatus.valueOf(statusStr.toUpperCase());
                }
            } catch (IllegalArgumentException ignored) {}
        }

        ProviderType providerType = null;
        if (StringUtils.hasText(typeStr)) {
            try {
                providerType = ProviderType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Page<VerificationApplication> page = applicationRepository.searchApplications(
                status, providerType, StringUtils.hasText(search) ? search.trim() : null, pageable
        );
        return page.map(this::toDto);
    }

    /**
     * Admin: Get application summary counts by status.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSummaryCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s.name(), applicationRepository.countByStatus(s));
        }
        counts.put("TOTAL", applicationRepository.count());
        return counts;
    }

    /**
     * Admin: Get application by ID.
     */
    @Transactional(readOnly = true)
    public VerificationApplicationDto getApplicationById(Long applicationId) {
        VerificationApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        return toDto(app);
    }

    /**
     * Admin: Start review.
     */
    @Transactional
    public VerificationApplicationDto startReview(Long applicationId, User adminUser) {
        VerificationApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (app.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not in SUBMITTED status");
        }

        app.setStatus(ApplicationStatus.UNDER_REVIEW);
        app.setReviewedBy(adminUser);
        app.setUpdatedAt(LocalDateTime.now());

        VerificationHistory hist = new VerificationHistory(
                app, ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW,
                adminUser, "START_REVIEW", "Admin started verification review"
        );
        app.getHistory().add(hist);

        VerificationApplication saved = applicationRepository.save(app);

        notificationService.sendVerificationNotification(
                app.getProvider().getUser(),
                "SYSTEM",
                "Application Under Review",
                "An administrator has started reviewing your verification application (Ref: " + app.getReferenceNumber() + ")."
        );

        return toDto(saved);
    }

    /**
     * Admin: Update checklist items.
     */
    @Transactional
    public VerificationChecklistDto updateChecklist(Long applicationId, AdminChecklistUpdateRequest req, User adminUser) {
        VerificationApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        VerificationChecklist checklist = app.getChecklist();
        if (checklist == null) {
            checklist = new VerificationChecklist();
            checklist.setApplication(app);
            app.setChecklist(checklist);
        }

        if (req.identityDocReceived() != null) checklist.setIdentityDocReceived(req.identityDocReceived());
        if (req.identityDocReadable() != null) checklist.setIdentityDocReadable(req.identityDocReadable());
        if (req.nameAndDetailsReviewed() != null) checklist.setNameAndDetailsReviewed(req.nameAndDetailsReviewed());
        if (req.identityCheckAuthorized() != null) checklist.setIdentityCheckAuthorized(req.identityCheckAuthorized());
        if (req.businessInfoReviewed() != null) checklist.setBusinessInfoReviewed(req.businessInfoReviewed());
        if (req.shopPhotosReviewed() != null) checklist.setShopPhotosReviewed(req.shopPhotosReviewed());
        if (req.businessAddressReviewed() != null) checklist.setBusinessAddressReviewed(req.businessAddressReviewed());
        if (req.locationProofReviewed() != null) checklist.setLocationProofReviewed(req.locationProofReviewed());
        if (req.contactVerified() != null) checklist.setContactVerified(req.contactVerified());

        checklist.setLastUpdatedBy(adminUser);
        checklist.setUpdatedAt(LocalDateTime.now());

        VerificationChecklist saved = checklistRepository.save(checklist);
        return toChecklistDto(saved);
    }

    /**
     * Admin: Execute action (APPROVE, REQUEST_INFO, REJECT).
     */
    @Transactional
    public VerificationApplicationDto performAdminAction(Long applicationId, AdminVerificationActionRequest req, User adminUser) {
        VerificationApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (!StringUtils.hasText(req.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action is required");
        }

        String action = req.action().toUpperCase();
        ApplicationStatus currentStatus = app.getStatus();

        if ("START_REVIEW".equals(action)) {
            return startReview(applicationId, adminUser);
        }

        if ("APPROVE".equals(action)) {
            VerificationChecklist checklist = app.getChecklist();
            if (checklist == null || !checklist.isComplete(app.getProviderType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot approve: All mandatory verification checklist items for " + app.getProviderType() + " must be verified first.");
            }

            app.setStatus(ApplicationStatus.VERIFIED);
            app.setReviewedBy(adminUser);
            app.setReviewedAt(LocalDateTime.now());
            if (StringUtils.hasText(req.adminNotes())) app.setAdminNotes(req.adminNotes());

            // Synchronize Provider verification status to grant booking eligibility
            Provider provider = app.getProvider();
            provider.setVerificationStatus(VerificationStatus.VERIFIED);
            providerRepository.save(provider);

            VerificationHistory hist = new VerificationHistory(
                    app, currentStatus, ApplicationStatus.VERIFIED, adminUser, "APPROVED",
                    StringUtils.hasText(req.adminNotes()) ? req.adminNotes() : "Verification application approved by administrator"
            );
            app.getHistory().add(hist);

            VerificationApplication saved = applicationRepository.save(app);

            if (adminActivityLogService != null) {
                adminActivityLogService.record(
                        adminUser,
                        "PROVIDER_VERIFIED",
                        "PROVIDER",
                        provider.getId(),
                        provider.getBusinessName() != null ? provider.getBusinessName() : "Provider #" + provider.getId(),
                        "Verification application " + app.getReferenceNumber() + " approved. Partner verified for public booking.",
                        req.adminNotes(),
                        "/admin/providers.html?search=" + provider.getId()
                );
            }

            notificationService.sendVerificationNotification(
                    provider.getUser(),
                    "SYSTEM",
                    "Verification Approved! 🎉",
                    "Congratulations! Your application (Ref: " + app.getReferenceNumber() + ") has been approved. You are now a Verified Partner on Local Service Connect!"
            );

            return toDto(saved);
        }

        if ("REQUEST_INFO".equals(action)) {
            if (!StringUtils.hasText(req.reason())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please specify the explanation and required corrections.");
            }

            app.setStatus(ApplicationStatus.ACTION_REQUIRED);
            app.setActionRequiredReason(req.reason().trim());
            app.setActionRequiredFields(StringUtils.hasText(req.fields()) ? req.fields().trim() : null);
            app.setReviewedBy(adminUser);
            app.setReviewedAt(LocalDateTime.now());
            if (StringUtils.hasText(req.adminNotes())) app.setAdminNotes(req.adminNotes());

            VerificationHistory hist = new VerificationHistory(
                    app, currentStatus, ApplicationStatus.ACTION_REQUIRED, adminUser, "ACTION_REQUIRED",
                    "Corrections requested: " + req.reason() + (StringUtils.hasText(req.fields()) ? " (" + req.fields() + ")" : "")
            );
            app.getHistory().add(hist);

            VerificationApplication saved = applicationRepository.save(app);

            if (adminActivityLogService != null) {
                adminActivityLogService.record(
                        adminUser,
                        "PROVIDER_CORRECTION_REQUESTED",
                        "PROVIDER",
                        app.getProvider().getId(),
                        app.getProvider().getBusinessName() != null ? app.getProvider().getBusinessName() : "Provider #" + app.getProvider().getId(),
                        "Requested corrections for application " + app.getReferenceNumber() + ". Required fields: " + (StringUtils.hasText(req.fields()) ? req.fields() : "unspecified"),
                        req.reason(),
                        "/admin/providers.html?search=" + app.getProvider().getId()
                );
            }

            notificationService.sendVerificationNotification(
                    app.getProvider().getUser(),
                    "SYSTEM",
                    "Action Required: Verification Corrections Needed",
                    req.reason()
            );

            return toDto(saved);
        }

        if ("REJECT".equals(action)) {
            if (!StringUtils.hasText(req.reason())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please specify a rejection reason.");
            }

            app.setStatus(ApplicationStatus.REJECTED);
            app.setRejectionReason(req.reason().trim());
            app.setReviewedBy(adminUser);
            app.setReviewedAt(LocalDateTime.now());
            if (StringUtils.hasText(req.adminNotes())) app.setAdminNotes(req.adminNotes());

            Provider provider = app.getProvider();
            provider.setVerificationStatus(VerificationStatus.REJECTED);
            providerRepository.save(provider);

            VerificationHistory hist = new VerificationHistory(
                    app, currentStatus, ApplicationStatus.REJECTED, adminUser, "REJECTED",
                    "Application rejected: " + req.reason()
            );
            app.getHistory().add(hist);

            VerificationApplication saved = applicationRepository.save(app);

            if (adminActivityLogService != null) {
                adminActivityLogService.record(
                        adminUser,
                        "PROVIDER_REJECTED",
                        "PROVIDER",
                        provider.getId(),
                        provider.getBusinessName() != null ? provider.getBusinessName() : "Provider #" + provider.getId(),
                        "Verification application " + app.getReferenceNumber() + " rejected.",
                        req.reason(),
                        "/admin/providers.html?search=" + provider.getId()
                );
            }

            notificationService.sendVerificationNotification(
                    provider.getUser(),
                    "SYSTEM",
                    "Verification Application Rejected",
                    "Your verification application was rejected: " + req.reason()
            );

            return toDto(saved);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported action: " + action);
    }

    /**
     * Secure file access: Admins or the owning provider only!
     */
    @Transactional(readOnly = true)
    public Resource getDocumentResource(Long documentId, User currentUser) {
        VerificationDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = doc.getApplication().getProvider().getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            log.warn("Unauthorized attempt to access document {} by user {}", documentId, currentUser.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to requested document");
        }

        try {
            Path filePath = Paths.get(doc.getStoredPath());
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found on disk");
            }
            return resource;
        } catch (Exception e) {
            log.error("Error reading document file: {}", doc.getStoredPath(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading document");
        }
    }

    @Transactional(readOnly = true)
    public VerificationDocument getDocumentMetadata(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private VerificationApplication getOrCreateEntity(Provider provider) {
        return applicationRepository.findTopByProviderIdOrderByIdDesc(provider.getId())
                .orElseGet(() -> {
                    VerificationApplication app = new VerificationApplication();
                    app.setProvider(provider);
                    app.setReferenceNumber(generateReferenceNumber());
                    app.setProviderType(ProviderType.INDIVIDUAL);
                    app.setStatus(ApplicationStatus.DRAFT);
                    app.setBusinessName(provider.getBusinessName());
                    app.setBusinessDescription(provider.getDescription());
                    app.setServiceAddress(provider.getFormattedAddress());
                    app.setCreatedAt(LocalDateTime.now());
                    app.setUpdatedAt(LocalDateTime.now());

                    VerificationApplication saved = applicationRepository.save(app);

                    VerificationChecklist checklist = new VerificationChecklist();
                    checklist.setApplication(saved);
                    checklist.setUpdatedAt(LocalDateTime.now());
                    saved.setChecklist(checklistRepository.save(checklist));

                    VerificationHistory history = new VerificationHistory(
                            saved, null, ApplicationStatus.DRAFT, provider.getUser(),
                            "CREATED_DRAFT", "Started verification application draft"
                    );
                    historyRepository.save(history);
                    saved.getHistory().add(history);

                    return saved;
                });
    }

    private void applyRequestToApplication(VerificationApplication app, Object reqObj) {
        if (reqObj instanceof SaveVerificationDraftRequest req) {
            if (StringUtils.hasText(req.providerType())) {
                try {
                    app.setProviderType(ProviderType.valueOf(req.providerType().toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
            if (req.legalName() != null) app.setLegalName(req.legalName().trim());
            if (req.dateOfBirth() != null) app.setDateOfBirth(req.dateOfBirth());
            if (req.idType() != null) app.setIdType(req.idType().trim());
            if (req.idNumber() != null) app.setIdNumber(req.idNumber().trim());
            if (req.privacyConsent() != null) app.setPrivacyConsent(req.privacyConsent());
            if (req.serviceAddress() != null) app.setServiceAddress(req.serviceAddress().trim());
            if (req.city() != null) app.setCity(req.city().trim());
            if (req.locality() != null) app.setLocality(req.locality().trim());
            if (req.pincode() != null) app.setPincode(req.pincode().trim());
            if (req.businessName() != null) app.setBusinessName(req.businessName().trim());
            if (req.businessType() != null) app.setBusinessType(req.businessType().trim());
            if (req.businessAddress() != null) app.setBusinessAddress(req.businessAddress().trim());
            if (req.businessPhone() != null) app.setBusinessPhone(req.businessPhone().trim());
            if (req.businessDescription() != null) app.setBusinessDescription(req.businessDescription().trim());
            if (req.operatingHours() != null) app.setOperatingHours(req.operatingHours().trim());
            if (req.servicesOffered() != null) app.setServicesOffered(req.servicesOffered().trim());
            if (req.serviceAreas() != null) app.setServiceAreas(req.serviceAreas().trim());
            if (req.locationProofType() != null) app.setLocationProofType(req.locationProofType().trim());
        } else if (reqObj instanceof SubmitVerificationRequest req) {
            if (StringUtils.hasText(req.providerType())) {
                try {
                    app.setProviderType(ProviderType.valueOf(req.providerType().toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
            if (req.legalName() != null) app.setLegalName(req.legalName().trim());
            if (req.dateOfBirth() != null) app.setDateOfBirth(req.dateOfBirth());
            if (req.idType() != null) app.setIdType(req.idType().trim());
            if (req.idNumber() != null) app.setIdNumber(req.idNumber().trim());
            if (req.privacyConsent() != null) app.setPrivacyConsent(req.privacyConsent());
            if (req.serviceAddress() != null) app.setServiceAddress(req.serviceAddress().trim());
            if (req.city() != null) app.setCity(req.city().trim());
            if (req.locality() != null) app.setLocality(req.locality().trim());
            if (req.pincode() != null) app.setPincode(req.pincode().trim());
            if (req.businessName() != null) app.setBusinessName(req.businessName().trim());
            if (req.businessType() != null) app.setBusinessType(req.businessType().trim());
            if (req.businessAddress() != null) app.setBusinessAddress(req.businessAddress().trim());
            if (req.businessPhone() != null) app.setBusinessPhone(req.businessPhone().trim());
            if (req.businessDescription() != null) app.setBusinessDescription(req.businessDescription().trim());
            if (req.operatingHours() != null) app.setOperatingHours(req.operatingHours().trim());
            if (req.servicesOffered() != null) app.setServicesOffered(req.servicesOffered().trim());
            if (req.serviceAreas() != null) app.setServiceAreas(req.serviceAreas().trim());
            if (req.locationProofType() != null) app.setLocationProofType(req.locationProofType().trim());
        }
    }

    private String validateMagicBytesAndMime(MultipartFile file, DocumentType docType) {
        byte[] header = new byte[16];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            if (read < 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty or corrupted");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to inspect file bytes");
        }

        // Check JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // Check PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // Check WebP: RIFF ... WEBP
        if ((header[0] & 0xFF) == 0x52 && (header[1] & 0xFF) == 0x49 &&
            (header[2] & 0xFF) == 0x46 && (header[3] & 0xFF) == 0x46 &&
            (header[8] & 0xFF) == 0x57 && (header[9] & 0xFF) == 0x45 &&
            (header[10] & 0xFF) == 0x42 && (header[11] & 0xFF) == 0x50) {
            return "image/webp";
        }

        // Check PDF: %PDF- (25 50 44 46)
        if ((header[0] & 0xFF) == 0x25 && (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x44 && (header[3] & 0xFF) == 0x46) {
            if (docType == DocumentType.SHOP_EXTERIOR || docType == DocumentType.SHOP_SIGNBOARD || docType == DocumentType.SHOP_INTERIOR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photographs must be JPEG, PNG, or WebP images, not PDF documents.");
            }
            return "application/pdf";
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file format. Please upload JPEG, PNG, WebP or PDF.");
    }

    private String getExtension(String filename, String mimeType) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot).toLowerCase();
            if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp") || ext.equals(".pdf")) {
                return ext;
            }
        }
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }

    private String generateReferenceNumber() {
        int year = Year.now().getValue();
        int rand = 10000 + RANDOM.nextInt(90000);
        String ref = "APP-" + year + "-" + rand;
        while (applicationRepository.existsByReferenceNumber(ref)) {
            rand = 10000 + RANDOM.nextInt(90000);
            ref = "APP-" + year + "-" + rand;
        }
        return ref;
    }

    private String maskIdNumber(String idNum) {
        if (!StringUtils.hasText(idNum)) return "";
        String trimmed = idNum.trim();
        if (trimmed.length() <= 4) return "•••• " + trimmed;
        int visible = 4;
        String tail = trimmed.substring(trimmed.length() - visible);
        return "•••• •••• " + tail;
    }

    public VerificationApplicationDto toDto(VerificationApplication a) {
        if (a == null) return null;
        Provider p = a.getProvider();
        String ownerName = null;
        String email = null;
        String phone = null;
        if (p != null) {
            try {
                User u = p.getUser();
                if (u != null) {
                    ownerName = u.getName();
                    email = u.getEmail();
                    phone = u.getPhone();
                }
            } catch (Exception ignored) {}
        }

        List<VerificationDocumentDto> docDtos = a.getDocuments() != null
                ? a.getDocuments().stream().map(this::toDocDto).toList()
                : List.of();

        VerificationChecklistDto checkDto = a.getChecklist() != null
                ? toChecklistDto(a.getChecklist())
                : null;

        List<VerificationHistoryDto> histDtos = a.getHistory() != null
                ? a.getHistory().stream().map(this::toHistDto).toList()
                : List.of();

        String reviewedByName = null;
        try {
            if (a.getReviewedBy() != null) {
                reviewedByName = a.getReviewedBy().getName();
            }
        } catch (Exception ignored) {}

        return new VerificationApplicationDto(
                a.getId(),
                p != null ? p.getId() : null,
                p != null ? p.getBusinessName() : null,
                ownerName,
                email,
                phone,
                a.getReferenceNumber(),
                a.getProviderType() != null ? a.getProviderType().name() : null,
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getLegalName(),
                a.getDateOfBirth(),
                a.getIdType(),
                maskIdNumber(a.getIdNumber()),
                a.isPrivacyConsent(),
                a.getServiceAddress(),
                a.getCity(),
                a.getLocality(),
                a.getPincode(),
                a.getBusinessName(),
                a.getBusinessType(),
                a.getBusinessAddress(),
                a.getBusinessPhone(),
                a.getBusinessDescription(),
                a.getOperatingHours(),
                a.getServicesOffered(),
                a.getServiceAreas(),
                a.getLocationProofType(),
                a.getAdminNotes(),
                a.getActionRequiredReason(),
                a.getActionRequiredFields(),
                a.getRejectionReason(),
                reviewedByName,
                a.getSubmittedAt(),
                a.getReviewedAt(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                docDtos,
                checkDto,
                histDtos
        );
    }

    public VerificationDocumentDto toDocDto(VerificationDocument d) {
        if (d == null) return null;
        return new VerificationDocumentDto(
                d.getId(),
                d.getApplication() != null ? d.getApplication().getId() : null,
                d.getDocumentType() != null ? d.getDocumentType().name() : null,
                d.getFileName(),
                d.getContentType(),
                d.getFileSizeBytes(),
                d.getUploadedAt(),
                d.isVerified(),
                d.getAdminComment(),
                "/api/verification/documents/" + d.getId() + "/file"
        );
    }

    public VerificationChecklistDto toChecklistDto(VerificationChecklist c) {
        if (c == null) return null;
        ProviderType pType = c.getApplication() != null ? c.getApplication().getProviderType() : ProviderType.INDIVIDUAL;
        String updatedByName = null;
        try {
            if (c.getLastUpdatedBy() != null) {
                updatedByName = c.getLastUpdatedBy().getName();
            }
        } catch (Exception ignored) {}
        return new VerificationChecklistDto(
                c.getId(),
                c.getApplication() != null ? c.getApplication().getId() : null,
                c.isIdentityDocReceived(),
                c.isIdentityDocReadable(),
                c.isNameAndDetailsReviewed(),
                c.isIdentityCheckAuthorized(),
                c.isBusinessInfoReviewed(),
                c.isShopPhotosReviewed(),
                c.isBusinessAddressReviewed(),
                c.isLocationProofReviewed(),
                c.isContactVerified(),
                c.isComplete(pType),
                updatedByName,
                c.getUpdatedAt()
        );
    }

    public VerificationHistoryDto toHistDto(VerificationHistory h) {
        if (h == null) return null;
        String actorName = h.getActorName();
        String actorRole = h.getActorRole();
        if (actorName == null) {
            try {
                User act = h.getActor();
                if (act != null) {
                    actorName = act.getName();
                    actorRole = act.getRole() != null ? act.getRole().name() : "USER";
                }
            } catch (Exception ignored) {}
        }
        return new VerificationHistoryDto(
                h.getId(),
                h.getApplication() != null ? h.getApplication().getId() : null,
                h.getFromStatus() != null ? h.getFromStatus().name() : null,
                h.getToStatus() != null ? h.getToStatus().name() : null,
                actorName != null ? actorName : "System",
                actorRole != null ? actorRole : "SYSTEM",
                h.getAction(),
                h.getNotes(),
                h.getTimestamp()
        );
    }
}
