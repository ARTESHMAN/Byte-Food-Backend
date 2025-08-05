package org.croissantbuddies.snappfood.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.croissantbuddies.snappfood.ENUM.OrderStatus;
import org.croissantbuddies.snappfood.entity.CartItem;
import org.croissantbuddies.snappfood.entity.Order;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.time.LocalDate;
import java.util.*;

public class OrderDAO {

    private final SessionFactory sessionFactory;

    public OrderDAO(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(Order order) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(order);
            tx.commit();
        }
    }

    public void update(Order order) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(order);
            tx.commit();
        }
    }

    public void delete(Order order) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(order);
            tx.commit();
        }
    }

    public Order findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Order.class, id);
        }
    }
    public List<Order> findAllByCourierId(Long courierId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Order o WHERE o.courierId = :courierId", Order.class)
                    .setParameter("courierId", courierId)
                    .list();
        }
    }
    public List<Order> findHistoryForCourier(Long courierId, String searchTerm, String vendorFilter) {
        try (Session session = sessionFactory.openSession()) {

            StringBuilder queryStr = new StringBuilder("SELECT o FROM Order o WHERE o.courierId = :courierId");
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("courierId", courierId);

            if (vendorFilter != null && !vendorFilter.trim().isEmpty()) {
                queryStr.append(" AND o.vendorId = :vendorId");
                parameters.put("vendorId", Long.parseLong(vendorFilter));
            }

            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                queryStr.append(" AND LOWER(o.deliveryAddress) LIKE :searchTerm");
                parameters.put("searchTerm", "%" + searchTerm.toLowerCase() + "%");
            }

            queryStr.append(" ORDER BY o.createdAt DESC");

            TypedQuery<Order> query = session.createQuery(queryStr.toString(), Order.class);

            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }

            return query.getResultList();
        }
    }

    public List<Order> findByRestaurantId(Long restaurantId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Order o WHERE o.vendorId = :restaurantId", Order.class)
                    .setParameter("restaurantId", restaurantId)
                    .list();
        }
    }
    public List<Order> findByStatus(OrderStatus status) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Order o WHERE o.status = :status", Order.class)
                    .setParameter("status", status)
                    .list();
        }
    }

    public List<Order> findByCustomerId(Long customerId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Order o WHERE o.customerId = :customerId ORDER BY o.createdAt DESC", Order.class)
                    .setParameter("customerId", customerId)
                    .list();
        }
    }

    public List<Order> findAdminFiltered(Map<String, String> filters) {
        try (Session session = sessionFactory.openSession()) {
            StringBuilder queryStr = new StringBuilder("SELECT o FROM Order o WHERE 1=1");
            Map<String, Object> parameters = new HashMap<>();

            if (filters.containsKey("vendor")) {
                queryStr.append(" AND o.vendorId = :vendorId");
                parameters.put("vendorId", Long.parseLong(filters.get("vendor")));
            }

            if (filters.containsKey("courier")) {
                queryStr.append(" AND o.courierId = :courierId");
                parameters.put("courierId", Long.parseLong(filters.get("courier")));
            }

            if (filters.containsKey("customer")) {
                queryStr.append(" AND o.customerId = :customerId");
                parameters.put("customerId", Long.parseLong(filters.get("customer")));
            }

            if (filters.containsKey("status")) {
                queryStr.append(" AND o.status = :status");
                parameters.put("status", OrderStatus.valueOf(filters.get("status").toUpperCase()));
            }

            if (filters.containsKey("search")) {
                queryStr.append(" AND (CAST(o.id AS string) LIKE :searchTerm OR LOWER(o.deliveryAddress) LIKE :searchTerm)");
                parameters.put("searchTerm", "%" + filters.get("search").toLowerCase() + "%");
            }

            queryStr.append(" ORDER BY o.createdAt DESC");

            TypedQuery<Order> query = session.createQuery(queryStr.toString(), Order.class);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            List<Order> orders = query.getResultList();
            for (Order order : orders) {
                Hibernate.initialize(order.getItems());
                if (order.getItems() != null) {
                    for (CartItem item : order.getItems()) {
                        Hibernate.initialize(item.getFood());
                    }
                }
            }
            return orders;
        }
    }
    public List<Order> findByIdsWithItems(Set<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT DISTINCT o FROM Order o " +
                                    "LEFT JOIN FETCH o.items i " +
                                    "LEFT JOIN FETCH i.food " +
                                    "WHERE o.id IN (:orderIds)", Order.class)
                    .setParameter("orderIds", orderIds)
                    .list();
        }
    }
    public List<Order> findByCustomerIdFiltered(Long customerId, String statusFilter, LocalDate startDate, LocalDate endDate) {
        try (Session session = sessionFactory.openSession()) {
            StringBuilder queryStr = new StringBuilder("FROM Order o WHERE o.customerId = :customerId");
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("customerId", customerId);

            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                queryStr.append(" AND o.status = :status");
                parameters.put("status", OrderStatus.valueOf(statusFilter.toUpperCase()));
            }

            if (startDate != null) {
                queryStr.append(" AND o.createdAt >= :startDate");
                parameters.put("startDate", startDate.atStartOfDay());
            }
            if (endDate != null) {
                queryStr.append(" AND o.createdAt < :endDate");
                parameters.put("endDate", endDate.plusDays(1).atStartOfDay());
            }

            queryStr.append(" ORDER BY o.createdAt DESC");

            Query<Order> query = session.createQuery(queryStr.toString(), Order.class);
            parameters.forEach(query::setParameter);
            return query.list();
        }
    }
}
