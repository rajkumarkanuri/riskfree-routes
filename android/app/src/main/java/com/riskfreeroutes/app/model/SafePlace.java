package com.riskfreeroutes.app.model;

import com.google.android.gms.maps.model.LatLng;

public class SafePlace {
    private String id;
    private String name;
    private String address;
    private LatLng location;
    private double distanceMeters;
    private String phoneNumber;
    private Boolean isOpenNow;
    private String type; // "police", "hospital", "pharmacy"

    public SafePlace(String id, String name, String address, LatLng location, double distanceMeters, String phoneNumber, Boolean isOpenNow, String type) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.location = location;
        this.distanceMeters = distanceMeters;
        this.phoneNumber = phoneNumber;
        this.isOpenNow = isOpenNow;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public LatLng getLocation() { return location; }
    public double getDistanceMeters() { return distanceMeters; }
    public String getPhoneNumber() { return phoneNumber; }
    public Boolean isOpenNow() { return isOpenNow; }
    public String getType() { return type; }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
}
