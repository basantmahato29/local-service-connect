package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.ServiceStatus;
import com.localserviceconnect.entity.ServiceType;
import com.localserviceconnect.entity.SubService;
import com.localserviceconnect.repository.BookingRepository;
import com.localserviceconnect.repository.ServiceTypeRepository;
import com.localserviceconnect.repository.SubServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CatalogService {

    private final ServiceTypeRepository serviceTypes;
    private final SubServiceRepository subServices;
    private final BookingRepository bookings;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final AdminActivityLogService adminActivityLogService;

    @Autowired
    public CatalogService(ServiceTypeRepository serviceTypes,
                          SubServiceRepository subServices,
                          BookingRepository bookings,
                          org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                          AdminActivityLogService adminActivityLogService) {
        this.serviceTypes = serviceTypes;
        this.subServices = subServices;
        this.bookings = bookings;
        this.jdbcTemplate = jdbcTemplate;
        this.adminActivityLogService = adminActivityLogService;
    }

    public CatalogService(ServiceTypeRepository serviceTypes,
                          SubServiceRepository subServices,
                          BookingRepository bookings,
                          org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this(serviceTypes, subServices, bookings, jdbcTemplate, null);
    }

    public static SubServiceDto toSubDto(SubService s) {
        if (s == null) return null;
        return new SubServiceDto(
                s.getId(),
                s.getService() != null ? s.getService().getId() : null,
                s.getService() != null ? s.getService().getName() : null,
                s.getName(),
                s.getDescription(),
                s.getBasePrice(),
                s.getDuration(),
                s.getImageUrl(),
                s.isEnabled(),
                s.getDisplayOrder()
        );
    }

    public static ServiceDto toDto(ServiceType s) {
        return new ServiceDto(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getIcon(),
                s.getStatus().name()
        );
    }

    public ServiceDto toDtoWithSubServices(ServiceType s) {
        List<SubServiceDto> subs = subServices.findByServiceIdAndEnabledTrueOrderByDisplayOrderAscNameAsc(s.getId())
                .stream().map(CatalogService::toSubDto).toList();
        return new ServiceDto(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getIcon(),
                s.getStatus().name(),
                subs
        );
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> listActive() {
        return serviceTypes.findByStatusOrderByName(ServiceStatus.ACTIVE)
                .stream()
                .map(this::toDtoWithSubServices)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> searchActive(String query) {
        if (query == null || query.isBlank()) {
            return listActive();
        }
        String q = query.trim().toLowerCase();
        return serviceTypes.findByStatusOrderByName(ServiceStatus.ACTIVE)
                .stream()
                .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(q))
                          || (s.getDescription() != null && s.getDescription().toLowerCase().contains(q)))
                .map(this::toDtoWithSubServices)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> listAll() {
        return serviceTypes.findAllByOrderByName()
                .stream()
                .map(this::toDtoWithSubServices)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceDto get(Long id) {
        return serviceTypes.findById(id)
                .map(this::toDtoWithSubServices)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    @Transactional
    public ServiceDto create(ServiceCreateRequest req) {
        String name = req.name() != null ? req.name().trim() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
        }
        if (serviceTypes.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service with this name already exists");
        }
        ServiceType s = new ServiceType();
        s.setName(name);
        s.setDescription(req.description());
        s.setIcon(req.icon() != null ? req.icon().trim() : "bi-gear");
        s.setStatus(ServiceStatus.ACTIVE);
        ServiceType saved = serviceTypes.save(s);
        if (adminActivityLogService != null) {
            adminActivityLogService.record(null, "SERVICE_CREATED", "SERVICE", saved.getId(), saved.getName(), "Created canonical service category: " + saved.getName(), null, "/admin/services.html");
        }
        return toDto(saved);
    }

    @Transactional
    public ServiceDto update(Long id, ServiceUpdateRequest req) {
        ServiceType s = serviceTypes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        String name = req.name() != null ? req.name().trim() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name cannot be empty");
        }
        if (serviceTypes.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another service with this name already exists");
        }
        s.setName(name);
        s.setDescription(req.description());
        if (req.icon() != null && !req.icon().isBlank()) {
            s.setIcon(req.icon().trim());
        }
        if (req.status() != null) {
            try {
                s.setStatus(ServiceStatus.valueOf(req.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid service status: " + req.status());
            }
        }
        ServiceType saved = serviceTypes.save(s);
        if (adminActivityLogService != null) {
            adminActivityLogService.record(null, "SERVICE_UPDATED", "SERVICE", saved.getId(), saved.getName(), "Updated canonical service category: " + saved.getName() + " (status=" + saved.getStatus() + ")", null, "/admin/services.html");
        }
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        ServiceType s = serviceTypes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        if (bookings.existsByServiceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This service has bookings and cannot be deleted. Disable it instead.");
        }
        if (jdbcTemplate != null) {
            jdbcTemplate.update("DELETE FROM provider_services WHERE service_id = ?", id);
            jdbcTemplate.update("DELETE FROM sub_services WHERE service_id = ?", id);
        }
        String serviceName = s.getName();
        serviceTypes.delete(s);
        if (adminActivityLogService != null) {
            adminActivityLogService.record(null, "SERVICE_DELETED", "SERVICE", id, serviceName, "Deleted canonical service category: " + serviceName + " (#" + id + ")", null, "/admin/services.html");
        }
    }

    // --- Subservice CRUD & Retrieval Operations ---

    @Transactional(readOnly = true)
    public List<SubServiceDto> listSubServices(Long serviceId, boolean activeOnly) {
        if (!serviceTypes.existsById(serviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent service not found");
        }
        var list = activeOnly 
                ? subServices.findByServiceIdAndEnabledTrueOrderByDisplayOrderAscNameAsc(serviceId)
                : subServices.findByServiceIdOrderByDisplayOrderAscNameAsc(serviceId);
        return list.stream().map(CatalogService::toSubDto).toList();
    }

    @Transactional(readOnly = true)
    public SubServiceDto getSubService(Long id) {
        return subServices.findById(id)
                .map(CatalogService::toSubDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subservice not found"));
    }

    @Transactional
    public SubServiceDto createSubService(SubServiceCreateRequest req) {
        ServiceType parent = serviceTypes.findById(req.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent service not found"));
        String name = req.name() != null ? req.name().trim() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subservice name is required");
        }
        if (subServices.existsByServiceIdAndNameIgnoreCase(parent.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A subservice with this name already exists under " + parent.getName());
        }
        SubService sub = new SubService();
        sub.setService(parent);
        sub.setName(name);
        sub.setDescription(req.description());
        sub.setBasePrice(req.basePrice());
        sub.setDuration(req.duration());
        sub.setImageUrl(req.imageUrl());
        sub.setEnabled(req.enabled() != null ? req.enabled() : true);
        sub.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : 0);
        SubService saved = subServices.save(sub);
        if (adminActivityLogService != null) {
            adminActivityLogService.record(null, "SUBSERVICE_CREATED", "SUBSERVICE", saved.getId(), saved.getName(), "Created subservice: " + saved.getName() + " under category " + parent.getName(), null, "/admin/services.html");
        }
        return toSubDto(saved);
    }

    @Transactional
    public SubServiceDto updateSubService(Long id, SubServiceUpdateRequest req) {
        SubService sub = subServices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subservice not found"));
        if (req.name() != null && !req.name().isBlank()) {
            String name = req.name().trim();
            if (subServices.existsByServiceIdAndNameIgnoreCaseAndIdNot(sub.getService().getId(), name, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another subservice with this name exists in this category");
            }
            sub.setName(name);
        }
        if (req.description() != null) sub.setDescription(req.description());
        if (req.basePrice() != null) sub.setBasePrice(req.basePrice());
        if (req.duration() != null) sub.setDuration(req.duration());
        if (req.imageUrl() != null) sub.setImageUrl(req.imageUrl());
        if (req.enabled() != null) sub.setEnabled(req.enabled());
        if (req.displayOrder() != null) sub.setDisplayOrder(req.displayOrder());
        SubService saved = subServices.save(sub);
        if (adminActivityLogService != null) {
            adminActivityLogService.record(null, "SUBSERVICE_UPDATED", "SUBSERVICE", saved.getId(), saved.getName(), "Updated subservice: " + saved.getName(), null, "/admin/services.html");
        }
        return toSubDto(saved);
    }

    @Transactional
    public void deleteSubService(Long id) {
        SubService sub = subServices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subservice not found"));
        String subName = sub.getName();
        subServices.delete(sub);
        if (adminActivityLogService != null) {
            adminActivityLogService.record(null, "SUBSERVICE_DELETED", "SUBSERVICE", id, subName, "Deleted subservice: " + subName + " (#" + id + ")", null, "/admin/services.html");
        }
    }
}
