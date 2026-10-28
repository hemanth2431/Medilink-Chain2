package com.example.medilinkchain;

public class Hospital {
    private String name;
    private String distance;
    private String phone;

    public Hospital(String name, String distance, String phone) {
        this.name = name;
        this.distance = distance;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getDistance() { return distance; }
    public String getPhone() { return phone; }
}
