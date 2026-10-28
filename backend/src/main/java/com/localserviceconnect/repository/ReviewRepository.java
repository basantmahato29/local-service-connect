package com.localserviceconnect.repository;

import com.localserviceconnect.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByBookingId(Long bookingId);
    Optional<Review> findByBookingId(Long bookingId);
    List<Review> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<Review> findByCustomerUserEmailOrderByCreatedAtDesc(String email);
    long countByProviderId(Long providerId);

    @Query("select coalesce(avg(r.rating), 0.0) from Review r where r.provider.id = :providerId")
    Double averageFor(@Param("providerId") Long providerId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByProviderId(Long providerId);
}
