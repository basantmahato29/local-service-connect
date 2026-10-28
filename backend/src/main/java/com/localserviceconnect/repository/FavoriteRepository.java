package com.localserviceconnect.repository;

import com.localserviceconnect.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByCustomerIdAndProviderId(Long customerId, Long providerId);

    boolean existsByCustomerIdAndProviderId(Long customerId, Long providerId);

    @Query("SELECT f FROM Favorite f JOIN FETCH f.provider p LEFT JOIN FETCH p.user u WHERE f.customer.id = :customerId ORDER BY f.createdAt DESC")
    List<Favorite> findByCustomerIdWithProvider(@Param("customerId") Long customerId);

    @Query("SELECT f.provider.id FROM Favorite f WHERE f.customer.id = :customerId")
    List<Long> findProviderIdsByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT f FROM Favorite f JOIN FETCH f.customer c JOIN FETCH c.user u WHERE f.provider.id = :providerId AND f.notifyOnAvailable = true")
    List<Favorite> findByProviderIdAndNotifyOnAvailableTrue(@Param("providerId") Long providerId);

    void deleteByCustomerIdAndProviderId(Long customerId, Long providerId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Favorite f WHERE f.provider.id = :providerId")
    void deleteByProviderId(@Param("providerId") Long providerId);

    long countByCustomerId(Long customerId);
}
