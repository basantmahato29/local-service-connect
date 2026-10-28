package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.service.CustomerProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/profile")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping
    public ResponseEntity<CustomerProfileDto> get(Authentication authentication) {
        return ResponseEntity.ok(customerProfileService.getProfile(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<CustomerProfileDto> update(
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody CustomerProfileUpdateRequest request) {
        return ResponseEntity.ok(customerProfileService.updateProfile(authentication.getName(), request));
    }

    @DeleteMapping
    public ResponseEntity<?> deactivate(Authentication authentication) {
        customerProfileService.deactivateAccount(authentication.getName());
        return ResponseEntity.ok(java.util.Map.of("message", "Account successfully deactivated"));
    }
}
