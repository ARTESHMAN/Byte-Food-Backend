package org.croissantbuddies.snappfood.dao;

import org.croissantbuddies.snappfood.ENUM.Validitaion;
import org.croissantbuddies.snappfood.dto.StatusResult;
import org.croissantbuddies.snappfood.entity.Courier;
import org.croissantbuddies.snappfood.entity.Seller;
import org.croissantbuddies.snappfood.entity.User;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.croissantbuddies.snappfood.util.PasswordUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import javax.xml.namespace.QName;

public class UserDAO extends GenericDAO<User, Long> {

    public UserDAO(SessionFactory sessionFactory) {
        super(User.class, sessionFactory);
    }

    public StatusResult login(String phone, String rawPassword) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            String hql = "FROM User u WHERE u.phone = :phone";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("phone", phone);
            User user = query.uniqueResult();

            if (user != null) {
                String hashedInput = PasswordUtil.hashPassword(rawPassword);
                if (user.getPassword().equals(hashedInput)) {
                    String token = JwtUtil.generateToken(user.getId(), user.getRole().name());
                    return new StatusResult(200, "User logged in successfully", user.getId(), token, user);
                }
            }

            return new StatusResult(401, "Invalid phone or password");

        } catch (Exception e) {
            e.printStackTrace();
            return new StatusResult(500, "Internal server error");

        } finally {
            session.close();
        }
    }


    public StatusResult register(User user) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            session.beginTransaction();
            if (user.getRole() == null) {
                return new StatusResult(400, "User role must be set");
            }
            String checkPhone = "FROM User u WHERE u.phone = :number";
            Query<User> phoneQuery = session.createQuery(checkPhone, User.class);
            phoneQuery.setParameter("number", user.getPhone());
            if (phoneQuery.uniqueResult() != null) {
                return new StatusResult(409, "Phone number already exists");
            }

            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                String checkEmail = "FROM User u WHERE u.email = :email";
                Query<User> emailQuery = session.createQuery(checkEmail, User.class);
                emailQuery.setParameter("email", user.getEmail());
                if (emailQuery.uniqueResult() != null) {
                    return new StatusResult(409, "Email already exists");
                }
            }
            user.setPassword(PasswordUtil.hashPassword(user.getPassword()));
            session.persist(user);
            session.getTransaction().commit();
            String token = JwtUtil.generateToken(user.getId(), user.getRole().name());
            return new StatusResult(200, "User registered successfully", user.getId(),token);

        } catch (Exception e) {
            if (session.getTransaction() != null) session.getTransaction().rollback();
            e.printStackTrace();
            return new StatusResult(400, "Invalid input data");
        } finally {
            session.close();
        }

}
    public void update(User user) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.merge(user);
            transaction.commit();
            System.out.println("User updated successfully in database!");
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Error updating user: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public User findByPhone(String phone) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            String hql = "FROM User u WHERE u.phone = :phone";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("phone", phone);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            session.close();
        }
    }

    public User findByEmail(String email) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            String hql = "FROM User u WHERE u.email = :email";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("email", email);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            session.close();
        }
    }
    public boolean updateUserStatus(Long userId, Validitaion newStatus) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            User user = session.get(User.class, userId);

            if (user == null) {
                if (transaction != null) transaction.rollback();
                return false;
            }

            if (user instanceof Seller) {
                ((Seller) user).setStatus(newStatus);
            } else if (user instanceof Courier) {
                ((Courier) user).setStatus(newStatus);
            } else {
                if (transaction != null) transaction.rollback();
                return false;
            }

            session.merge(user);
            transaction.commit();
            System.out.println("SUCCESS: Status for user " + userId + " updated to " + newStatus);
            return true;
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
            return false;
        }
    }
}
