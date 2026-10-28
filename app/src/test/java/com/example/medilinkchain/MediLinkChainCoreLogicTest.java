package com.example.medilinkchain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * Unit tests covering core MediLinkChain business logic, calculations,
 * hospital distance computation, health assessment ranges, and role/id formatting.
 */
public class MediLinkChainCoreLogicTest {

    // =========================================================================
    // 1. OsmHospital & Geographic Distance Logic
    // =========================================================================

    @Test
    public void testOsmHospitalInstantiationAndDefaults() {
        OsmHospital hospital = new OsmHospital(
                101L,
                "Apollo Multi-Specialty Hospital",
                "Jubilee Hills, Road No 72, Hyderabad",
                17.4156,
                78.4350,
                1500.0f,
                "+91 40 2360 7777",
                "Hospital",
                "Cardiology, Emergency, Trauma"
        );

        assertEquals(101L, hospital.getId());
        assertEquals("Apollo Multi-Specialty Hospital", hospital.getName());
        assertEquals(17.4156, hospital.getLatitude(), 0.0001);
        assertEquals(78.4350, hospital.getLongitude(), 0.0001);
        assertEquals("Jubilee Hills, Road No 72, Hyderabad", hospital.getAddress());
        assertEquals("+91 40 2360 7777", hospital.getPhone());
        assertEquals("Hospital", hospital.getFacilityType());
        assertEquals("Cardiology, Emergency, Trauma", hospital.getSpecialty());
        assertEquals(1500.0f, hospital.getDistanceMeters(), 0.1f);
    }

    @Test
    public void testOsmHospitalNullSafetyDefaults() {
        OsmHospital hospital = new OsmHospital(
                202L,
                null,
                null,
                17.4000,
                78.4000,
                0f,
                null,
                null,
                null
        );

        assertEquals("Hospital / Medical Center", hospital.getName());
        assertEquals("Location nearby", hospital.getAddress());
        assertEquals("", hospital.getPhone());
        assertEquals("Hospital", hospital.getFacilityType());
        assertEquals("General Healthcare", hospital.getSpecialty());
    }

    @Test
    public void testOsmHospitalDistanceFormatting() {
        // Less than 1000m
        OsmHospital nearHospital = new OsmHospital(
                1L, "Clinic", "Street 1", 17.0, 78.0, 450.0f, "", "Clinic", "General"
        );
        assertEquals("450 m away", nearHospital.getFormattedDistance());
        assertEquals("450 m", nearHospital.getShortDistance());

        // More than 1000m
        OsmHospital farHospital = new OsmHospital(
                2L, "Hospital Center", "Street 2", 17.0, 78.0, 2450.0f, "", "Hospital", "Trauma"
        );
        assertEquals("2.5 km away", farHospital.getFormattedDistance());
        assertEquals("2.5 km", farHospital.getShortDistance());
    }

    @Test
    public void testHaversineDistanceCalculationAndSorting() {
        // Haversine formula calculation
        double userLat = 17.4190;
        double userLon = 78.4390;

        OsmHospital h1 = new OsmHospital(1L, "Far Hospital", "Addr1", 18.0000, 79.0000, 0f, "", "Hospital", "");
        OsmHospital h2 = new OsmHospital(2L, "Close Hospital", "Addr2", 17.4200, 78.4400, 0f, "", "Hospital", "");
        OsmHospital h3 = new OsmHospital(3L, "Mid Hospital", "Addr3", 17.5000, 78.5000, 0f, "", "Hospital", "");

        h1.setDistanceMeters((float) computeHaversineDistance(userLat, userLon, h1.getLatitude(), h1.getLongitude()));
        h2.setDistanceMeters((float) computeHaversineDistance(userLat, userLon, h2.getLatitude(), h2.getLongitude()));
        h3.setDistanceMeters((float) computeHaversineDistance(userLat, userLon, h3.getLatitude(), h3.getLongitude()));

        assertTrue(h2.getDistanceMeters() < h3.getDistanceMeters());
        assertTrue(h3.getDistanceMeters() < h1.getDistanceMeters());

        List<OsmHospital> list = new ArrayList<>();
        list.add(h1);
        list.add(h2);
        list.add(h3);

        Collections.sort(list, (a, b) -> Float.compare(a.getDistanceMeters(), b.getDistanceMeters()));

        assertEquals("Close Hospital", list.get(0).getName());
        assertEquals("Mid Hospital", list.get(1).getName());
        assertEquals("Far Hospital", list.get(2).getName());
    }

    private double computeHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371e3; // meters
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // =========================================================================
    // 2. Health Assessment & Vital Signs Validation Logic
    // =========================================================================

    @Test
    public void testBmiCalculation() {
        // Height: 175 cm, Weight: 70 kg -> BMI = 70 / (1.75 * 1.75) = 22.86
        double heightCm = 175.0;
        double weightKg = 70.0;
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);

        assertEquals(22.86, bmi, 0.05);

        // Category assertion
        String category;
        if (bmi < 18.5) category = "Underweight";
        else if (bmi < 25.0) category = "Normal weight";
        else if (bmi < 30.0) category = "Overweight";
        else category = "Obese";

        assertEquals("Normal weight", category);
    }

    @Test
    public void testBloodPressureClassification() {
        assertEquals("Normal", classifyBloodPressure(118, 76));
        assertEquals("Elevated", classifyBloodPressure(124, 78));
        assertEquals("Stage 1 Hypertension", classifyBloodPressure(132, 84));
        assertEquals("Stage 2 Hypertension", classifyBloodPressure(145, 92));
        assertEquals("Hypertensive Crisis", classifyBloodPressure(185, 125));
    }

    private String classifyBloodPressure(int systolic, int diastolic) {
        if (systolic > 180 || diastolic > 120) {
            return "Hypertensive Crisis";
        } else if (systolic >= 140 || diastolic >= 90) {
            return "Stage 2 Hypertension";
        } else if (systolic >= 130 || diastolic >= 80) {
            return "Stage 1 Hypertension";
        } else if (systolic >= 120 && diastolic < 80) {
            return "Elevated";
        } else {
            return "Normal";
        }
    }

    @Test
    public void testFastingSugarClassification() {
        assertEquals("Normal", classifyFastingSugar(88.0));
        assertEquals("Prediabetes", classifyFastingSugar(110.0));
        assertEquals("Diabetes Indicator", classifyFastingSugar(135.0));
    }

    private String classifyFastingSugar(double mgDl) {
        if (mgDl < 70) return "Low Blood Sugar (Hypoglycemia)";
        if (mgDl <= 99) return "Normal";
        if (mgDl <= 125) return "Prediabetes";
        return "Diabetes Indicator";
    }

    // =========================================================================
    // 3. Admin ID to Synthetic Email Resolution Logic
    // =========================================================================

    @Test
    public void testAdminIdToEmailMapping() {
        String inputAdminId = "AD26H001";
        String normalizedInput = inputAdminId.trim();

        String resolvedEmail;
        if (normalizedInput.contains("@")) {
            resolvedEmail = normalizedInput;
        } else {
            resolvedEmail = normalizedInput.toLowerCase(Locale.ROOT) + "@medilinkchain.org";
        }

        assertEquals("ad26h001@medilinkchain.org", resolvedEmail);

        // Regular email input should remain unchanged
        String regularEmail = "doctor.smith@cityhospital.com";
        String resolvedRegular = regularEmail.contains("@") ? regularEmail : regularEmail + "@medilinkchain.org";
        assertEquals("doctor.smith@cityhospital.com", resolvedRegular);
    }
}
