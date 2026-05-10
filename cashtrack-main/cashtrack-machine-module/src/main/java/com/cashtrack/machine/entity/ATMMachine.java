package com.cashtrack.machine.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "atm_machines")
public class ATMMachine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String location;
    private double cashBalance;
    private String status; // ACTIVE, INACTIVE, OUT_OF_CASH
    private double latitude;
    private double longitude;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public double getCashBalance() { return cashBalance; }
    public void setCashBalance(double cashBalance) { this.cashBalance = cashBalance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}