package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.Customer;
import com.localserviceconnect.entity.User;
import com.localserviceconnect.repository.CustomerRepository;
import com.localserviceconnect.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {

    private final CustomerRepository customers;
    private final UserRepository users;
    private final com.localserviceconnect.security.JwtUtil jwtUtil;

    public CustomerProfileService(CustomerRepository customers, UserRepository users, com.localserviceconnect.security.JwtUtil jwtUtil) {
        this.customers = customers;
        this.users = users;
        this.jwtUtil = jwtUtil;
    }

    @Transactional(readOnly = true)
    public CustomerProfileDto getProfile(String email) {
        Customer c = customers.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        return toDto(c, null);
    }

    @Transactional
    public CustomerProfileDto updateProfile(String email, CustomerProfileUpdateRequest req) {
        Customer c = customers.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        User u = c.getUser();
        boolean emailChanged = false;
        if (req.name() != null && !req.name().isBlank()) {
            u.setName(req.name().trim());
        }
        if (req.phone() != null && !req.phone().isBlank()) {
            u.setPhone(req.phone().trim());
        }
        if (req.email() != null && !req.email().isBlank() && !req.email().equalsIgnoreCase(u.getEmail())) {
            String newEmail = req.email().trim().toLowerCase();
            if (users.existsByEmail(newEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already taken by another account");
            }
            u.setEmail(newEmail);
            emailChanged = true;
        }
        users.save(u);

        if (req.profileImage() != null) {
            c.setProfileImage(req.profileImage().trim());
        }
        if (req.address() != null) c.setAddress(req.address().trim());
        if (req.area() != null) c.setArea(req.area().trim());
        if (req.city() != null) c.setCity(req.city().trim());
        if (req.pincode() != null) c.setPincode(req.pincode().trim());
        if (req.formattedAddress() != null) c.setFormattedAddress(req.formattedAddress().trim());
        if (req.latitude() != null || req.longitude() != null) {
            if (!com.localserviceconnect.util.GeoUtils.isValidCoordinates(req.latitude(), req.longitude())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
            }
            c.setLatitude(req.latitude());
            c.setLongitude(req.longitude());
        }
        String freshToken = emailChanged ? jwtUtil.generate(u) : null;
        return toDto(customers.save(c), freshToken);
    }

    @Transactional
    public void deactivateAccount(String email) {
        Customer c = customers.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        User u = c.getUser();
        u.setEnabled(false);
        users.save(u);
    }

    private CustomerProfileDto toDto(Customer c, String token) {
        User u = c.getUser();
        return new CustomerProfileDto(
                c.getId(),
                u.getName(),
                u.getEmail(),
                u.getPhone(),
                c.getAddress(),
                c.getArea(),
                c.getCity(),
                c.getPincode(),
                c.getFormattedAddress(),
                c.getLatitude(),
                c.getLongitude(),
                c.getProfileImage(),
                token
        );
    }
}
