package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.AdminActivityLogDto;
import com.localserviceconnect.entity.AdminActivityLog;
import com.localserviceconnect.entity.User;
import com.localserviceconnect.repository.AdminActivityLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AdminActivityLogService {
    private static final Logger log = LoggerFactory.getLogger(AdminActivityLogService.class);

    private final AdminActivityLogRepository repository;

    public AdminActivityLogService(AdminActivityLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Record an administrative action into the audit trail.
     * Uses REQUIRES_NEW propagation where possible or catches any logging exception
     * to ensure audit logging failures never break the primary business transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(User actor, String actionType, String entityType, Long entityId,
                       String entityIdentifier, String details, String reason, String targetUrl,
                       HttpServletRequest request) {
        try {
            Long actorId = actor != null ? actor.getId() : null;
            String actorName = actor != null ? actor.getName() : "System";
            String actorEmail = actor != null ? actor.getEmail() : "system@localserviceconnect.com";
            String actorRole = actor != null && actor.getRole() != null ? actor.getRole().name() : "ADMIN";
            String ip = extractClientIp(request);

            AdminActivityLog entry = new AdminActivityLog(
                    actorId, actorName, actorEmail, actorRole,
                    actionType, entityType, entityId, entityIdentifier,
                    details, reason, "SUCCESS", ip, targetUrl
            );

            repository.save(entry);
            log.info("Admin audit: {} by {} ({}) on {} #{} [{}]",
                    actionType, actorEmail, actorRole, entityType, entityId, entityIdentifier);
        } catch (Exception ex) {
            log.error("Failed to record admin activity log: {}", ex.getMessage(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(User actor, String actionType, String entityType, Long entityId,
                       String entityIdentifier, String details, String reason, String targetUrl) {
        record(actor, actionType, entityType, entityId, entityIdentifier, details, reason, targetUrl, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminActivityLogDto> getLogsPaged(String search, String actionType, String entityType,
                                                  String actorEmail, LocalDate fromDate, LocalDate toDate,
                                                  int page, int size, String sortBy, String sortDir) {
        Specification<AdminActivityLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("actionType")), term),
                        cb.like(cb.lower(root.get("actorName")), term),
                        cb.like(cb.lower(root.get("actorEmail")), term),
                        cb.like(cb.lower(root.get("entityIdentifier")), term),
                        cb.like(cb.lower(root.get("details")), term),
                        cb.like(cb.lower(root.get("reason")), term)
                );
                predicates.add(searchPredicate);
            }

            if (StringUtils.hasText(actionType) && !"ALL".equalsIgnoreCase(actionType)) {
                predicates.add(cb.equal(root.get("actionType"), actionType.trim()));
            }

            if (StringUtils.hasText(entityType) && !"ALL".equalsIgnoreCase(entityType)) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }

            if (StringUtils.hasText(actorEmail)) {
                predicates.add(cb.equal(cb.lower(root.get("actorEmail")), actorEmail.trim().toLowerCase()));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromDate.atStartOfDay()));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toDate.atTime(LocalTime.MAX)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "timestamp";
        if ("id".equalsIgnoreCase(sortBy) || "actionType".equalsIgnoreCase(sortBy) || "actorName".equalsIgnoreCase(sortBy)) {
            field = sortBy;
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(dir, field));
        return repository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<String> getActionTypes() {
        return repository.findDistinctActionTypes();
    }

    @Transactional(readOnly = true)
    public List<String> getEntityTypes() {
        return repository.findDistinctEntityTypes();
    }

    @Transactional(readOnly = true)
    public Optional<AdminActivityLogDto> getLogById(Long id) {
        return repository.findById(id).map(this::toDto);
    }

    private AdminActivityLogDto toDto(AdminActivityLog l) {
        return new AdminActivityLogDto(
                l.getId(),
                l.getActorId(),
                l.getActorName(),
                l.getActorEmail(),
                l.getActorRole(),
                l.getActionType(),
                l.getEntityType(),
                l.getEntityId(),
                l.getEntityIdentifier(),
                l.getDetails(),
                l.getReason(),
                l.getResult(),
                l.getIpAddress(),
                l.getTargetUrl(),
                l.getTimestamp()
        );
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xf = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xf)) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

