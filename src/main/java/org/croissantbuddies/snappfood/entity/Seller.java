package org.croissantbuddies.snappfood.entity;

import jakarta.persistence.*;
import org.croissantbuddies.snappfood.ENUM.Role;
import org.croissantbuddies.snappfood.ENUM.Validitaion;

@Entity
@Table(name = "sellers")
public class Seller extends User {

    @Column(length = 1000)
    private String bio;
    @Column(length = 1000)
    private String restoInfo;
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Validitaion status = Validitaion.INVALID;
    public Seller() {}
    public Seller(String fullName, String phone, String password, String address) {
        super(fullName, phone, password, Role.SELLER, address);
    }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getRestoInfo() { return restoInfo; }
    public void setRestoInfo(String restoInfo) { this.restoInfo = restoInfo; }
    public Validitaion getStatus() { return status; }
    public void setStatus(Validitaion status) { this.status = status; }
}