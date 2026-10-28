package com.localserviceconnect.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_checklists")
public class VerificationChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private VerificationApplication application;

    // IDENTITY CHECKS
    @Column(name = "identity_doc_received", nullable = false)
    private boolean identityDocReceived = false;

    @Column(name = "identity_doc_readable", nullable = false)
    private boolean identityDocReadable = false;

    @Column(name = "name_and_details_reviewed", nullable = false)
    private boolean nameAndDetailsReviewed = false;

    @Column(name = "identity_check_authorized", nullable = false)
    private boolean identityCheckAuthorized = false;

    // BUSINESS CHECKS
    @Column(name = "business_info_reviewed", nullable = false)
    private boolean businessInfoReviewed = false;

    @Column(name = "shop_photos_reviewed", nullable = false)
    private boolean shopPhotosReviewed = false;

    @Column(name = "business_address_reviewed", nullable = false)
    private boolean businessAddressReviewed = false;

    @Column(name = "location_proof_reviewed", nullable = false)
    private boolean locationProofReviewed = false;

    // CONTACT CHECKS
    @Column(name = "contact_verified", nullable = false)
    private boolean contactVerified = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_updated_by_user_id")
    private User lastUpdatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public VerificationChecklist() {}

    /**
     * Determines whether all mandatory checklist items are complete based on provider type.
     */
    public boolean isComplete(ProviderType providerType) {
        boolean idComplete = identityDocReceived && identityDocReadable && nameAndDetailsReviewed && identityCheckAuthorized;
        boolean contactComplete = contactVerified;

        if (providerType == ProviderType.INDIVIDUAL) {
            return idComplete && contactComplete;
        } else {
            boolean busComplete = businessInfoReviewed && shopPhotosReviewed && businessAddressReviewed && locationProofReviewed;
            return idComplete && contactComplete && busComplete;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public VerificationApplication getApplication() { return application; }
    public void setApplication(VerificationApplication application) { this.application = application; }

    public boolean isIdentityDocReceived() { return identityDocReceived; }
    public void setIdentityDocReceived(boolean identityDocReceived) { this.identityDocReceived = identityDocReceived; }

    public boolean isIdentityDocReadable() { return identityDocReadable; }
    public void setIdentityDocReadable(boolean identityDocReadable) { this.identityDocReadable = identityDocReadable; }

    public boolean isNameAndDetailsReviewed() { return nameAndDetailsReviewed; }
    public void setNameAndDetailsReviewed(boolean nameAndDetailsReviewed) { this.nameAndDetailsReviewed = nameAndDetailsReviewed; }

    public boolean isIdentityCheckAuthorized() { return identityCheckAuthorized; }
    public void setIdentityCheckAuthorized(boolean identityCheckAuthorized) { this.identityCheckAuthorized = identityCheckAuthorized; }

    public boolean isBusinessInfoReviewed() { return businessInfoReviewed; }
    public void setBusinessInfoReviewed(boolean businessInfoReviewed) { this.businessInfoReviewed = businessInfoReviewed; }

    public boolean isShopPhotosReviewed() { return shopPhotosReviewed; }
    public void setShopPhotosReviewed(boolean shopPhotosReviewed) { this.shopPhotosReviewed = shopPhotosReviewed; }

    public boolean isBusinessAddressReviewed() { return businessAddressReviewed; }
    public void setBusinessAddressReviewed(boolean businessAddressReviewed) { this.businessAddressReviewed = businessAddressReviewed; }

    public boolean isLocationProofReviewed() { return locationProofReviewed; }
    public void setLocationProofReviewed(boolean locationProofReviewed) { this.locationProofReviewed = locationProofReviewed; }

    public boolean isContactVerified() { return contactVerified; }
    public void setContactVerified(boolean contactVerified) { this.contactVerified = contactVerified; }

    public User getLastUpdatedBy() { return lastUpdatedBy; }
    public void setLastUpdatedBy(User lastUpdatedBy) { this.lastUpdatedBy = lastUpdatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
