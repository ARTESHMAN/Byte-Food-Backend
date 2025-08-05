package org.croissantbuddies.snappfood.entity;

import jakarta.persistence.*;
import org.croissantbuddies.snappfood.ENUM.Role;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "users")
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    // --- تغییر اول: nullable = false و مقدار پیش‌فرض ---
    @Column(nullable = false, columnDefinition = "DOUBLE DEFAULT 0.0")
    private Double amount = 0.0;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Lob
    @Column(columnDefinition="LONGTEXT")
    private String profileImageBase64;

    @Column(nullable = true)
    private String bankName;

    @Column(nullable = true)
    private String accountNumber;

    public User(String fullName, String phone, String password, Role role,String address) {
        this.fullName = fullName;
        this.phone = phone;
        this.password = password;
        this.role = role;
        this.address = address;
        this.amount = 0.0;
    }

    public User() {
        this.amount = 0.0;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }
    public String getBankName() {
        return bankName;
    }
    public String getAccountNumber() {
        return accountNumber;
    }
    public void setBankName(String bankName) {
        this.bankName = bankName;
    }
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getProfileImageBase64() {
        return profileImageBase64;
    }

    public void setProfileImageBase64(String profileImageBase64) {
        this.profileImageBase64 = profileImageBase64;
    }

    public double getAmount() {
        return amount != null ? amount : 0.0;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}