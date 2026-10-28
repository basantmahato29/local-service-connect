package com.localserviceconnect.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Geographical calculation utilities (Haversine formula for straight-line distance).
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    /**
     * Validates whether a latitude value is within valid geographical boundaries [-90.0, 90.0].
     */
    public static boolean isValidLatitude(Double lat) {
        return lat != null && !lat.isNaN() && !lat.isInfinite() && lat >= -90.0 && lat <= 90.0;
    }

    /**
     * Validates whether a longitude value is within valid geographical boundaries [-180.0, 180.0].
     */
    public static boolean isValidLongitude(Double lon) {
        return lon != null && !lon.isNaN() && !lon.isInfinite() && lon >= -180.0 && lon <= 180.0;
    }

    /**
     * Validates whether a coordinate pair represents valid geographical latitude and longitude.
     */
    public static boolean isValidCoordinates(Double lat, Double lon) {
        return isValidLatitude(lat) && isValidLongitude(lon);
    }

    /**
     * Calculates the great-circle distance between two coordinates in kilometers.
     * Returns null if any coordinate is null.
     * Throws IllegalArgumentException if coordinates are out of valid geographical boundaries.
     */
    public static Double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }

        if (!isValidCoordinates(lat1, lon1) || !isValidCoordinates(lat2, lon2)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid coordinate bounds: latitude must be in [-90.0, 90.0] and longitude in [-180.0, 180.0]. Given: (%s, %s) -> (%s, %s)",
                    lat1, lon1, lat2, lon2));
        }

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        // Guard against floating-point inaccuracies where 'a' slightly exceeds 1.0 or drops below 0.0
        a = Math.max(0.0, Math.min(1.0, a));

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(distance).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Checks whether the target coordinate is within the specified radius (in km) from the center.
     * Safely returns false if any coordinate or radius is null, invalid, or radius is negative.
     */
    public static boolean isWithinRadius(Double centerLat, Double centerLon, Double targetLat, Double targetLon, Double radiusKm) {
        if (!isValidCoordinates(centerLat, centerLon) || !isValidCoordinates(targetLat, targetLon) || radiusKm == null || radiusKm < 0) {
            return false;
        }
        Double distance = distanceKm(centerLat, centerLon, targetLat, targetLon);
        if (distance == null) {
            return false;
        }
        return distance <= radiusKm;
    }
}

