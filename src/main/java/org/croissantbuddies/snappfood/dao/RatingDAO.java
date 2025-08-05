package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Rating;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class RatingDAO extends GenericDAO<Rating, Long> {

    public RatingDAO(SessionFactory sessionFactory) {
        super(Rating.class, sessionFactory);
    }

    public List<Rating> findByFoodId(Long foodId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Rating r WHERE r.foodId = :fid", Rating.class)
                    .setParameter("fid", foodId)
                    .list();
        }
    }
    public boolean existsByOrderId(Long orderId) {
        try (Session session = sessionFactory.openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(r) FROM Rating r WHERE r.orderId = :orderId", Long.class
            ).setParameter("orderId", orderId).uniqueResult();
            return count != null && count > 0;
        }
    }
    public List<Rating> findByRestaurantId(Long restaurantId) {
        try (Session session = sessionFactory.openSession()) {
            List<Rating> ratings = session.createQuery(
                            "SELECT r FROM Rating r WHERE r.restaurantId = :restaurantId", Rating.class)
                    .setParameter("restaurantId", restaurantId).list();

            for (Rating rating : ratings) {
                Hibernate.initialize(rating.getImageBase64());
            }
            return ratings;
        }
    }
}