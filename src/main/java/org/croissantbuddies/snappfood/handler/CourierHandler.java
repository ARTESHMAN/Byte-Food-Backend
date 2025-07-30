package org.croissantbuddies.snappfood.handler;
import org.croissantbuddies.snappfood.dao.CourierDAO;
import org.croissantbuddies.snappfood.dao.RestaurantDAO;
import org.croissantbuddies.snappfood.dao.UserDAO;
import org.croissantbuddies.snappfood.entity.*;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.ENUM.OrderStatus;
import org.croissantbuddies.snappfood.dao.OrderDAO;

import org.croissantbuddies.snappfood.util.JwtUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;

public class CourierHandler implements HttpHandler {
    private final OrderDAO orderDAO;
    private static final Gson gson = new Gson();
    private final UserDAO userDAO;
    private final RestaurantDAO restaurantDAO;
    private final CourierDAO courierDAO;
    public CourierHandler(OrderDAO orderDAO, UserDAO userDAO, RestaurantDAO restaurantDAO, CourierDAO courierDAO) {
        this.orderDAO = orderDAO;
        this.userDAO = userDAO;
        this.restaurantDAO = restaurantDAO;
        this.courierDAO = courierDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/deliveries/available")) {
            getAvailableDeliveries(exchange);
        } else if ("PATCH".equalsIgnoreCase(exchange.getRequestMethod()) && path.matches("/deliveries/\\d+")) {
            changeStatusOfDeliveryRequest(exchange);
        }else if("GET".equalsIgnoreCase(exchange.getRequestMethod())&&path.equals("/deliveries/history")){
            getDeliveriesHistory(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }

    public void getAvailableDeliveries(HttpExchange exchange) throws IOException {

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<OrderStatus> statusesForCourier = List.of(
                    OrderStatus.AWAITING_COURIER_PICKUP,
                    OrderStatus.READY_FOR_PICKUP
            );

            List<Order> availableOrders = session.createQuery("FROM Order o WHERE o.status IN (:statuses)", Order.class)
                    .setParameterList("statuses", statusesForCourier)
                    .list();

            if (availableOrders.isEmpty()) {
                sendJsonResponse2(exchange, 200, new JSONArray());
                return;
            }

            Set<Long> vendorIds = availableOrders.stream().map(Order::getVendorId).collect(Collectors.toSet());
            Map<Long, Restaurant> restaurantMap = restaurantDAO.findAllByIds(new ArrayList<>(vendorIds))
                    .stream().collect(Collectors.toMap(Restaurant::getId, r -> r));

            JSONArray jsonArray = new JSONArray();
            for (Order order : availableOrders) {
                JSONObject json = new JSONObject();
                User user = userDAO.findById(order.getCustomerId());
                Restaurant restaurant = restaurantMap.get(order.getVendorId());

                json.put("id", order.getId());
                if (order.getDeliveryAddress().equalsIgnoreCase("Default Address")) {
                    json.put("delivery_address", user != null ? user.getAddress() : "N/A");
                } else {
                    json.put("delivery_address", order.getDeliveryAddress());
                }
                json.put("customer_id", order.getCustomerId());
                json.put("vendor_id", order.getVendorId());
                json.put("vendorName", restaurant != null ? restaurant.getName() : "Unknown");
                json.put("buyerName", user != null ? user.getFullName() : "N/A");
                json.put("pay_price", order.getPayPrice());
                json.put("status", order.getStatus().name().toLowerCase());
                json.put("created_at", order.getCreatedAt().toString());

                jsonArray.put(json);
            }

            sendJsonResponse2(exchange, 200, jsonArray);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    public void getDeliveriesHistory(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        try {
            String token = authHeader.substring(7);
            Long userId = JwtUtil.getUserIdFromToken(token);
            String userRole = JwtUtil.getRoleFromToken(token);

            if (!"COURIER".equalsIgnoreCase(userRole)) {
                sendErrorResponse(exchange, 403, "Forbidden: Only COURIER can get delivery history");
                return;
            }

            List<Order> orders = orderDAO.findAllByCourierId(userId);

            if (orders.isEmpty()) {
                sendJsonResponse2(exchange, 200, new JSONArray());
                return;
            }
            Set<Long> vendorIds = orders.stream().map(Order::getVendorId).collect(Collectors.toSet());
            Map<Long, Restaurant> restaurantMap = restaurantDAO.findAllByIds(new ArrayList<>(vendorIds))
                    .stream().collect(Collectors.toMap(Restaurant::getId, r -> r));

            JSONArray responseArray = new JSONArray();
            for (Order order : orders) {
                JSONObject json = new JSONObject();
                Restaurant restaurant = restaurantMap.get(order.getVendorId());
                User user = userDAO.findById(order.getCustomerId());
                json.put("id", order.getId());
                if (order.getDeliveryAddress().equalsIgnoreCase("Default Address")) {
                    json.put("delivery_address", user != null ? user.getAddress() : "N/A");
                } else {
                    json.put("delivery_address", order.getDeliveryAddress());
                }
                json.put("vendor_id", order.getVendorId());
                json.put("vendorName", restaurant != null ? restaurant.getName() : "Unknown");
                json.put("status", order.getStatus().name().toLowerCase());
                json.put("pay_price", order.getPayPrice());
                json.put("created_at", order.getCreatedAt().toString());
                User buyer = userDAO.findById(order.getCustomerId());
                json.put("buyerName", buyer != null ? buyer.getFullName() : "N/A");
                responseArray.put(json);
            }
            sendJsonResponse2(exchange, 200, responseArray);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    public void changeStatusOfDeliveryRequest(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long orderId;
        try {
            orderId = Long.parseLong(parts[2]);
        } catch (Exception e) {
            sendErrorResponse(exchange, 400, "Invalid Order ID in URL");
            return;
        }

        try {
            String token = authHeader.substring(7);
            Long courierId = JwtUtil.getUserIdFromToken(token);
            String userRole = JwtUtil.getRoleFromToken(token);

            if (!"COURIER".equalsIgnoreCase(userRole)) {
                sendErrorResponse(exchange, 403, "Forbidden: Only COURIER can change status");
                return;
            }

            JSONObject requestBody = parseRequestBody(exchange);
            String statusStr = requestBody.optString("status", null);
            if (statusStr == null) {
                sendErrorResponse(exchange, 400, "Missing status field");
                return;
            }

            Order order = orderDAO.findById(orderId);
            if (order == null) {
                sendErrorResponse(exchange, 404, "Order not found");
                return;
            }

            if (order.getCourierId() != null && !order.getCourierId().equals(courierId)) {
                sendErrorResponse(exchange, 409, "Conflict: Order has already been taken by another courier.");
                return;
            }

            OrderStatus newStatus = OrderStatus.valueOf(statusStr.toUpperCase());

            order.setStatus(newStatus);
            if (order.getCourierId() == null) {
                order.setCourierId(courierId);
            }
            if (newStatus == OrderStatus.COMPLETED) {
                double orderPrice = order.getPayPrice();
                double courierFee = orderPrice * 0.05;
                order.setCourierFee(courierFee);

                Courier courier = courierDAO.findById(courierId);
                if (courier != null) {
                    courier.setAmount(courier.getAmount() + courierFee);
                    userDAO.update(courier);
                }

                Restaurant restaurant = restaurantDAO.findByIdWithSeller(order.getVendorId()); // متد بهینه برای جلوگیری از خطای Lazy
                if (restaurant != null && restaurant.getSeller() != null) {
                    Seller seller = restaurant.getSeller();
                    double sellerShare = orderPrice - courierFee;
                    seller.setAmount(seller.getAmount() + sellerShare);
                    userDAO.update(seller);
                }
            }
            orderDAO.update(order);

            sendResponse(exchange, 200, "Order status updated successfully");

        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, "Invalid status value");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
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
    public void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.getResponseBody().close();
    }

    private void sendJsonResponse3(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
        }
    }
}
