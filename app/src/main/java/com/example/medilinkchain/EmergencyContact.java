package com.example.medilinkchain;

public class EmergencyContact {
    private String name;
    private String relation;
    private String phone;

    public EmergencyContact(String name, String relation, String phone) {
        this.name = name;
        this.relation = relation;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getRelation() { return relation; }
    public String getPhone() { return phone; }
}
