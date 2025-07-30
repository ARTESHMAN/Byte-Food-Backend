package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Seller;
import org.hibernate.SessionFactory;

public class SellerDAO extends GenericDAO<Seller, Long> {

    public SellerDAO(SessionFactory sessionFactory) {
        super(Seller.class, sessionFactory);
    }

}
