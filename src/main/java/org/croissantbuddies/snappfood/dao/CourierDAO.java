package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Courier;
import org.hibernate.SessionFactory;

public class CourierDAO extends GenericDAO<Courier, Long> {

    public CourierDAO(SessionFactory sessionFactory) {
        super(Courier.class, sessionFactory);
    }


}
