package com.example.medilinkchain;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.Locale;

/**
 * GoogleMapsHelper — Centralized Google Maps integration helper for MediLinkChain.
 * Provides access to the configured Google Maps API Key, URL builders for Directions,
 * Geocoding, Places, and Static Maps, as well as reliable navigation intents.
 */
public class GoogleMapsHelper {

    public static final String API_KEY = BuildConfig.MAPS_API_KEY;

    /**
     * Returns the active Google Maps API key injected from build configuration.
     */
    public static String getApiKey() {
        return API_KEY;
    }

    /**
     * Builds a Google Maps Directions web/intent URL.
     */
    public static String buildDirectionsUrl(Double originLat, Double originLng, double destLat, double destLng, String travelMode) {
        String mode = (travelMode != null && !travelMode.isEmpty()) ? travelMode : "driving";
        if (originLat != null && originLng != null) {
            return String.format(Locale.US,
                    "https://www.google.com/maps/dir/?api=1&origin=%.6f,%.6f&destination=%.6f,%.6f&travelmode=%s",
                    originLat, originLng, destLat, destLng, mode);
        } else {
            return String.format(Locale.US,
                    "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=%s",
                    destLat, destLng, mode);
        }
    }

    /**
     * Builds a Google Maps Static Map image URL.
     */
    public static String buildStaticMapUrl(double lat, double lng, int zoom, int width, int height) {
        return String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/staticmap?center=%.6f,%.6f&zoom=%d&size=%dx%d&markers=color:red%%7C%.6f,%.6f&key=%s",
                lat, lng, zoom, width, height, lat, lng, API_KEY);
    }

    /**
     * Builds a Google Maps Geocoding API request URL.
     */
    public static String buildGeocodingUrl(String address) {
        return String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s",
                Uri.encode(address != null ? address : ""), API_KEY);
    }

    /**
     * Builds a Google Places Nearby Search API request URL for hospitals/healthcare facilities.
     */
    public static String buildNearbyHospitalsUrl(double lat, double lng, int radiusMeters) {
        return String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=%.6f,%.6f&radius=%d&type=hospital&key=%s",
                lat, lng, radiusMeters, API_KEY);
    }

    /**
     * Opens turn-by-turn navigation in the official Google Maps application
     * with graceful fallbacks to general map viewers and web browsers.
     */
    public static void openNavigation(Context context, Double originLat, Double originLng, double destLat, double destLng, String label) {
        if (context == null) return;

        // Attempt 1: Direct navigation mode in Google Maps App
        try {
            Uri navUri = Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f&mode=d", destLat, destLng));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, navUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            context.startActivity(mapIntent);
            return;
        } catch (ActivityNotFoundException ignored) {}

        // Attempt 2: Geo URI scheme (handled by any installed map app)
        try {
            String query = (label != null && !label.isEmpty()) ? Uri.encode(label) : (destLat + "," + destLng);
            Uri geoUri = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%s", destLat, destLng, query));
            Intent geoIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            context.startActivity(geoIntent);
            return;
        } catch (Exception ignored) {}

        // Attempt 3: Web Browser fallback using Google Maps Directions URL
        try {
            String webUrl = buildDirectionsUrl(originLat, originLng, destLat, destLng, "driving");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
            context.startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to launch navigation.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Opens a Google Maps search query in the Google Maps app or browser.
     */
    public static void openSearch(Context context, String query) {
        if (context == null || query == null || query.trim().isEmpty()) return;
        try {
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(query.trim()));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            context.startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            try {
                Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query.trim()));
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, webUri);
                context.startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(context, "Unable to open Google Maps.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

