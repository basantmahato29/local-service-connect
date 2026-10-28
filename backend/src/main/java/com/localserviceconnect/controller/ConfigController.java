package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.MapsConfigDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${app.google.maps.api-key:}")
    private String apiKey;

    @Value("${app.google.maps.default-center-lat:23.3441}")
    private double defaultLat;

    @Value("${app.google.maps.default-center-lng:85.3096}")
    private double defaultLng;

    @Value("${app.google.maps.default-zoom:12}")
    private int defaultZoom;

    private static volatile String runtimeApiKey = null;

    @GetMapping("/maps")
    public ResponseEntity<MapsConfigDto> getMapsConfig() {
        String effectiveKey = (runtimeApiKey != null && !runtimeApiKey.isBlank()) ? runtimeApiKey : apiKey;
        if (effectiveKey == null || effectiveKey.isBlank() || effectiveKey.equals("YOUR_GOOGLE_MAPS_API_KEY")) {
            String envVar = System.getenv("GOOGLE_MAPS_API_KEY");
            if (envVar != null && !envVar.isBlank()) {
                effectiveKey = envVar.trim();
            }
        }
        if (effectiveKey == null || effectiveKey.isBlank() || effectiveKey.equals("YOUR_GOOGLE_MAPS_API_KEY")) {
            String dotEnvKey = loadKeyFromDotEnv();
            if (dotEnvKey != null && !dotEnvKey.isBlank()) {
                effectiveKey = dotEnvKey.trim();
            }
        }

        boolean configured = effectiveKey != null && !effectiveKey.trim().isBlank() && !effectiveKey.trim().equals("YOUR_GOOGLE_MAPS_API_KEY");
        return ResponseEntity.ok(new MapsConfigDto(
                configured ? effectiveKey.trim() : "",
                configured,
                defaultLat,
                defaultLng,
                defaultZoom
        ));
    }

    @PostMapping("/maps")
    public ResponseEntity<MapsConfigDto> setMapsConfig(@RequestBody Map<String, String> body) {
        String newKey = body != null ? body.get("apiKey") : null;
        runtimeApiKey = (newKey != null && !newKey.isBlank()) ? newKey.trim() : null;
        return getMapsConfig();
    }

    private static String loadKeyFromDotEnv() {
        String[] potentialPaths = {".env", "../.env", "backend/.env"};
        for (String p : potentialPaths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("GOOGLE_MAPS_API_KEY=")) {
                            String val = line.substring("GOOGLE_MAPS_API_KEY=".length()).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (!val.isBlank() && !val.equals("YOUR_GOOGLE_MAPS_API_KEY")) {
                                return val;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
