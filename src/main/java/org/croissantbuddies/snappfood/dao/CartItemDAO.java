package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.CartItem;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class CartItemDAO extends GenericDAO<CartItem, Long> {

    public CartItemDAO(SessionFactory sessionFactory) {
        super(CartItem.class, sessionFactory);
    }

    public List<CartItem> findByOrderId(Long orderId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM CartItem WHERE order.id = :oid", CartItem.class)
                    .setParameter("oid", orderId)
                    .list();
        }
    }

}