# Local Service Connect 🛠️📍

A full-stack local on-demand service booking platform connecting customers with verified local professionals (electricians, plumbers, mechanics, cleaners, barbers, carpenters, and more).

---

## 🌟 Key Features

1. **Google Maps Platform Integration**:
   - **Google Places Autocomplete**: Real-time address autocomplete for customer search, booking address, and provider shop/business location.
   - **Interactive Map Picker**: Draggable map pin allowing users to pinpoint exact locations.
   - **Browser Geolocation API**: "Use My Current Location" button for one-tap GPS coordinate detection.
   - **Dynamic Service Coverage Radius**: Service providers can set a coverage radius (e.g., 5 km, 10 km, 15 km, 20 km) with a live visual circle preview on Google Maps.
   - **Haversine Distance Engine**: Computes straight-line distance (`distanceKm`) between customer and provider coordinates; filters out providers outside their configured coverage area.
   - **Nearby Providers Overview Map**: Displays the customer's location (blue marker) and matching verified providers (red markers) with interactive InfoWindows and direct booking actions.
   - **Turn-by-Turn Directions**: Direct link to Google Maps navigation for providers heading to accepted jobs.
   - **Graceful Fallback**: If `GOOGLE_MAPS_API_KEY` is not set, manual address and area-based search continue working without any crashes or disruption.

2. **Rapido-Style OTP Service Verification**:
   - Cryptographically secure 4-digit OTP generated upon booking acceptance.
   - Displayed only in the authenticated customer's booking view with copy button and countdown timer.
   - Hidden from provider API responses; providers must enter the customer's OTP to start service.
   - Separate customer confirmation step upon service completion before ratings & reviews can be submitted.

3. **Service Catalogs & Real-time Matching**:
   - Normal scheduled bookings and ~10-minute target Quick Service bookings.
   - Multi-role support: Customer, Provider, and Admin.

---

## 🗺️ Google Maps API Setup Guide

To enable full Google Maps functionality, follow these steps in your [Google Cloud Console](https://console.cloud.google.com/):

### 1. APIs to Enable
In Google Cloud Console, navigate to **APIs & Services > Library** and enable the following:
- **Maps JavaScript API**: Renders the interactive maps, custom markers, and coverage radius circles.
- **Places API**: Powers address autocomplete suggestions for street names, landmarks, and localities.
- **Geocoding API**: Converts coordinates (latitude/longitude) to human-readable addresses and vice versa.

### 2. Configure Your API Key
Set your API key as an environment variable before launching the application:

#### Windows (PowerShell):
```powershell
$env:GOOGLE_MAPS_API_KEY="AIzaSyYourActualGoogleMapsApiKeyHere"
```

#### Windows (CMD):
```cmd
set GOOGLE_MAPS_API_KEY=AIzaSyYourActualGoogleMapsApiKeyHere
```

#### Linux / macOS:
```bash
export GOOGLE_MAPS_API_KEY="AIzaSyYourActualGoogleMapsApiKeyHere"
```

Alternatively, you can specify it in `backend/src/main/resources/application.properties`:
```properties
app.google.maps.api-key=AIzaSyYourActualGoogleMapsApiKeyHere
```

> [!NOTE]
> If no API key is configured, the application automatically enters fallback mode. The UI displays an informative alert and allows customers and providers to enter areas and addresses manually.

---

## 🚀 Getting Started

### Prerequisites
- **Java 21+** (Tested on Java 25)
- **Maven 3.8+**
- Modern Web Browser (Chrome, Edge, Firefox, Safari)

### Run Backend & Serve Frontend
From the `backend/` directory:
```bash
mvn spring-boot:run
```

Once started:
- Open your browser at: **[http://localhost:8080](http://localhost:8080)**

---

## 🧪 Testing

Run all unit and integration tests (including GeoUtils, Google Maps endpoints, OTP verification, and booking flows):
```bash
cd backend
mvn test
```
All 32 tests pass out of the box with zero failures.

---

## 📐 Architecture & Geo Formulas

### Straight-line Haversine Distance Formula
Straight-line distance calculation implemented in `GeoUtils.java`:
$$d = 2R \cdot \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)}\right)$$
Where:
- $R = 6371.0\text{ km}$ (mean Earth radius)
- $\phi_1, \phi_2$ = latitudes in radians
- $\Delta\phi = \phi_2 - \phi_1$
- $\Delta\lambda = \lambda_2 - \lambda_1$

### Eligibility Rules for Provider Matching
A provider is eligible if:
1. `verificationStatus == VERIFIED`
2. Offers the requested service type (`status == ACTIVE`)
3. For Quick Service: `availability == true` and not currently occupied on an active booking
4. **Location Eligibility**: Straight-line distance $d \le \text{provider.serviceRadiusKm}$ (or matching configured textual areas as fallback).

