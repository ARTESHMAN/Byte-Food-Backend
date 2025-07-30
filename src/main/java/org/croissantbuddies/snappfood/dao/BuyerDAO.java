package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Buyer;
import org.hibernate.SessionFactory;

public class BuyerDAO extends GenericDAO<Buyer, Long> {

    public BuyerDAO(SessionFactory sessionFactory) {
        super(Buyer.class, sessionFactory);
    }


}