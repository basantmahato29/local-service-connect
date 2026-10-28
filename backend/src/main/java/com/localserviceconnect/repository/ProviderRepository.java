package com.localserviceconnect.repository;

import com.localserviceconnect.entity.Provider;
import com.localserviceconnect.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long>, JpaSpecificationExecutor<Provider> {
    Optional<Provider> findByUserId(Long userId);
    Optional<Provider> findByUserEmail(String email);

    long countByVerificationStatus(VerificationStatus status);
    List<Provider> findByVerificationStatusOrderByIdDesc(VerificationStatus status);

    /** Verified providers, optionally filtered by service (0 = any) and area ('' = any, lowercase). */
    @Query("""
        select p from Provider p
        where p.verificationStatus = com.localserviceconnect.entity.VerificationStatus.VERIFIED
          and (:serviceId = 0L or exists (select 1 from p.services s where s.id = :serviceId))
          and (:area = '' or exists (select 1 from ProviderArea a where a.providerId = p.id and (lower(a.area) = :area or lower(a.city) = :area)))
        order by p.rating desc, p.reviewCount desc""")
    List<Provider> search(@Param("serviceId") long serviceId, @Param("area") String area);

    /**
     * Matching engine. Verified providers who offer the (active) service and serve the area (lowercase).
     * For Quick Service (quick = true) the provider must also be online and not currently busy.
     * Ranked by rating, then number of reviews.
     */
    @Query("""
        select p from Provider p
        where p.verificationStatus = com.localserviceconnect.entity.VerificationStatus.VERIFIED
          and exists (select 1 from p.services s where s.id = :serviceId
                      and s.status = com.localserviceconnect.entity.ServiceStatus.ACTIVE)
          and (:area = '' or exists (select 1 from ProviderArea a where a.providerId = p.id and (lower(a.area) = :area or lower(a.city) = :area)))
          and (:quick = false or (p.availability = true and not exists (
                select 1 from Booking b where b.provider = p and (
                  b.status in (com.localserviceconnect.entity.BookingStatus.ON_THE_WAY,
                               com.localserviceconnect.entity.BookingStatus.SERVICE_STARTED)
                  or (b.status = com.localserviceconnect.entity.BookingStatus.ACCEPTED
                      and b.bookingType = com.localserviceconnect.entity.BookingType.QUICK_SERVICE)))))
        order by p.rating desc, p.reviewCount desc, p.id""")
    List<Provider> match(@Param("serviceId") long serviceId, @Param("area") String area, @Param("quick") boolean quick);

    /**
     * Finds candidate verified providers for a given service (or any service if serviceId = 0).
     * For Quick Service, ensures the provider is available and not currently occupied.
     */
    @Query("""
        select p from Provider p
        where p.verificationStatus = com.localserviceconnect.entity.VerificationStatus.VERIFIED
          and (:serviceId = 0L or exists (select 1 from p.services s where s.id = :serviceId
                      and s.status = com.localserviceconnect.entity.ServiceStatus.ACTIVE))
          and (:quick = false or (p.availability = true and not exists (
                select 1 from Booking b where b.provider = p and (
                  b.status in (com.localserviceconnect.entity.BookingStatus.ON_THE_WAY,
                               com.localserviceconnect.entity.BookingStatus.SERVICE_STARTED)
                  or (b.status = com.localserviceconnect.entity.BookingStatus.ACCEPTED
                      and b.bookingType = com.localserviceconnect.entity.BookingType.QUICK_SERVICE)))))
        order by p.rating desc, p.reviewCount desc""")
    List<Provider> findCandidates(@Param("serviceId") long serviceId, @Param("quick") boolean quick);
}
