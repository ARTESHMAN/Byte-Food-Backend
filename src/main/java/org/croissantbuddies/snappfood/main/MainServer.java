package org.croissantbuddies.snappfood.main;

import com.sun.net.httpserver.HttpServer;
import org.croissantbuddies.snappfood.dao.*;
import org.croissantbuddies.snappfood.handler.*;
import org.croissantbuddies.snappfood.util.HibernateUtil;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MainServer {
    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            RestaurantDAO restaurantDAO = new RestaurantDAO(HibernateUtil.getSessionFactory());
            UserDAO userDAO = new UserDAO(HibernateUtil.getSessionFactory());
            FoodDAO foodDAO = new FoodDAO(HibernateUtil.getSessionFactory());
            OrderDAO orderDAO = new OrderDAO(HibernateUtil.getSessionFactory());
            TransactionDAO transactionDAO = new TransactionDAO(HibernateUtil.getSessionFactory());
            CouponDAO couponDAO = new CouponDAO(HibernateUtil.getSessionFactory());
            RatingDAO ratingDAO = new RatingDAO(HibernateUtil.getSessionFactory());
            BuyerDAO buyerDAO = new BuyerDAO(HibernateUtil.getSessionFactory());
            MenuDAO menuDAO = new MenuDAO(HibernateUtil.getSessionFactory());
            server.createContext("/auth", new AuthHandler());
            server.createContext("/restaurants", new RestaurantHandler(restaurantDAO, userDAO,foodDAO,orderDAO,menuDAO,couponDAO));
            server.createContext("/deliveries", new CourierHandler(orderDAO,userDAO,restaurantDAO,new CourierDAO(HibernateUtil.getSessionFactory())));
            server.createContext("/transactions", new TransactionsHandler(userDAO,orderDAO,transactionDAO));
            server.createContext("/wallet", new WalletHandler(userDAO));
            server.createContext("/payment", new PaymentHandler(userDAO, orderDAO));
            server.createContext("/admin", new AdminHandler(restaurantDAO,userDAO,foodDAO,orderDAO,transactionDAO,couponDAO));
            server.createContext("/buyers", new BuyerHandler(restaurantDAO, ratingDAO, orderDAO, buyerDAO, foodDAO, couponDAO,userDAO));
            server.setExecutor(null);

            System.out.println("Server started at http://localhost:8000");
            server.start();

        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
