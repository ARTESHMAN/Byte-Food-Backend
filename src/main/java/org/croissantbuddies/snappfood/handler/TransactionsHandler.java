package org.croissantbuddies.snappfood.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.dao.OrderDAO;
import org.croissantbuddies.snappfood.dao.TransactionDAO;
import org.croissantbuddies.snappfood.dao.UserDAO;
import org.croissantbuddies.snappfood.entity.Order;
import org.croissantbuddies.snappfood.entity.Transaction;
import org.croissantbuddies.snappfood.entity.User;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.croissantbuddies.snappfood.ENUM.OrderStatus.WAITING_VENDOR;

public class TransactionsHandler implements HttpHandler {
    private final UserDAO userDAO;
    private final OrderDAO orderDAO;
    private final TransactionDAO transactionDAO;
    public TransactionsHandler(UserDAO userDAO, OrderDAO orderDAO, TransactionDAO transactionDAO) {
        this.userDAO = userDAO;
        this.orderDAO = orderDAO;
        this.transactionDAO = transactionDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());
        if ("POST".equalsIgnoreCase(method) && relativePath.equals("/online")) {
            makeAnOnlinePaymentForOrder(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }
    public void makeAnOnlinePaymentForOrder(HttpExchange exchange) throws IOException {
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
        User user=userDAO.findById(userId);
        if (user == null) {
            sendErrorResponse(exchange, 404, "Not Found");
            return;
        }
        JSONObject body = parseRequestBody(exchange);
        long orderId = body.optLong("order_id", -1);
        Order order = orderDAO.findById(orderId);
        String method = body.optString("method", null);
        if((user.getAmount()-order.getPayPrice())>=0){
            user.setAmount(user.getAmount()-order.getPayPrice());
            userDAO.update(user);
            order.setStatus(WAITING_VENDOR);
            Transaction transaction = new Transaction();
            transaction.setOrderId(orderId);
            transaction.setUserId(userId);
            transaction.setAmount(order.getPayPrice());
            transaction.setMethod(method);
            transaction.setStatus(WAITING_VENDOR);
            transaction.setCreatedAt(LocalDateTime.now());
            transactionDAO.save(transaction);

            JSONObject response = new JSONObject();
            response.put("id", "");
            response.put("order_id", order.getId());
            response.put("method", method);
            response.put("status", "success");
            sendResponse(exchange, 200, response.toString());
        } else {
            sendErrorResponse(exchange, 403, "payment failed");
        }
    }
public void sendJsonResponse2(HttpExchange exchange, int statusCode, JSONArray jsonArray) throws IOException {
    byte[] bytes = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    OutputStream os = exchange.getResponseBody();
    os.write(bytes);
    os.close();
}
public void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
    byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
    exchange.sendResponseHeaders(statusCode, responseBytes.length);
    exchange.getResponseBody().write(responseBytes);
    exchange.getResponseBody().close();
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
