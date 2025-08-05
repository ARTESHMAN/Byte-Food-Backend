package org.croissantbuddies.snappfood.entity;

import jakarta.persistence.*;
import org.croissantbuddies.snappfood.ENUM.Role;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "buyers")


public class Buyer extends User {

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL)
    private List<CartItem> cart = new ArrayList<>();
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "buyer_favorite_restaurants",
            joinColumns = @JoinColumn(name = "buyer_id"),
            inverseJoinColumns = @JoinColumn(name = "restaurant_id")
    )
    private List<Restaurant> favoriteRestaurants = new ArrayList<>();

    public List<Restaurant> getFavoriteRestaurants() {
        return favoriteRestaurants;
    }

    public void setFavoriteRestaurants(List<Restaurant> favoriteRestaurants) {
        this.favoriteRestaurants = favoriteRestaurants;
    }

    public Buyer() {}

    public Buyer(String fullName, String phone, String password, String role, String address) {
        super(fullName, phone, password, Role.BUYER,address);
    }

    public List<CartItem> getCart() { return cart; }
    public void setCart(List<CartItem> cart) { this.cart = cart; }


    public void addToCart(Food food, int quantity) {
        for (CartItem item : cart) {
            if (item.getFood().getId().equals(food.getId())) {
                item.setQuantity(item.getQuantity() + quantity);
                return;
            }
        }
        CartItem newItem = new CartItem(food, this, quantity);
        cart.add(newItem);
    }


    public void removeFromCart(Food food) {
        CartItem itemToRemove = null;
        for (CartItem item : this.cart) {
            if (item.getFood().getId().equals(food.getId())) {
                itemToRemove = item;
                break;
            }
        }
        if (itemToRemove != null) {
            this.cart.remove(itemToRemove);
            itemToRemove.setBuyer(null);
        }
    }

    public double totalCartPrice() {
        double total = 0.0;
        for (CartItem item : cart) {
            BigDecimal price = BigDecimal.valueOf(item.getFood().getPrice());
            total +=price.doubleValue() * item.getQuantity();
        }
        return total;
    }

}