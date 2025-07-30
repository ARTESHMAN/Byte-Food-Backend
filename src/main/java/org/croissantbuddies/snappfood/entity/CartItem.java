package org.croissantbuddies.snappfood.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cart_items")


public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @ManyToOne
    @JoinColumn(name = "buyer_id", nullable = true)
    private Buyer buyer;

    @Column(nullable = false)
    private int quantity;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;


    public CartItem() {}

    public CartItem(Food food, Buyer buyer, int quantity) {
        this.food = food;
        this.buyer = buyer;
        this.quantity = quantity;
    }

    public Long getId() { return id; }

    public Food getFood() { return food; }
    public void setFood(Food food) { this.food = food; }

    public Buyer getBuyer() { return buyer; }
    public void setBuyer(Buyer buyer) { this.buyer = buyer; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        return id != null && id.equals(cartItem.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}