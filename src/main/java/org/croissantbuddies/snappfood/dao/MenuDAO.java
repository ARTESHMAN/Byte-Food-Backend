package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Menu;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

public class MenuDAO {
    private final SessionFactory sessionFactory;

    public MenuDAO(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(Menu menu) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(menu);
            tx.commit();
        }
    }

    public void update(Menu menu) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(menu);
            tx.commit();
        }
    }

    public Menu findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Menu.class, id);
        }
    }

    public void delete(Menu menu) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(menu);
            tx.commit();
        }
    }
}
