package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Food;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class FoodDAO {

    private final SessionFactory sessionFactory;

    public FoodDAO(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(Food food) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(food);
            tx.commit();
        }
    }

    public void update(Food food) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(food);
            tx.commit();
        }
    }

    public Food findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Food.class, id);
        }
    }

    public void delete(Food food) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(food);
            tx.commit();
        }
    }

    public List<Food> getFilteredItems(String search, Integer minPrice, Integer maxPrice, List<String> keywords) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            StringBuilder hql = new StringBuilder("SELECT DISTINCT f FROM Food f JOIN FETCH f.restaurant r WHERE 1=1 ");
            if (search != null && !search.isBlank()) {
                hql.append("AND (LOWER(f.name) LIKE :search OR LOWER(r.name) LIKE :search) ");
            }
            if (minPrice != null) {
                hql.append("AND f.price >= :minPrice ");
            }
            if (maxPrice != null) {
                hql.append("AND f.price <= :maxPrice ");
            }
            if (keywords != null && !keywords.isEmpty()) {
                hql.append("AND EXISTS (SELECT 1 FROM f.keywords k WHERE k IN (:keywords)) ");
            }

            Query<Food> query = session.createQuery(hql.toString(), Food.class);

            if (search != null && !search.isBlank()) {
                query.setParameter("search", "%" + search.toLowerCase() + "%");
            }
            if (minPrice != null) {
                query.setParameter("minPrice", minPrice);
            }
            if (maxPrice != null) {
                query.setParameter("maxPrice", maxPrice);
            }
            if (keywords != null && !keywords.isEmpty()) {
                query.setParameter("keywords", keywords);
            }

            return query.getResultList();
        } finally {
            session.close();
        }
    }
}
