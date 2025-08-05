package org.croissantbuddies.snappfood.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.dao.OrderDAO;
import org.croissantbuddies.snappfood.dao.UserDAO;
import org.croissantbuddies.snappfood.entity.Order;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PaymentHandler implements HttpHandler {

    private final UserDAO userDAO;
    private final OrderDAO orderDAO;

    public PaymentHandler(UserDAO userDAO, OrderDAO orderDAO) {
        this.userDAO = userDAO;
        this.orderDAO = orderDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());
        if ("GET".equalsIgnoreCase(method)) {
            getUserTransactionHistory(exchange);
        }else{
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }
    public void getUserTransactionHistory(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        String token = authHeader.substring(7);
        Long userId;
        String userRole;
        try {
            userId = JwtUtil.getUserIdFromToken(token);
            userRole = JwtUtil.getRoleFromToken(token);
        } catch (Exception e) {
            sendErrorResponse(exchange, 401, "Invalid token: " + e.getMessage());
            return;
        }
        JSONArray jsonArray = new JSONArray();
        List<Order> order = orderDAO.findAllByCourierId(userId);
        for (Order o : order) {
            JSONObject json = new JSONObject();
            json.put("id", o.getCustomerId());
            json.put("order_id", o.getId());
            json.put("user_id", o.getCustomerId());
            json.put("method",o.getPaymentMethod());
            json.put("status",o.getStatus());
            jsonArray.put(json);
        }
        sendJsonResponse2(exchange, 200, jsonArray);
    }
    public void sendJsonResponse2(HttpExchange exchange, int statusCode, JSONArray jsonArray) throws IOException {
        byte[] bytes = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
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

}

