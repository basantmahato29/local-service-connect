package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ServiceCatalogController {

    private final CatalogService catalogService;

    public ServiceCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Public endpoint: list active services in the catalog */
    @GetMapping({"/api/services", "/api/catalog/services"})
    public ResponseEntity<List<ServiceDto>> listActive() {
        return ResponseEntity.ok(catalogService.listActive());
    }

    /** Public endpoint: search active services in the catalog */
    @GetMapping({"/api/services/search", "/api/catalog/services/search"})
    public ResponseEntity<List<ServiceDto>> searchActive(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(catalogService.searchActive(query));
    }

    /** Public endpoint: get service by ID */
    @GetMapping({"/api/services/{id}", "/api/catalog/services/{id}"})
    public ResponseEntity<ServiceDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.get(id));
    }

    /** Public endpoint: list active subservices under a service category */
    @GetMapping({"/api/services/{id}/subservices", "/api/catalog/services/{id}/subservices"})
    public ResponseEntity<List<SubServiceDto>> listActiveSubServices(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.listSubServices(id, true));
    }

    /** Public endpoint: get single subservice by ID */
    @GetMapping({"/api/subservices/{id}", "/api/catalog/subservices/{id}"})
    public ResponseEntity<SubServiceDto> getSubService(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.getSubService(id));
    }

    /** Admin endpoint: list all services (active and inactive) */
    @GetMapping("/api/admin/services")
    public ResponseEntity<List<ServiceDto>> listAll() {
        return ResponseEntity.ok(catalogService.listAll());
    }

    /** Admin endpoint: create new service */
    @PostMapping("/api/admin/services")
    public ResponseEntity<ServiceDto> create(@RequestBody ServiceCreateRequest request) {
        return ResponseEntity.ok(catalogService.create(request));
    }

    /** Admin endpoint: update service */
    @PutMapping("/api/admin/services/{id}")
    public ResponseEntity<ServiceDto> update(@PathVariable Long id, @RequestBody ServiceUpdateRequest request) {
        return ResponseEntity.ok(catalogService.update(id, request));
    }

    /** Admin endpoint: delete / deactivate service */
    @DeleteMapping("/api/admin/services/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Admin endpoint: list all subservices under a category (including inactive) */
    @GetMapping("/api/admin/services/{id}/subservices")
    public ResponseEntity<List<SubServiceDto>> listAdminSubServices(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.listSubServices(id, false));
    }

    /** Admin endpoint: create subservice */
    @PostMapping("/api/admin/services/{id}/subservices")
    public ResponseEntity<SubServiceDto> createSubService(
            @PathVariable Long id,
            @RequestBody SubServiceCreateRequest request) {
        SubServiceCreateRequest req = (request.serviceId() == null || !request.serviceId().equals(id))
                ? new SubServiceCreateRequest(id, request.name(), request.description(), request.basePrice(), request.duration(), request.imageUrl(), request.enabled(), request.displayOrder())
                : request;
        return ResponseEntity.ok(catalogService.createSubService(req));
    }

    /** Admin endpoint: update subservice */
    @PutMapping("/api/admin/subservices/{id}")
    public ResponseEntity<SubServiceDto> updateSubService(
            @PathVariable Long id,
            @RequestBody SubServiceUpdateRequest request) {
        return ResponseEntity.ok(catalogService.updateSubService(id, request));
    }

    /** Admin endpoint: delete subservice */
    @DeleteMapping("/api/admin/subservices/{id}")
    public ResponseEntity<Void> deleteSubService(@PathVariable Long id) {
        catalogService.deleteSubService(id);
        return ResponseEntity.noContent().build();
    }
}
