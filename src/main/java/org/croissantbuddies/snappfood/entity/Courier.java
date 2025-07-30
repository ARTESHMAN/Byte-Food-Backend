package org.croissantbuddies.snappfood.entity;

import jakarta.persistence.*;
import org.croissantbuddies.snappfood.ENUM.Role;
import org.croissantbuddies.snappfood.ENUM.Validitaion;

@Entity
@Table(name = "couriers")

public class Courier extends User {



    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Validitaion status = Validitaion.INVALID;


    public Courier() {
    }

    public Courier(String fullName, String phone, String password, String bankName, String accountNumber,String address) {
        super(fullName, phone, password, Role.COURIER,address);

    }
    public Validitaion getStatus() {
        return status;
    }

    public void setStatus(Validitaion status) {
        this.status = status;
    }
}
