package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Coupon;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import java.util.List;

public class CouponDAO extends GenericDAO<Coupon, Long> {

    public CouponDAO(SessionFactory sessionFactory) {
        super(Coupon.class, sessionFactory);
    }

    public Coupon findByCode(String code) {
        try (Session session = sessionFactory.openSession()) {
            Query<Coupon> query = session.createQuery("FROM Coupon c WHERE c.couponCode = :code", Coupon.class);
            query.setParameter("code", code);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Coupon> findByRestaurantId(Long restaurantId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Coupon c WHERE c.restaurant.id = :restaurantId", Coupon.class)
                    .setParameter("restaurantId", restaurantId)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}