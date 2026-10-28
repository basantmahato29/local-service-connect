package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ApplicationStatus;
import com.localserviceconnect.entity.ProviderType;
import com.localserviceconnect.entity.VerificationApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VerificationApplicationRepository extends JpaRepository<VerificationApplication, Long> {

    Optional<VerificationApplication> findTopByProviderIdOrderByIdDesc(Long providerId);
    List<VerificationApplication> findByProviderId(Long providerId);

    Optional<VerificationApplication> findByReferenceNumber(String referenceNumber);

    boolean existsByReferenceNumber(String referenceNumber);

    long countByStatus(ApplicationStatus status);

    List<VerificationApplication> findByStatusOrderByIdDesc(ApplicationStatus status);

    List<VerificationApplication> findAllByOrderByIdDesc();

    @Query("""
        SELECT a FROM VerificationApplication a
        LEFT JOIN a.provider p
        LEFT JOIN p.user u
        WHERE (:status IS NULL OR a.status = :status)
          AND (:providerType IS NULL OR a.providerType = :providerType)
          AND (
               :search IS NULL OR :search = '' OR
               lower(a.referenceNumber) LIKE lower(concat('%', :search, '%')) OR
               lower(a.legalName) LIKE lower(concat('%', :search, '%')) OR
               lower(a.businessName) LIKE lower(concat('%', :search, '%')) OR
               lower(p.businessName) LIKE lower(concat('%', :search, '%')) OR
               lower(u.name) LIKE lower(concat('%', :search, '%')) OR
               lower(u.email) LIKE lower(concat('%', :search, '%')) OR
               lower(u.phone) LIKE lower(concat('%', :search, '%'))
          )
        ORDER BY a.updatedAt DESC, a.id DESC
    """)
    List<VerificationApplication> searchApplications(
            @Param("status") ApplicationStatus status,
            @Param("providerType") ProviderType providerType,
            @Param("search") String search
    );

    @Query(value = """
        SELECT a FROM VerificationApplication a
        LEFT JOIN a.provider p
        LEFT JOIN p.user u
        WHERE (:status IS NULL OR a.status = :status)
          AND (:providerType IS NULL OR a.providerType = :providerType)
          AND (
               :search IS NULL OR :search = '' OR
               lower(a.referenceNumber) LIKE lower(concat('%', :search, '%')) OR
               lower(a.legalName) LIKE lower(concat('%', :search, '%')) OR
               lower(a.businessName) LIKE lower(concat('%', :search, '%')) OR
               lower(p.businessName) LIKE lower(concat('%', :search, '%')) OR
               lower(u.name) LIKE lower(concat('%', :search, '%')) OR
               lower(u.email) LIKE lower(concat('%', :search, '%')) OR
               lower(u.phone) LIKE lower(concat('%', :search, '%'))
          )
    """,
    countQuery = """
        SELECT count(a) FROM VerificationApplication a
        LEFT JOIN a.provider p
        LEFT JOIN p.user u
        WHERE (:status IS NULL OR a.status = :status)
          AND (:providerType IS NULL OR a.providerType = :providerType)
          AND (
               :search IS NULL OR :search = '' OR
               lower(a.referenceNumber) LIKE lower(concat('%', :search, '%')) OR
               lower(a.legalName) LIKE lower(concat('%', :search, '%')) OR
               lower(a.businessName) LIKE lower(concat('%', :search, '%')) OR
               lower(p.businessName) LIKE lower(concat('%', :search, '%')) OR
               lower(u.name) LIKE lower(concat('%', :search, '%')) OR
               lower(u.email) LIKE lower(concat('%', :search, '%')) OR
               lower(u.phone) LIKE lower(concat('%', :search, '%'))
          )
    """)
    Page<VerificationApplication> searchApplications(
            @Param("status") ApplicationStatus status,
            @Param("providerType") ProviderType providerType,
            @Param("search") String search,
            Pageable pageable
    );
}
