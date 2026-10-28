# Project Rules & Architecture Specifications
## Local Service Connect

**Document Version:** 1.0.0  
**Last Updated:** October 2026  
**System Classification:** Home-Service Booking & Dispatch Platform (Full Stack Web Application)

---

## 1. Existing Technology Stack and Architecture

### 1.1 Backend Technology Stack
* **Language & Runtime:** Java 25 (Eclipse Adoptium OpenJDK 25.0.4.1), 64-Bit Server VM.
* **Framework:** Spring Boot 3.5.16.
* **Build System & Tooling:** Apache Maven 3.9.9.
* **Security & Auth:** Spring Security 6, Stateless JWT (io.jsonwebtoken / jjwt 0.12.6), BCryptPasswordEncoder (strength 10), AES-256-GCM (`CryptoUtil`) for sensitive at-rest OTP encryption.
* **Persistence & ORM:** Spring Data JPA / Hibernate 6.x.
* **Database Drivers:**
  - H2 Database (v2.3.232) for development and test execution.
  - MySQL Connector/J (v9.x) for production deployment.
* **Validation:** Jakarta Bean Validation (`jakarta.validation-api` / Hibernate Validator).
* **Communication & Integrations:**
  - Pluggable SMS gateway (`SmsService` with `TwilioSmsService`, `Msg91SmsService`, and `NoOpSmsService`).
  - Google Maps & OpenStreetMap Geocoding integration via REST and frontend Maps JS API.

### 1.2 Frontend Technology Stack
* **Markup & Structure:** Semantic HTML5 with accessibility roles, WCAG 2.1 AA targets, and descriptive labels.
* **Styling:** Custom CSS design system (`frontend/css/style.css`) built with CSS custom properties, paired with Bootstrap 5.3.3 and Bootstrap Icons 1.11.3.
* **Client Logic:** Modern Vanilla JavaScript (ES6+ modular closures and async/await).
* **Mapping & Location UI:** Dual engine supporting Leaflet 1.9.4 and Google Maps JavaScript API (with OpenStreetMap Nominatim reverse geocoding fallback).
* **Asset Pipeline:** Static resources in `frontend/` synchronized with `backend/src/main/resources/static/` via Maven `maven-resources-plugin:copy-resources`.

### 1.3 Architecture Pattern
The project implements a classic **Multi-Tier Layered Architecture**:
1. **Presentation Layer:** Client HTML/CSS/JS served directly via Spring Boot Tomcat embedded server on port 8080.
2. **Controller Layer (REST APIs):** Maps HTTP routes, extracts authenticated credentials via Spring Security `Authentication`, and delegates to services.
3. **Service Layer (Business Logic):** Encapsulates domain logic, booking matching, authorization checks, geofencing (Haversine via `GeoUtils`), state transitions, and cryptographic operations.
4. **Data Access Layer (Spring Data JPA Repositories):** Executes JPQL queries, pessimistic locks (`findWithLockById`), and specification executions.
5. **Database Storage Layer:** Relational persistence with foreign-key constraints, cascading deletes where appropriate, and indexed status/area columns.

---

## 2. Coding Conventions and Naming Rules

### 2.1 Backend (Java)
* **Package Structure:** Standard reverse-domain convention: `com.localserviceconnect.*`
  - `controller`: REST endpoints and HTTP contract handlers.
  - `service`: Domain logic, calculations, transaction boundaries (`@Transactional`).
  - `repository`: Spring Data JPA repository interfaces.
  - `entity`: JPA entities mapping database tables.
  - `dto`: Immutable record classes (`Dtos.java`) for type-safe API requests and responses.
  - `security`: JWT filters, cryptographic utilities, and security filter chain.
  - `config`: Exception handlers, CORS config, and database seeders.
  - `util`: Stateless algorithmic helpers (e.g., `GeoUtils`).
* **Naming Conventions:**
  - Classes: UpperCamelCase (e.g., `BookingService`, `ProviderProfileController`).
  - Methods & Variables: lowerCamelCase (e.g., `verifyAndStart`, `customerEmail`).
  - Constants & Enums: UPPER_SNAKE_CASE (e.g., `EARTH_RADIUS_KM`, `BOOKING_TYPE`).
  - Database Mappings: Lowercase with underscores (`@Column(name = "service_radius_km")`).
* **Data Transfer Objects:** Use Java Records for request and response payloads to guarantee immutability.

### 2.2 Frontend (JavaScript & CSS)
* **File Organization:** Dedicated directories for `css/`, `js/`, `admin/`, `customer/`, `provider/`, `img/`.
* **JavaScript Conventions:**
  - Vanilla ES6+ without external monolithic UI frameworks.
  - Asynchronous operations must use `async/await` with structured `try/catch/finally` blocks.
  - DOM helpers: Element queries via `$(id)` shorthand (`document.getElementById`).
  - Strict escaping: All dynamic strings injected into innerHTML must pass through `esc()` to prevent XSS.
* **CSS Conventions:**
  - Centralized design tokens in `:root` (colors, radii, shadows, typography).
  - BEM-inspired naming (e.g., `.hero-location-box`, `.btn-current-location`).
  - Strict responsive design: Breakpoints at 1200px, 992px, 768px, 480px, and 360px.

---

## 3. Layer Responsibilities & System Boundaries

### 3.1 Frontend Responsibilities
* Render responsive user interfaces for Customers, Providers, and Administrators.
* Perform client-side format checks (e.g., 4-digit OTP format, required fields, date constraints).
* Manage local session state using `localStorage` keys: `lsc_token`, `lsc_role`, `lsc_name`, `lsc_id`.
* Gracefully display loading spinners, disabled button states, and toast notifications.
* **Forbidden on Frontend:** Frontend must never make authorization decisions or assume client inputs are trusted. Hiding a button does not constitute security.

### 3.2 Backend Responsibilities
* Enforce absolute server-side authorization: Every privileged action must verify caller identity from the JWT `Authentication` principal.
* Validate all request inputs (null checks, boundary ranges, coordinate limits, regex formats).
* Compute business logic: Haversine distance calculations, provider availability filtering, target completion states.
* Enforce pessimistic and transactional concurrency controls on state changes.
* Sanitize and format data returned to clients (e.g., mask customer address/phone until booking is accepted).

### 3.3 Database Responsibilities
* Ensure referential integrity via foreign-key constraints.
* Enforce uniqueness on natural keys (`users.email`, `services.name`, `(provider_id, service_id)`).
* Guarantee persistence and ACID transactions.

---

## 4. Authentication and Authorization Requirements

### 4.1 Authentication Standards
* **Mechanism:** Stateless JSON Web Tokens (JWT) signed with HMAC-SHA256 (`Keys.hmacShaKeyFor`).
* **Token Lifetime:** Configurable expiration (default: 24 hours / 86,400,000 ms).
* **Password Storage:** One-way BCrypt hashing with auto-generated salt (min strength 10). Plaintext passwords must never be logged or stored.
* **Account Status:** Disabled accounts (`enabled = false`) must be blocked immediately at the `JwtFilter` level with HTTP 403 Forbidden.

### 4.2 Role-Based Access Control (RBAC)
Three distinct system roles:
1. `ROLE_CUSTOMER`: Can search providers, create bookings, view their own bookings/profile, generate service-start OTPs, confirm service completion, and write reviews for completed bookings.
2. `ROLE_PROVIDER`: Can manage their own business profile, services, coverage areas, availability toggle, accept/reject assigned bookings, advance booking workflow steps, and verify customer OTPs.
3. `ROLE_ADMIN`: Can access the administrative dashboard, view system metrics, verify/reject provider registrations, enable/disable customer and provider accounts, and delete inappropriate reviews.

### 4.3 Insecure Direct Object Reference (IDOR) Defenses
* Accessing `/api/bookings/{id}` must verify that the authenticated caller is either the booking's Customer, the booking's Provider, or an Administrator.
* Non-owners must receive **HTTP 404 Not Found** (not HTTP 403) to prevent resource enumeration and existence confirmation.
* Provider operations (`/api/provider/bookings/{id}/accept`, etc.) must verify ownership using the authenticated provider's ID or email.

---

## 5. Input Validation and Error-Handling Standards

### 5.1 Input Validation Rules
* **Bean Validation:** Every mutating controller endpoint must annotate request bodies with `@Valid`.
* **String Constraints:** Name, email, phone, and addresses must be trimmed, non-blank, and bound to reasonable length limits.
* **Coordinate Boundaries:**
  - Latitude: must be between `-90.0` and `+90.0`.
  - Longitude: must be between `-180.0` and `+180.0`.
  - Reject `NaN`, `Infinity`, or out-of-range decimal values.
* **Service-Start OTP:** Must strictly match `^[0-9]{4}$`.
* **Ratings:** Must be integers strictly between `1` and `5`.

### 5.2 Error-Handling Standards
* Handled globally via `GlobalExceptionHandler` (`@RestControllerAdvice`).
* Error responses must return consistent JSON payloads: `{"message": "Descriptive message"}`.
* **No Stack Trace Leakage:** Server traces, SQL exception details, and internal class names must never be exposed to clients.
* HTTP Status Code Consistency:
  - `400 Bad Request`: Malformed payload, invalid format, out-of-range parameters.
  - `401 Unauthorized`: Missing, expired, or invalid JWT token.
  - `403 Forbidden`: Insufficient role permissions or account disabled.
  - `404 Not Found`: Resource does not exist or caller is unauthorized to view it.
  - `409 Conflict`: Duplicate entry, invalid state transition, or schedule collision.
  - `500 Internal Server Error`: Unhandled catastrophic failure (logged internally).

---

## 6. Database Consistency and Data Integrity Requirements

### 6.1 State Machine Integrity for Bookings
Bookings must transition strictly through valid states:
1. `PENDING`: Initial state upon creation.
2. `ACCEPTED`: Provider confirms acceptance. (Triggers automatic service-start OTP generation).
3. `REJECTED`: Provider rejects booking (terminal state).
4. `CANCELLED`: Customer cancels pending or accepted booking (terminal state). Invalidates active OTP.
5. `ON_THE_WAY`: Provider indicates they are en route to customer location.
6. `SERVICE_STARTED`: Can **only** be reached by successful customer 4-digit OTP verification. Manual status bypass is blocked.
7. `AWAITING_COMPLETION`: Provider finishes work and requests customer sign-off.
8. `COMPLETED`: Customer explicitly confirms job completion (terminal state, unlocks review eligibility).

### 6.2 Concurrency & Race Condition Safeguards
* Critical status updates (e.g., OTP verification, booking acceptance) must use pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to eliminate race conditions.
* Quick Service exclusive booking: A provider cannot accept multiple Quick Service bookings simultaneously.

---

## 7. API Security Requirements

1. **CORS Policy:** Strict origin control in production environments.
2. **CSRF Strategy:** Disabled because authentication relies on stateless `Authorization: Bearer` headers rather than ambient cookies.
3. **Session Policy:** `SessionCreationPolicy.STATELESS` — no server-side HTTP session storage.
4. **Data Masking (Privacy by Design):** Customer phone numbers, exact residential street addresses, and GPS coordinates must be hidden from providers until the provider explicitly accepts the booking.
5. **OTP Storage & Exposure:**
   - Stored in database as a BCrypt hash for verification.
   - Encrypted with AES-256-GCM for customer retrieval.
   - Plaintext OTP is permanently wiped from memory and database once verified.
   - Maximum 5 failed verification attempts before temporary 15-minute lockout.
6. **Rate Limiting:** Protect authentication and OTP verification endpoints against brute-force attacks.

---

## 8. Testing Standards and Minimum Acceptance Criteria

1. **Unit Testing:**
   - All domain calculations (Haversine distances, time windows, ratings).
   - Coordinate boundary validation.
   - Status transition validations.
2. **Integration Testing:**
   - Full Spring MVC slice testing (`MockMvc`) with real database rollback (`@Transactional`).
   - Role permission boundary verification (Customer vs. Provider vs. Admin).
   - OTP lifecycle: creation, verification, attempt decrement, lockout, expiration.
3. **End-to-End Testing:**
   - Automated multi-role user workflows (Customer booking -> Provider acceptance -> OTP start -> Completion -> Review).
4. **Performance & Stress Testing:**
   - Automated concurrent user simulation measuring latency, error rates, and state consistency under load.
5. **Acceptance Threshold:** 100% pass rate on all automated tests before deployment. Zero regression tolerance.

---

## 9. Performance and Accessibility Expectations

* **Performance:**
  - Page load time < 2.0s on standard 4G/broadband.
  - API response time (p95) < 200ms for read endpoints; < 400ms for write endpoints under normal load.
  - Lightweight DOM footprint and responsive layout without layout shifts (CLS < 0.1).
* **Accessibility (WCAG 2.1 AA):**
  - Minimum contrast ratio of 4.5:1 for standard text and 3:1 for interactive UI components.
  - Accessible keyboard navigation (`tabindex`, `Enter`, `Space` support on all interactive elements).
  - Explicit `aria-label`, `role`, and `title` attributes on icon-only and docked controls.

---

## 10. Rules for Protecting Existing Functionality

1. **Preserve Working Features:** Never refactor working UI components or APIs without a dedicated regression test suite.
2. **Incremental Remediation:** Implement minimal, surgical fixes. Avoid monolithic, high-risk rewrites.
3. **Secret Protection:** Never commit API keys (Google Maps, Twilio, JWT secrets) or database credentials to git. All credentials must be sourced from environment variables.
4. **Synchronization Rule:** Whenever modifying files in `frontend/`, ensure corresponding copies in `backend/src/main/resources/static/` remain perfectly synchronized.
