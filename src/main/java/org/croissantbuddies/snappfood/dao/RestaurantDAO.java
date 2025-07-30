package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.entity.Restaurant;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RestaurantDAO extends GenericDAO<Restaurant, Long> {
    public RestaurantDAO(SessionFactory sessionFactory) {
        super(Restaurant.class, sessionFactory);
    }
    public void save(Restaurant restaurant) {

        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(restaurant);
            transaction.commit();
            System.out.println("Restaurant saved successfully!");
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }

    public Restaurant findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Restaurant.class, id);
        }
    }

    public List<Restaurant> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Restaurant", Restaurant.class).list();
        }
    }

    public void update(Restaurant restaurant) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.update(restaurant);
            transaction.commit();
            System.out.println("Restaurant updated successfully!");
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }

    public void delete(Restaurant restaurant) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.delete(restaurant);
            transaction.commit();
            System.out.println("Restaurant deleted successfully!");
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }
    public List<Restaurant> findBySellerId(Long sellerId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Restaurant WHERE seller.id = :sellerId", Restaurant.class)
                    .setParameter("sellerId", sellerId)
                    .list();
        }
    }
    public List<Restaurant> getFilteredRestaurants(String search, List<String> keywords) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder("FROM Restaurant r WHERE 1=1");

            if (search != null && !search.trim().isEmpty()) {
                hql.append(" AND lower(r.name) LIKE :search");
            }

            if (keywords != null && !keywords.isEmpty()) {
                for (int i = 0; i < keywords.size(); i++) {
                    hql.append(" AND lower(r.name) LIKE :kw").append(i);
                }
            }

            Query<Restaurant> query = session.createQuery(hql.toString(), Restaurant.class);

            if (search != null && !search.trim().isEmpty()) {
                query.setParameter("search", "%" + search.toLowerCase() + "%");
            }

            if (keywords != null && !keywords.isEmpty()) {
                for (int i = 0; i < keywords.size(); i++) {
                    query.setParameter("kw" + i, "%" + keywords.get(i).toLowerCase() + "%");
                }
            }

            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public Restaurant findByIdWithMenusAndItems(Long id) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "SELECT DISTINCT r FROM Restaurant r " +
                                    "LEFT JOIN FETCH r.menus m " +
                                    "LEFT JOIN FETCH m.items " + // این قسمت‌ها باعث Eager Fetching می‌شوند
                                    "WHERE r.id = :id", Restaurant.class)
                    .setParameter("id", id)
                    .uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            session.close();
        }
    }

    public Restaurant findByIdWithFoods(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT DISTINCT r FROM Restaurant r " +
                                    "LEFT JOIN FETCH r.foods " +
                                    "WHERE r.id = :id", Restaurant.class)
                    .setParameter("id", id)
                    .uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Restaurant findByIdWithDetails(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT DISTINCT r FROM Restaurant r " +
                                    "LEFT JOIN FETCH r.menus " +
                                    "LEFT JOIN FETCH r.foods " +
                                    "WHERE r.id = :id", Restaurant.class)
                    .setParameter("id", id)
                    .uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Restaurant findByIdWithMenus(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT DISTINCT r FROM Restaurant r " +
                                    "LEFT JOIN FETCH r.menus " +
                                    "WHERE r.id = :id", Restaurant.class)
                    .setParameter("id", id)
                    .uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public List<Map<String, Object>> findAllWithAverageRating(String search, List<String> keywords) {
        try (Session session = sessionFactory.openSession()) {

            StringBuilder hql = new StringBuilder(
                    "SELECT r, AVG(rt.rating) FROM Restaurant r " +
                            "LEFT JOIN Rating rt ON rt.restaurantId = r.id WHERE 1=1 "
            );

            Map<String, Object> parameters = new HashMap<>();

            if (search != null && !search.trim().isEmpty()) {
                hql.append(" AND lower(r.name) LIKE :search");
                parameters.put("search", "%" + search.toLowerCase() + "%");
            }

            if (keywords != null && !keywords.isEmpty()) {

                hql.append(" AND EXISTS (SELECT 1 FROM r.foods f JOIN f.keywords k WHERE lower(k) IN (:keywords))");
                List<String> lowerCaseKeywords = keywords.stream().map(String::toLowerCase).collect(Collectors.toList());
                parameters.put("keywords", lowerCaseKeywords);
            }

            hql.append(" GROUP BY r.id");
            Query<Object[]> query = session.createQuery(hql.toString(), Object[].class);
            parameters.forEach(query::setParameter);
            List<Object[]> results = query.list();

            return results.stream().map(record -> {
                Restaurant restaurant = (Restaurant) record[0];
                Double avgRating = (Double) record[1];
                return Map.<String, Object>of(
                        "restaurant", restaurant,
                        "averageRating", avgRating == null ? 0.0 : avgRating
                );
            }).collect(Collectors.toList());
        }
    }
    public Restaurant findByIdWithSeller(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT r FROM Restaurant r JOIN FETCH r.seller WHERE r.id = :id", Restaurant.class)
                    .setParameter("id", id)
                    .uniqueResult();
        }
    }
    public List<Restaurant> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Restaurant WHERE id IN (:ids)", Restaurant.class)
                    .setParameterList("ids", ids)
                    .list();
        }
    }
}
