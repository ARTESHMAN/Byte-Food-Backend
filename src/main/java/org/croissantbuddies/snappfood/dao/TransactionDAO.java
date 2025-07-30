package org.croissantbuddies.snappfood.dao;

import jakarta.persistence.TypedQuery;
import org.croissantbuddies.snappfood.ENUM.OrderStatus;
import org.croissantbuddies.snappfood.entity.Transaction;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionDAO {

    private final SessionFactory sessionFactory;

    public TransactionDAO(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(Transaction transaction) {
        try (Session session = sessionFactory.openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            session.persist(transaction);
            tx.commit();
        }
    }

    public void update(Transaction transaction) {
        try (Session session = sessionFactory.openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            session.merge(transaction);
            tx.commit();
        }
    }

    public void delete(Transaction transaction) {
        try (Session session = sessionFactory.openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            session.remove(transaction);
            tx.commit();
        }
    }

    public Transaction findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Transaction.class, id);
        }
    }

    public List<Transaction> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Transaction", Transaction.class).list();
        }
    }

    public List<Transaction> findByUserId(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Transaction t WHERE t.userId = :userId", Transaction.class)
                    .setParameter("userId", userId)
                    .list();
        }
    }

    public List<Transaction> findByOrderId(Long orderId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Transaction t WHERE t.orderId = :orderId", Transaction.class)
                    .setParameter("orderId", orderId)
                    .list();
        }
    }

    public Transaction findByTransactionId(String transactionId) {
        try (Session session = sessionFactory.openSession()) {
            TypedQuery<Transaction> query = session.createQuery(
                    "FROM Transaction t WHERE t.transactionId = :txId", Transaction.class);
            query.setParameter("txId", transactionId);
            return query.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public List<Transaction> findAdminFiltered(Map<String, String> filters) {
        try (Session session = sessionFactory.openSession()) {
            StringBuilder queryStr = new StringBuilder("SELECT t FROM Transaction t WHERE 1=1");
            Map<String, Object> parameters = new HashMap<>();

            if (filters.containsKey("user") && filters.get("user") != null && !filters.get("user").isEmpty()) {
                try {
                    queryStr.append(" AND t.userId = :userId");
                    parameters.put("userId", Long.parseLong(filters.get("user")));
                } catch (NumberFormatException e) {
                }
            }

            if (filters.containsKey("method") && filters.get("method") != null && !filters.get("method").isEmpty()) {
                queryStr.append(" AND t.method = :method");
                parameters.put("method", filters.get("method"));
            }
            if (filters.containsKey("status") && filters.get("status") != null && !filters.get("status").isEmpty()) {
                try {
                    queryStr.append(" AND t.status = :status");
                    parameters.put("status", OrderStatus.valueOf(filters.get("status").toUpperCase().replace(" ", "_")));
                } catch (IllegalArgumentException e) {
                }
            }
            if (filters.containsKey("search") && filters.get("search") != null && !filters.get("search").isEmpty()) {
                queryStr.append(" AND (CAST(t.id AS string) LIKE :searchTerm OR t.transactionId LIKE :searchTerm)");
                parameters.put("searchTerm", "%" + filters.get("search").toLowerCase() + "%");
            }

            queryStr.append(" ORDER BY t.createdAt DESC");

            TypedQuery<Transaction> query = session.createQuery(queryStr.toString(), Transaction.class);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }

            return query.getResultList();
        }
    }
}