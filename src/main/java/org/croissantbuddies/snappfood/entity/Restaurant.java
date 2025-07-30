package org.croissantbuddies.snappfood.entity;

import org.croissantbuddies.snappfood.ENUM.Validitaion;
import org.croissantbuddies.snappfood.entity.Menu;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "restaurants")
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Coupon> coupons = new ArrayList<>();
    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    @Lob
    @Column(columnDefinition="LONGTEXT")
    private String profileImageBase64;

    @Column(name = "tax_fee")
    private double taxFee;

    @Column(name = "additional_fee")
    private double additionalFee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Food> foods = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Menu> menus = new ArrayList<>();

    public Restaurant() {}

    public Restaurant(String name, String address, String phone, String profileImageBase64,
                      double taxFee, double additionalFee, Seller seller) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.profileImageBase64 = profileImageBase64;
        this.taxFee = taxFee;
        this.additionalFee = additionalFee;
        this.seller = seller;
    }

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLogoBase64() { return profileImageBase64; }
    public void setLogoBase64(String logoBase64) { this.profileImageBase64 = logoBase64; }

    public double getTaxFee() { return taxFee; }
    public void setTaxFee(double taxFee) { this.taxFee = taxFee; }

    public double getAdditionalFee() { return additionalFee; }
    public void setAdditionalFee(double additionalFee) { this.additionalFee = additionalFee; }

    public Seller getSeller() { return seller; }
    public void setSeller(Seller seller) { this.seller = seller; }

    public List<Food> getFoods() { return foods; }
    public void setFoods(List<Food> foods) { this.foods = foods; }

    public List<Menu> getMenus() { return menus; }
    public void setMenus(List<Menu> menus) { this.menus = menus; }
    public List<Coupon> getCoupons() {
        return coupons;
    }

    public void setCoupons(List<Coupon> coupons) {
        this.coupons = coupons;
    }

    public void addFood(Food food) {
        foods.add(food);
        food.setRestaurant(this);
    }

    public void removeFood(Food food) {
        foods.remove(food);
        food.setRestaurant(null);
    }


    public void addMenuItem(String menuTitle, Food food) {
        Menu menu = menus.stream()
                .filter(m -> m.getTitle().equalsIgnoreCase(menuTitle))
                .findFirst()
                .orElseGet(() -> {
                    Menu newMenu = new Menu(menuTitle, this);
                    menus.add(newMenu);
                    return newMenu;
                });
        menu.addItem(food);
    }

    public void removeMenuItem(String menuTitle, Food food) {
        menus.stream()
                .filter(m -> m.getTitle().equalsIgnoreCase(menuTitle))
                .findFirst()
                .ifPresent(menu -> menu.removeItem(food));
    }
    public void setId(Long id) {
        this.id = id;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Restaurant that = (Restaurant) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
