package com.example.medilinkchain;

import java.io.Serializable;
import java.util.Locale;

public class OsmHospital implements Serializable {
    private long id;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private float distanceMeters;
    private String phone;
    private String facilityType;
    private String specialty;

    public OsmHospital(long id, String name, String address, double latitude, double longitude,
                       float distanceMeters, String phone, String facilityType, String specialty) {
        this.id = id;
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "Hospital / Medical Center";
        this.address = address != null && !address.trim().isEmpty() ? address.trim() : "Location nearby";
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.phone = phone != null ? phone.trim() : "";
        this.facilityType = facilityType != null ? facilityType : "Hospital";
        this.specialty = specialty != null ? specialty : "General Healthcare";
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(float distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public String getPhone() {
        return phone;
    }

    public String getFacilityType() {
        return facilityType;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getFormattedDistance() {
        if (distanceMeters < 1000f) {
            return Math.round(distanceMeters) + " m away";
        } else {
            return String.format(Locale.US, "%.1f km away", distanceMeters / 1000.0f);
        }
    }

    public String getShortDistance() {
        if (distanceMeters < 1000f) {
            return Math.round(distanceMeters) + " m";
        } else {
            return String.format(Locale.US, "%.1f km", distanceMeters / 1000.0f);
        }
    }
}
