package org.croissantbuddies.snappfood.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.dao.UserDAO;
import org.croissantbuddies.snappfood.entity.User;
import org.croissantbuddies.snappfood.util.HibernateUtil; //  این import را اضافه کنید
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.hibernate.Session; //  این import را اضافه کنید
import org.hibernate.Transaction; //  این import را اضافه کنید
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class WalletHandler implements HttpHandler {
    private final UserDAO userDAO;

    public WalletHandler(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("POST".equalsIgnoreCase(method) && path.endsWith("/top-up")) {
            topUpUserWallet(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }

    public void topUpUserWallet(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }

        Session session = null;
        Transaction tx = null;
        try {
            String token = authHeader.substring(7);
            Long userId = JwtUtil.getUserIdFromToken(token);

            JSONObject body = parseRequestBody(exchange);
            double topUpAmount = body.optDouble("amount", -1);
            if (topUpAmount <= 0) {
                sendErrorResponse(exchange, 400, "Invalid top-up amount");
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();

            User user = session.get(User.class, userId);
            if (user == null) {
                sendErrorResponse(exchange, 404, "User not found");
                if (tx != null) tx.rollback();
                return;
            }

            user.setAmount(user.getAmount() + topUpAmount);


            tx.commit();

            JSONObject response = new JSONObject();
            response.put("message", "Wallet topped up successfully");
            response.put("new_amount", user.getAmount());
            sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        JSONObject json = new JSONObject();
        json.put("error", message);
        sendJsonResponse(exchange, statusCode, json);
    }

    public void sendJsonResponse(HttpExchange exchange, int statusCode, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private JSONObject parseRequestBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        StringBuilder textBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return new JSONObject(textBuilder.toString());
    }
}