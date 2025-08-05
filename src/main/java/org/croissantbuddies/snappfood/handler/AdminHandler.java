package org.croissantbuddies.snappfood.handler;

import com.google.gson.Gson;
import org.hibernate.Session;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.ENUM.CouponType;
import org.croissantbuddies.snappfood.ENUM.Validitaion;
import org.croissantbuddies.snappfood.dao.*;
import org.croissantbuddies.snappfood.dto.OrderDTO;
import org.croissantbuddies.snappfood.entity.*;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.hibernate.Hibernate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class AdminHandler implements HttpHandler {
    private final RestaurantDAO restaurantDAO;
    private final UserDAO userDAO;
    private final FoodDAO foodDAO;
    private final OrderDAO orderDAO;
    private final TransactionDAO transactionDAO;
    private final CouponDAO couponDAO;
    private final Gson gson = new Gson();

    public AdminHandler(RestaurantDAO restaurantDAO, UserDAO userDAO, FoodDAO foodDAO, OrderDAO orderDAO, TransactionDAO transactionDAO, CouponDAO couponDAO) {
        this.restaurantDAO = restaurantDAO;
        this.userDAO = userDAO;
        this.foodDAO = foodDAO;
        this.orderDAO = orderDAO;
        this.transactionDAO = transactionDAO;
        this.couponDAO = couponDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());

        if (relativePath.startsWith("/coupons")) {
            if (relativePath.matches("/coupons/\\d+")) {
                switch (method.toUpperCase()) {
                    case "GET":
                        getCouponById(exchange);
                        break;
                    case "PUT":
                        updateCoupon(exchange);
                        break;
                    case "DELETE":
                        deleteCoupon(exchange);
                        break;
                    default:
                        sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            }

            else if (relativePath.equals("/coupons")) {
                switch (method.toUpperCase()) {
                    case "GET":
                        getAllCoupons(exchange);
                        break;
                    case "POST":
                        createCoupon(exchange);
                        break;
                    default:
                        sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            } else {
                sendErrorResponse(exchange, 404, "Not Found");
            }
            return;
        }

        if ("GET".equalsIgnoreCase(method) && relativePath.equals("/users")) {
            listAllUsers(exchange);
        } else if ("patch".equalsIgnoreCase(method) && relativePath.matches("/users/\\d+/status")) {
            updateUserApprovalStatus(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.equals("/orders")) {
            getOrders(exchange);
        }else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/orders/\\d+")) {
                getOrderDetails(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.equals("/transactions")) {
            getTransactions(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }

    private void getAllCoupons(HttpExchange exchange) throws IOException {

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            if (!"ADMIN".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            List<Coupon> coupons = couponDAO.findAll();

            JSONArray responseArray = new JSONArray();
            for (Coupon coupon : coupons) {
                responseArray.put(couponToJson(coupon));
            }

            sendJsonResponse2(exchange, 200, responseArray);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private void getCouponById(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            if (!"ADMIN".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            long couponId = Long.parseLong(parts[parts.length - 1]);

            Coupon coupon = couponDAO.findById(couponId);
            if (coupon == null) {
                sendErrorResponse(exchange, 404, "Not Found: Coupon with this ID does not exist.");
                return;
            }

            JSONObject responseJson = couponToJson(coupon);
            sendJsonResponse(exchange, 200, responseJson);

        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Bad Request: Invalid coupon ID format.");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void createCoupon(HttpExchange exchange) throws IOException {
        try {
            JSONObject json = new JSONObject(parseRequestBody2(exchange));
            System.out.println("Received JSON for new coupon: " + json.toString(2));

            String couponCode = json.optString("coupon_code", null);
            String typeStr = json.optString("type", null);
            String startDateStr = json.optString("start_date", null);
            String endDateStr = json.optString("end_date", null);
            int userCount = json.optInt("user_count", -1);

            double value = json.optDouble("value", -1.0);
            double minPrice = json.optDouble("min_price", -1.0);


            System.out.println("couponCode: " + couponCode);
            System.out.println("typeStr: " + typeStr);
            System.out.println("value: " + value + ", minPrice: " + minPrice + ", userCount: " + userCount);
            System.out.println("startDate: " + startDateStr + ", endDate: " + endDateStr);

            if (couponCode == null || couponCode.trim().isEmpty()
                    || typeStr == null || typeStr.trim().isEmpty()
                    || !json.has("value") || Double.isNaN(value) || value < 0
                    || !json.has("min_price") || Double.isNaN(minPrice) || minPrice < 0
                    || userCount < 0
                    || startDateStr == null || startDateStr.trim().isEmpty()
                    || endDateStr == null || endDateStr.trim().isEmpty()) {

                sendErrorResponse(exchange, 400, "Invalid Input: Missing or invalid required fields.");
                return;
            }



            CouponType type = CouponType.valueOf(typeStr.toUpperCase());
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            if (endDate.isBefore(startDate)) {
                sendErrorResponse(exchange, 400, "Invalid Input: End date cannot be before start date.");
                return;
            }

            Coupon newCoupon = new Coupon(couponCode, type, value, minPrice, userCount, startDate, endDate);
            couponDAO.save(newCoupon);
            JSONObject responseJson = couponToJson(newCoupon);
            sendJsonResponse(exchange, 201, responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }


    private void updateCoupon(HttpExchange exchange) throws IOException {

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            if (!"ADMIN".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            long couponId = Long.parseLong(parts[parts.length - 1]);

            Coupon existingCoupon = couponDAO.findById(couponId);
            if (existingCoupon == null) {
                sendErrorResponse(exchange, 404, "Not Found: Coupon with this ID does not exist.");
                return;
            }

            JSONObject json = new JSONObject(parseRequestBody2(exchange));

            String newCode = json.optString("coupon_code", existingCoupon.getCouponCode());

            if (!newCode.equals(existingCoupon.getCouponCode())) {
                if (couponDAO.findByCode(newCode) != null) {
                    sendErrorResponse(exchange, 409, "Conflict: Another coupon with this code already exists.");
                    return;
                }
                existingCoupon.setCouponCode(newCode);
            }

            existingCoupon.setType(CouponType.valueOf(json.optString("type", existingCoupon.getType().name()).toUpperCase()));
            existingCoupon.setValue(json.optDouble("value", existingCoupon.getValue()));
            existingCoupon.setMinPrice(json.optDouble("min_price", existingCoupon.getMinPrice()));
            existingCoupon.setUserCount(json.optInt("user_count", existingCoupon.getUserCount()));
            existingCoupon.setStartDate(LocalDate.parse(json.optString("start_date", existingCoupon.getStartDate().toString())));
            existingCoupon.setEndDate(LocalDate.parse(json.optString("end_date", existingCoupon.getEndDate().toString())));

            if (existingCoupon.getEndDate().isBefore(existingCoupon.getStartDate())) {
                sendErrorResponse(exchange, 400, "Invalid Input: End date cannot be before start date.");
                return;
            }

            couponDAO.update(existingCoupon);

            JSONObject responseJson = couponToJson(existingCoupon);
            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private void deleteCoupon(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            if (!"ADMIN".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            long couponId = Long.parseLong(parts[parts.length - 1]);

            Coupon coupon = couponDAO.findById(couponId);
            if (coupon == null) {
                sendErrorResponse(exchange, 404, "Not Found: Coupon with this ID does not exist.");
                return;
            }

            couponDAO.delete(coupon);

            sendJasonResponse3(exchange, 200, "Coupon deleted successfully");

        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Bad Request: Invalid coupon ID format.");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private JSONObject couponToJson(Coupon coupon) {
        JSONObject json = new JSONObject();
        json.put("id", coupon.getId());
        json.put("coupon_code", coupon.getCouponCode());
        json.put("type", coupon.getType().name().toLowerCase());
        json.put("value", coupon.getValue());
        json.put("min_price", coupon.getMinPrice());
        json.put("user_count", coupon.getUserCount());
        json.put("start_date", coupon.getStartDate().toString());
        json.put("end_date", coupon.getEndDate().toString());
        return json;
    }



    private void getTransactions(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: Token not provided.");
            return;
        }

        String token = authHeader.substring(7);
        String userRole;
        try {
            userRole = JwtUtil.getRoleFromToken(token);
        } catch (Exception e) {
            sendErrorResponse(exchange, 401, "Unauthorized: Invalid token.");
            return;
        }

        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Access denied. Admin role required.");
            return;
        }

        Map<String, String> filters = parseQueryParams(exchange.getRequestURI().getQuery());
        List<Transaction> transactions = transactionDAO.findAdminFiltered(filters);

        JSONArray responseArray = new JSONArray();
        for (Transaction tx : transactions) {
            JSONObject json = new JSONObject();
            json.put("id", tx.getId());
            json.put("order_id", tx.getOrderId());
            json.put("user_id", tx.getUserId());
            json.put("method", tx.getMethod());
            if (tx.getStatus() != null) {
                json.put("status", tx.getStatus().name().toLowerCase());
            } else {
                json.put("status", JSONObject.NULL);
            }
            String buyerName = "N/A";
            String sellerName = "N/A";
            if (tx.getOrderId() != null) {
                Order order = orderDAO.findById(tx.getOrderId());
                if (order != null) {
                    User buyer = userDAO.findById(order.getCustomerId());
                    if (buyer != null) {
                        buyerName = buyer.getFullName();
                    }

                    Restaurant restaurant = restaurantDAO.findByIdWithSeller(order.getVendorId());
                    if (restaurant != null && restaurant.getSeller() != null) {
                        sellerName = restaurant.getSeller().getFullName();
                    }
                }
            }
            json.put("buyerName", buyerName);
            json.put("sellerName", sellerName);

            responseArray.put(json);
        }

        sendJsonResponse2(exchange, 200, responseArray);
    }
    public void listAllUsers(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
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
        if(!userRole.equals("ADMIN")) {
            sendErrorResponse(exchange, 403, "Forbidden:Only Admin CAN GET list of all users");
            return;
        }
        List<User> users = userDAO.findAll();
        JSONArray userList = new JSONArray();
        for (User user : users) {
            JSONObject userJSON = new JSONObject();
            userJSON.put("id", user.getId());
            userJSON.put("full_name", user.getFullName());
            userJSON.put("phone", user.getPhone());
            userJSON.put("email", user.getEmail());
            userJSON.put("role", user.getRole());
            userJSON.put("address", user.getAddress());
            userJSON.put("profileImageBase64", user.getProfileImageBase64());
            if (user instanceof Seller) {
                userJSON.put("status", ((Seller) user).getStatus());
            } else if (user instanceof Courier) {
                userJSON.put("status", ((Courier) user).getStatus());
            } else {
                userJSON.put("status", "N/A");
            }
            JSONObject bankJSON = new JSONObject();
            if (user.getBankName() != null) {
                bankJSON.put("bank_name", user.getBankName());
                bankJSON.put("account_number", user.getAccountNumber());
            }
            userJSON.put("bank_info", bankJSON);

            userList.put(userJSON);
        }

        sendJsonResponse2(exchange, 200, userList);
    }

    public void updateUserApprovalStatus(HttpExchange exchange) throws IOException {

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        try {
            String token = authHeader.substring(7);
            if (!"ADMIN".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden: Only Admin can change user status");
                return;
            }
        } catch (Exception e) {
            sendErrorResponse(exchange, 401, "Invalid token");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long userId;
        try {
            userId = Long.parseLong(parts[3]);
        } catch (Exception e) {
            sendErrorResponse(exchange, 400, "Invalid user ID in URL");
            return;
        }

        JSONObject body = parseRequestBody(exchange);
        String statusStr = body.optString("status", null);
        if (statusStr == null) {
            sendErrorResponse(exchange, 400, "Missing 'status' field in request body");
            return;
        }

        Validitaion newStatus;
        try {
            newStatus = Validitaion.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, "Invalid status value. Use VALID or INVALID.");
            return;
        }

        boolean success = userDAO.updateUserStatus(userId, newStatus);

        if (success) {
            sendJasonResponse3(exchange, 200, "User status updated successfully.");
        } else {
            sendErrorResponse(exchange, 500, "Failed to update user status. User might not exist or is not a Seller/Courier.");
        }
    }
// File: Online Food Ordering Project/src/main/java/org/croissantbuddies/snappfood/handler/AdminHandler.java

    private void getOrderDetails(HttpExchange exchange) throws IOException {
        // Admin authorization check (similar to other methods in this class)
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            if (!"ADMIN".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            long orderId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Order order = session.get(Order.class, orderId);
                if (order == null) {
                    sendErrorResponse(exchange, 404, "Order not found");
                    return;
                }

                Hibernate.initialize(order.getItems());
                for (CartItem item : order.getItems()) {
                    Hibernate.initialize(item.getFood());
                }

                JSONArray itemsArray = new JSONArray();
                for (CartItem item : order.getItems()) {
                    JSONObject itemJson = new JSONObject();
                    itemJson.put("foodName", item.getFood().getName());
                    itemJson.put("quantity", item.getQuantity());
                    itemJson.put("price", item.getFood().getPrice());
                    itemsArray.put(itemJson);
                }
                sendJsonResponse2(exchange, 200, itemsArray);
            }

        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid Order ID format in URL");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private void getOrders(HttpExchange exchange) throws IOException {
        Map<String, String> filters = parseQueryParams(exchange.getRequestURI().getQuery());
        List<Order> orders = orderDAO.findAdminFiltered(filters);
        JSONArray responseArray = new JSONArray();
        for (Order order : orders) {
            JSONObject orderJson = new JSONObject();
            orderJson.put("id", order.getId());
            User user = userDAO.findById(order.getCustomerId());
            if (order.getDeliveryAddress().equalsIgnoreCase("Default Address")) {
                orderJson.put("delivery_address", user != null ? user.getAddress() : "N/A");
            } else {
                orderJson.put("delivery_address", order.getDeliveryAddress());
            }
            orderJson.put("pay_price", order.getPayPrice());
            orderJson.put("status", order.getStatus() != null ? order.getStatus().name().toLowerCase() : "unknown");
            orderJson.put("created_at", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");

            orderJson.put("customerId", order.getCustomerId());
            orderJson.put("vendorId", order.getVendorId());
            User buyer = userDAO.findById(order.getCustomerId());
            orderJson.put("buyerName", buyer != null ? buyer.getFullName() : "N/A");
            Restaurant restaurant = restaurantDAO.findById(order.getVendorId());
            orderJson.put("vendorName", restaurant != null ? restaurant.getName() : "N/A");
            responseArray.put(orderJson);
        }
        sendJsonResponse2(exchange, 200, responseArray);
    }
    public void sendJsonResponse2(HttpExchange exchange, int statusCode, JSONArray jsonArray) throws IOException {
        byte[] bytes = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    public void sendJasonResponse3(HttpExchange exchange, int statusCode, String message) throws IOException {
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
    private String parseRequestBody2(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
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
    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length > 1 && !pair[1].isEmpty()) {
                try {
                    String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (IOException e) {

                }
            }
        }
        return params;
    }
}