package org.croissantbuddies.snappfood.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.ENUM.CouponType;
import org.croissantbuddies.snappfood.ENUM.OrderStatus;
import org.croissantbuddies.snappfood.ENUM.PaymentMethod;
import org.croissantbuddies.snappfood.dao.*;
import org.croissantbuddies.snappfood.dto.CouponDTO;
import org.croissantbuddies.snappfood.entity.*;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;


public class BuyerHandler implements HttpHandler {

    private final RestaurantDAO restaurantDAO;
    private final RatingDAO ratingDAO;
    private final OrderDAO orderDAO;
    private final BuyerDAO buyerDAO;
    private final FoodDAO foodDAO;
    private final CouponDAO couponDAO;
    private final UserDAO userDAO ;

    private static final Gson gson = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDate.class, new org.croissantbuddies.snappfood.util.LocalDateAdapter())
            .create();
    public BuyerHandler(RestaurantDAO restaurantDAO, RatingDAO ratingDAO,
                        OrderDAO orderDAO, BuyerDAO buyerDAO,
                        FoodDAO foodDAO, CouponDAO couponDAO, UserDAO userDAO) {
        this.restaurantDAO = restaurantDAO;
        this.ratingDAO = ratingDAO;
        this.orderDAO = orderDAO;
        this.buyerDAO = buyerDAO;
        this.foodDAO = foodDAO;
        this.couponDAO = couponDAO;
        this.userDAO = userDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());

        if ("POST".equalsIgnoreCase(method) && relativePath.equals("/vendors")) {
            listVendors(exchange);
        }else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/orders/\\d+")) {
                getOrderDetails(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/vendors/\\d+/ratings")) {
            getRatingsForRestaurant(exchange);
        } else if ("DELETE".equalsIgnoreCase(method) && relativePath.equals("/cart/remove")) {
            removeFromCart(exchange);
        } else if ("POST".equalsIgnoreCase(method) && relativePath.equals("/orders/submit")) {
            submitOrder(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/vendors/\\d+$")) {
            getVendorMenu(exchange);
        } else if ("POST".equalsIgnoreCase(method) && relativePath.equals("/cart/add")) {
            addToCart(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.equals("/cart")) {
            getCart(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.equals("/orders/history")) {
            getOrderHistory(exchange);
        } else if ("POST".equalsIgnoreCase(method) && relativePath.equals("/items")) {
            listItems(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.matches("^/items/\\d+$")) {
            String idStr = relativePath.substring("/items/".length());
            getItemDetails(exchange, idStr);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.equals("/coupons")) {
            checkCoupon(exchange);
        } else if ("POST".equalsIgnoreCase(method) && relativePath.equals("/ratings")) {
            submitRating(exchange);
        } else if ("GET".equalsIgnoreCase(method) && relativePath.matches("^/ratings/items/\\d+$")) {
            getRatingsForItem(exchange);
        } else if (method.equals("GET") && relativePath.matches("^/ratings/\\d+$")) {
            String[] parts = relativePath.split("/");
            Long id = Long.parseLong(parts[2]);
            getRatingById(exchange, id);
        } else if (method.equals("DELETE") && relativePath.matches("^/ratings/\\d+$")) {
            String[] parts = relativePath.split("/");
            Long id = Long.parseLong(parts[2]);
            deleteRatingById(exchange, id);
        } else if (method.equals("PUT") && relativePath.matches("^/ratings/\\d+$")) {
            String[] parts = relativePath.split("/");
            Long id = Long.parseLong(parts[2]);
            updateRatingById(exchange, id);
        }
        else if (method.equalsIgnoreCase("GET") && relativePath.equals("/favorites")) {
            getFavRestaurants(exchange);
        }
        else if (method.equalsIgnoreCase("PUT") && relativePath.matches("^/favorites/\\d+$")) {
            String[] parts = relativePath.split("/");
            long restaurantId = Long.parseLong(parts[2]);
            addToFavorites(exchange, restaurantId);
        } else if (method.equalsIgnoreCase("DELETE") && relativePath.matches("^/favorites/\\d+$")) {
            String[] parts = relativePath.split("/");
            long restaurantId = Long.parseLong(parts[2]);
            removeFromFavorites(exchange, restaurantId);
        }
        else {
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }
    private void sendResponse(HttpExchange exchange, int statusCode, String body) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception ignored) {}
    }
    private static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = "{\"error\":\"" + message + "\"}";
        sendJsonResponse(exchange, statusCode, response);
    }

    private static final Map<String, List<Long>> requestTimestamps = new HashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private boolean isRateLimited(String token) {
        long now = System.currentTimeMillis();
        List<Long> timestamps = requestTimestamps.getOrDefault(token, new ArrayList<>());
        timestamps.removeIf(timestamp -> now - timestamp > 60_000);

        if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            return true;
        }
        timestamps.add(now);
        requestTimestamps.put(token, timestamps);
        return false;
    }


    private void listVendors(HttpExchange exchange) throws IOException {
        try {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }
            String token = authHeader.substring("Bearer ".length()).trim();

            VendorFilter filter = gson.fromJson(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8),
                    VendorFilter.class
            );

            List<Map<String, Object>> results = restaurantDAO.findAllWithAverageRating(filter.search, filter.keywords);

            JSONArray responseArray = new JSONArray();
            for (Map<String, Object> result : results) {
                Restaurant restaurant = (Restaurant) result.get("restaurant");
                double averageRating = (Double) result.get("averageRating");

                JSONObject restaurantJson = new JSONObject();
                restaurantJson.put("id", restaurant.getId());
                restaurantJson.put("name", restaurant.getName());
                restaurantJson.put("address", restaurant.getAddress());
                restaurantJson.put("phone", restaurant.getPhone());
                restaurantJson.put("logoBase64", restaurant.getLogoBase64() != null ? restaurant.getLogoBase64() : "");
                restaurantJson.put("tax_fee", restaurant.getTaxFee());
                restaurantJson.put("additional_fee", restaurant.getAdditionalFee());
                restaurantJson.put("averageRating", String.format("%.1f", averageRating));

                responseArray.put(restaurantJson);
            }
            sendJsonResponse2(exchange, 200, responseArray);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private static class VendorFilter {
        String search;
        List<String> keywords;
    }

    private void getVendorMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long vendorId;
        try {
            vendorId = Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid vendor ID");
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Restaurant restaurant = session.get(Restaurant.class, vendorId);

            if (restaurant == null) {
                sendErrorResponse(exchange, 404, "Vendor not found");
                return;
            }

            JSONObject responseJson = new JSONObject();
            JSONObject vendorJson = new JSONObject();
            vendorJson.put("id", restaurant.getId());
            vendorJson.put("name", restaurant.getName());
            responseJson.put("vendor", vendorJson);

            JSONArray menusArray = new JSONArray();
            Hibernate.initialize(restaurant.getMenus());
            for (Menu menu : restaurant.getMenus()) {
                JSONObject menuJson = new JSONObject();
                menuJson.put("title", menu.getTitle());

                JSONArray itemsArray = new JSONArray();
                Hibernate.initialize(menu.getItems());
                for (Food food : menu.getItems()) {
                    JSONObject foodJson = new JSONObject();
                    foodJson.put("id", food.getId());
                    foodJson.put("name", food.getName());
                    foodJson.put("description", food.getDescription());
                    foodJson.put("price", food.getPrice());
                    foodJson.put("imageBase64", food.getImageBase64() != null ? food.getImageBase64() : "");
                    foodJson.put("keywords", new JSONArray(food.getKeywords()));
                    itemsArray.put(foodJson);
                }
                menuJson.put("items", itemsArray);
                menusArray.put(menuJson);
            }
            responseJson.put("menus", menusArray);
            sendJsonResponse(exchange, 200, responseJson);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private static class ItemFilter {
        public String search;
        public Integer price; // This acts as maxPrice
        public Integer minPrice;
        public List<String> keywords;
    }

    private void listItems(HttpExchange exchange) {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("application/json")) {
                sendErrorResponse(exchange, 415, "Unsupported Media Type");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }

            String token = authHeader.substring("Bearer ".length()).trim();
            if (isRateLimited(token)) {
                sendErrorResponse(exchange, 429, "Too Many Requests");
                return;
            }

            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            ItemFilter filter;
            try {
                filter = gson.fromJson(
                        new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8),
                        ItemFilter.class
                );
            } catch (JsonSyntaxException e) {
                sendErrorResponse(exchange, 400, "Invalid input");
                return;
            }

            List<Food> items = foodDAO.getFilteredItems(filter.search, filter.minPrice, filter.price, filter.keywords);

            if (items == null) {
                sendErrorResponse(exchange, 404, "Items not found");
                return;
            }

            JSONArray responseArray = new JSONArray();
            for (Food food : items) {
                JSONObject foodJson = new JSONObject();
                foodJson.put("id", food.getId());
                foodJson.put("name", food.getName());
                foodJson.put("description", food.getDescription());
                foodJson.put("price", food.getPrice());
                foodJson.put("supply", food.getSupply());
                foodJson.put("keywords", new JSONArray(food.getKeywords()));
                foodJson.put("imageBase64", food.getImageBase64() != null ? food.getImageBase64() : "");

                if (food.getRestaurant() != null) {
                    JSONObject restaurantJson = new JSONObject();
                    restaurantJson.put("id", food.getRestaurant().getId());
                    restaurantJson.put("name", food.getRestaurant().getName());
                    foodJson.put("restaurant", restaurantJson);
                }
                responseArray.put(foodJson);
            }

            sendJsonResponse2(exchange, 200, responseArray);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ItemResponse {
        public Long id;
        public String name;
        public String imageBase64;
        public String description;
        public Long vendor_id;
        public int price;
        public int supply;
        public List<String> keywords;
    }

    private void getItemDetails(HttpExchange exchange, String idStr) {
        try {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendResponse(exchange, 401, gson.toJson(new ErrorResponse("Unauthorized")));
                return;
            }
            String token = authHeader.substring("Bearer ".length()).trim();
            if (!JwtUtil.TokenWhitelist.contains(token)) {
                sendResponse(exchange, 403, gson.toJson(new ErrorResponse("Forbidden")));
                return;
            }
            if (isRateLimited(token)) {
                sendResponse(exchange, 429, gson.toJson(new ErrorResponse("Too Many Requests")));
                return;
            }
            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendResponse(exchange, 403, gson.toJson(new ErrorResponse("Forbidden for this role")));
                return;
            }
            Long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, gson.toJson(new ErrorResponse("Invalid item ID")));
                return;
            }
            Food food = foodDAO.findById(id);
            if (food == null) {
                sendResponse(exchange, 404, gson.toJson(new ErrorResponse("Item not found")));
                return;
            }
            if (food.getName() == null || food.getDescription() == null) {
                sendResponse(exchange, 409, gson.toJson(new ErrorResponse("Conflict in item data")));
                return;
            }
            ItemResponse res = new ItemResponse();
            res.id = food.getId();
            res.name = food.getName();
            res.imageBase64 = food.getImageBase64();
            res.description = food.getDescription();
            res.vendor_id = food.getRestaurant() != null ? food.getRestaurant().getId() : null;
            res.price = (int) food.getPrice();
            res.supply = food.getSupply();
            res.keywords = food.getKeywords();
            sendResponse(exchange, 200, gson.toJson(res));
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, gson.toJson(new ErrorResponse("Internal Server Error")));
        }
    }


    private void checkCoupon(HttpExchange exchange) {
        try {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }
            String token = authHeader.substring("Bearer ".length()).trim();

            if (isRateLimited(token)) {
                sendErrorResponse(exchange, 429, "Too Many Requests");
                return;
            }
            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            if (query == null) {
                sendErrorResponse(exchange, 400, "Invalid input: query parameters are required");
                return;
            }

            String couponCode = null;
            Long vendorId = null;
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("coupon_code=")) {
                    couponCode = param.substring("coupon_code=".length());
                } else if (param.startsWith("vendor_id=")) {
                    try {
                        vendorId = Long.parseLong(param.substring("vendor_id=".length()));
                    } catch (NumberFormatException e) {
                        sendErrorResponse(exchange, 400, "Invalid vendor_id format");
                        return;
                    }
                }
            }

            if (couponCode == null || couponCode.isEmpty() || vendorId == null) {
                sendErrorResponse(exchange, 400, "Invalid input: coupon_code and vendor_id are required");
                return;
            }

            Session session = HibernateUtil.getSessionFactory().openSession();
            Coupon coupon;
            try {
                coupon = session.createQuery(
                                "SELECT c FROM Coupon c LEFT JOIN FETCH c.restaurant WHERE c.couponCode = :code", Coupon.class)
                        .setParameter("code", couponCode)
                        .uniqueResult();
            } finally {
                session.close();
            }

            if (coupon == null) {
                sendErrorResponse(exchange, 404, "Coupon not found");
                return;
            }

            if (coupon.getRestaurant() != null && !coupon.getRestaurant().getId().equals(vendorId)) {
                sendErrorResponse(exchange, 409, "Coupon is not valid for this restaurant.");
                return;
            }

            LocalDate today = LocalDate.now();
            if (coupon.getEndDate().isBefore(today) || coupon.getStartDate().isAfter(today)) {
                sendErrorResponse(exchange, 409, "Coupon not valid at this time");
                return;
            }
            if (coupon.getUserCount() <= 0) {
                sendErrorResponse(exchange, 409, "Coupon usage limit exceeded");
                return;
            }

            CouponDTO dto = new CouponDTO();
            dto.id = coupon.getId();
            dto.coupon_code = coupon.getCouponCode();
            dto.type = coupon.getType() == CouponType.FIXED ? "fixed" : "percent";
            dto.value = coupon.getValue();
            dto.min_price = coupon.getMinPrice();
            dto.user_count = coupon.getUserCount();
            dto.start_date = coupon.getStartDate();
            dto.end_date = coupon.getEndDate();

            sendResponse(exchange, 200, gson.toJson(dto));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getFavRestaurants(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring("Bearer ".length()).trim();
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            Buyer buyer = buyerDAO.findById(buyerId);
            if (buyer == null) {
                sendErrorResponse(exchange, 404, "Buyer not found");
                return;
            }

            List<Restaurant> favRestaurants = buyer.getFavoriteRestaurants();
            JSONArray restaurantsArray = new JSONArray();

            for (Restaurant restaurant : favRestaurants) {
                JSONObject restaurantJson = new JSONObject();
                restaurantJson.put("id", restaurant.getId());
                restaurantJson.put("name", restaurant.getName());
                restaurantJson.put("address", restaurant.getAddress());
                restaurantJson.put("phone", restaurant.getPhone());
                restaurantJson.put("logoBase64", restaurant.getLogoBase64() != null ? restaurant.getLogoBase64() : "");
                restaurantJson.put("tax_fee", restaurant.getTaxFee());
                restaurantJson.put("additional_fee", restaurant.getAdditionalFee());
                restaurantsArray.put(restaurantJson);
            }

            JSONObject responseJson = new JSONObject();
            responseJson.put("restaurants", restaurantsArray);

            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }


    private void addToFavorites(HttpExchange exchange, long restaurantId) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring("Bearer ".length()).trim();
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            Buyer buyer = buyerDAO.findById(buyerId);
            if (buyer == null) {
                sendErrorResponse(exchange, 404, "Buyer not found");
                return;
            }

            Restaurant restaurant = restaurantDAO.findById(restaurantId);
            if (restaurant == null) {
                sendErrorResponse(exchange, 404, "Restaurant not found");
                return;
            }

            if (buyer.getFavoriteRestaurants().contains(restaurant)) {
                sendErrorResponse(exchange, 409, "Restaurant already in favorites");
                return;
            }

            buyer.getFavoriteRestaurants().add(restaurant);
            buyerDAO.update(buyer);


            JSONObject res = new JSONObject();
            res.put("message", "Added to favorites");

            sendJsonResponse(exchange, 200, res);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void removeFromFavorites(HttpExchange exchange, long restaurantId) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring("Bearer ".length()).trim();
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            Buyer buyer = buyerDAO.findById(buyerId);
            if (buyer == null) {
                sendErrorResponse(exchange, 404, "Buyer not found");
                return;
            }

            Restaurant restaurant = restaurantDAO.findById(restaurantId);
            if (restaurant == null) {
                sendErrorResponse(exchange, 404, "Restaurant not found");
                return;
            }

            if (!buyer.getFavoriteRestaurants().contains(restaurant)) {
                sendErrorResponse(exchange, 409, "Restaurant not in favorites");
                return;
            }

            buyer.getFavoriteRestaurants().remove(restaurant);
            buyerDAO.update(buyer);
            JSONObject res = new JSONObject();
            res.put("message", "Removed from favorites");

            sendJsonResponse(exchange, 200, res);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void getRatingsForItem(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("application/json")) {
                sendErrorResponse(exchange, 415, "Unsupported Media Type");
                return;
            }
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }

            String token = authHeader.substring("Bearer ".length()).trim();
            if (!JwtUtil.TokenWhitelist.contains(token)) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }
            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }
            if (isRateLimited(token)) {
                sendErrorResponse(exchange, 429, "Too Many Requests");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 5) {
                sendErrorResponse(exchange, 400, "Invalid item_id");
                return;
            }

            Long itemId;
            try {
                itemId = Long.parseLong(parts[4]);
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid item_id format");
                return;
            }
            Food food = foodDAO.findById(itemId);
            if (food == null) {
                sendErrorResponse(exchange, 404, "Item not found");
                return;
            }
            List<Rating> ratings = ratingDAO.findByFoodId(itemId);
            if (ratings == null || ratings.isEmpty()) {
                sendErrorResponse(exchange, 404, "No ratings found");
                return;
            }

            double avg = ratings.stream()
                    .mapToInt(Rating::getRating)
                    .average()
                    .orElse(0.0);

            List<Map<String, Object>> comments = ratings.stream().map(r -> {
                Map<String, Object> map = new HashMap<>();
                map.put("rating", r.getRating());
                map.put("comment", r.getComment());
                map.put("imageBase64", r.getImageBase64());
                map.put("created_at", r.getCreatedAt().toString());
                return map;
            }).collect(Collectors.toList());

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("avg_rating", avg);
            responseMap.put("comments", comments);

            sendJsonResponse(exchange, 200, gson.toJson(responseMap));

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }



    private void getRatingById(HttpExchange exchange, Long ratingId) throws IOException {
        try {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }

            String token = authHeader.substring("Bearer ".length()).trim();
            if (!JwtUtil.TokenWhitelist.contains(token)) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }
            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Only buyers can access this resource");
                return;
            }
            if (ratingId == null || ratingId <= 0) {
                sendErrorResponse(exchange, 400, "Invalid rating ID");
                return;
            }
            Rating rating = ratingDAO.findById(ratingId);
            if (rating == null) {
                sendErrorResponse(exchange, 404, "Rating not found");
                return;
            }
            Order order = orderDAO.findById(rating.getOrderId());
            if (order == null) {
                sendErrorResponse(exchange, 500, "Order not found for this rating");
                return;
            }

            JsonObject json = new JsonObject();
            json.addProperty("id", rating.getId());
            json.addProperty("restaurant_id", rating.getRestaurantId());
            json.addProperty("rating", rating.getRating());
            json.addProperty("comment", rating.getComment());
            json.addProperty("user_id", order.getCustomerId());
            json.addProperty("created_at", rating.getCreatedAt().toString());
            json.add("imageBase64", gson.toJsonTree(rating.getImageBase64()));
            sendJsonResponse(exchange, 200, json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }


    //Delete a rating or review
    private void deleteRatingById(HttpExchange exchange, Long ratingId) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("application/json")) {
                sendErrorResponse(exchange, 415, "Unsupported Media Type");
                return;
            }
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }

            String token = authHeader.substring("Bearer ".length()).trim();
            if (!JwtUtil.TokenWhitelist.contains(token)) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }
            if (isRateLimited(token)) {
                sendErrorResponse(exchange, 429, "Too Many Requests");
                return;
            }
            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }
            Long userId;
            try {
                userId = JwtUtil.getUserIdFromToken(token);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid token payload");
                return;
            }
            Rating rating = ratingDAO.findById(ratingId);
            if (rating == null) {
                sendErrorResponse(exchange, 404, "Rating not found");
                return;
            }
            Order order = orderDAO.findById(rating.getOrderId());
            if (order == null) {
                sendErrorResponse(exchange, 500, "Order not found for this rating");
                return;
            }
            if (!order.getCustomerId().equals(userId)) {
                sendErrorResponse(exchange, 403, "You are not allowed to delete this rating");
                return;
            }
            ratingDAO.delete(rating);

            JsonObject res = new JsonObject();
            res.addProperty("message", "Rating deleted");
            sendJsonResponse(exchange, 200, res.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }


    private void updateRatingById(HttpExchange exchange, Long ratingId) throws IOException {
        try {

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("application/json")) {
                sendErrorResponse(exchange, 415, "Unsupported Media Type");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }

            String token = authHeader.substring("Bearer ".length()).trim();

            if (!JwtUtil.TokenWhitelist.contains(token)) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            if (isRateLimited(token)) {
                sendErrorResponse(exchange, 429, "Too Many Requests");
                return;
            }

            String role = JwtUtil.getRoleFromToken(token);
            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            Long userId;
            try {
                userId = JwtUtil.getUserIdFromToken(token);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid token payload");
                return;
            }

            InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            UpdateRatingRequest req = gson.fromJson(reader, UpdateRatingRequest.class);

            if (req.rating < 1 || req.rating > 5 || req.comment == null || req.comment.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Invalid input");
                return;
            }

            Rating rating = ratingDAO.findById(ratingId);
            if (rating == null) {
                sendErrorResponse(exchange, 404, "Rating not found");
                return;
            }

            Order order = orderDAO.findById(rating.getOrderId());
            if (order == null) {
                sendErrorResponse(exchange, 500, "Order not found for this rating");
                return;
            }

            if (!order.getCustomerId().equals(userId)) {
                sendErrorResponse(exchange, 403, "You are not allowed to update this rating");
                return;
            }

            rating.setRating(req.rating);
            rating.setComment(req.comment);
            rating.setImageBase64(req.imageBase64 != null ? req.imageBase64 : List.of());

            ratingDAO.update(rating);


            JsonObject json = new JsonObject();
            json.addProperty("id", rating.getId());
            json.addProperty("order_id", rating.getOrderId());

            json.addProperty("restaurant_id", rating.getRestaurantId());
            json.addProperty("rating", rating.getRating());
            json.addProperty("comment", rating.getComment());
            json.addProperty("user_id", order.getCustomerId());
            json.addProperty("created_at", rating.getCreatedAt().toString());
            json.add("imageBase64", gson.toJsonTree(rating.getImageBase64()));


            sendJsonResponse(exchange, 200, json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private static class UpdateRatingRequest {
        public int rating;
        public String comment;
        public List<String> imageBase64;
    }
    private void addToCart(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        org.hibernate.Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            String token = authHeader.substring(7);
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            long foodId = body.getLong("foodId");
            int quantity = body.getInt("quantity");

            if (quantity <= 0) {
                sendErrorResponse(exchange, 400, "Quantity must be positive");
                return;
            }


            Buyer buyer = session.get(Buyer.class, buyerId);
            Food food = session.get(Food.class, foodId);

            if (buyer == null || food == null) {
                sendErrorResponse(exchange, 404, "Buyer or Food not found");
                return;
            }


            boolean itemExists = false;
            for (CartItem item : buyer.getCart()) {
                if (item.getFood().getId().equals(food.getId())) {
                    item.setQuantity(item.getQuantity() + quantity);
                    itemExists = true;
                    break;
                }
            }
            if (!itemExists) {
                CartItem newItem = new CartItem(food, buyer, quantity);
                buyer.getCart().add(newItem);
            }

            session.merge(buyer);
            tx.commit();

            sendResponse(exchange, 200, "{\"message\":\"Item added to cart successfully\"}");

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void getCart(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String token = authHeader.substring(7);
            Long buyerId = JwtUtil.getUserIdFromToken(token);

            Buyer buyer = session.get(Buyer.class, buyerId);
            if (buyer == null) {
                sendErrorResponse(exchange, 404, "Buyer not found");
                return;
            }


            JSONObject responseJson = new JSONObject();
            JSONArray cartArray = new JSONArray();

            if (buyer.getCart() == null || buyer.getCart().isEmpty()) {
                responseJson.put("items", cartArray);
                responseJson.put("subTotal", 0.0);
                responseJson.put("taxFee", 0.0);
                responseJson.put("packagingFee", 0.0);
                responseJson.put("finalPrice", 0.0);
                responseJson.put("vendorId", -1);
                sendJsonResponse(exchange, 200, responseJson);
                return;
            }

            double subTotal = 0.0;
            Restaurant restaurant = null;

            Hibernate.initialize(buyer.getCart());
            for (CartItem item : buyer.getCart()) {
                Hibernate.initialize(item.getFood());
                Food food = item.getFood();
                if (food != null) {
                    if (restaurant == null) {
                        Hibernate.initialize(food.getRestaurant());
                        restaurant = food.getRestaurant();
                    }
                    JSONObject itemJson = new JSONObject();
                    itemJson.put("foodId", food.getId());
                    itemJson.put("foodName", food.getName());
                    itemJson.put("quantity", item.getQuantity());
                    itemJson.put("price", food.getPrice());
                    cartArray.put(itemJson);
                    subTotal += item.getQuantity() * food.getPrice();
                }
            }

            double taxFee = 0;
            double packagingFee = 0;
            double finalPrice = subTotal;

            if (restaurant != null) {
                taxFee = subTotal * restaurant.getTaxFee() / 100.0;
                packagingFee = restaurant.getAdditionalFee();
                finalPrice += taxFee + packagingFee;
                responseJson.put("vendorId", restaurant.getId());
            } else {
                responseJson.put("vendorId", -1);
            }

            responseJson.put("items", cartArray);
            responseJson.put("subTotal", subTotal);
            responseJson.put("taxFee", taxFee);
            responseJson.put("packagingFee", packagingFee);
            responseJson.put("finalPrice", finalPrice);

            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private void getOrderHistory(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            Map<String, String> filters = parseQueryParams(exchange.getRequestURI().getQuery());
            String statusFilter = filters.get("status");

            LocalDate startDate = null;
            LocalDate endDate = null;
            try {
                if (filters.containsKey("start_date")) {
                    startDate = LocalDate.parse(filters.get("start_date"));
                }
                if (filters.containsKey("end_date")) {
                    endDate = LocalDate.parse(filters.get("end_date"));
                }
            } catch (DateTimeParseException e) {
                sendErrorResponse(exchange, 400, "Invalid date format. Please use YYYY-MM-DD.");
                return;
            }

            List<Order> orders = orderDAO.findByCustomerIdFiltered(buyerId, statusFilter, startDate, endDate);
            JSONArray ordersArray = new JSONArray();
            for (Order order : orders) {
                JSONObject orderJson = new JSONObject();
                orderJson.put("id", order.getId());
                orderJson.put("vendor_id", order.getVendorId());

                Restaurant restaurant = restaurantDAO.findById(order.getVendorId());
                orderJson.put("vendorName", restaurant != null ? restaurant.getName() : "Unknown Restaurant");

                orderJson.put("pay_price", order.getPayPrice());
                orderJson.put("status", order.getStatus().name());
                orderJson.put("created_at", order.getCreatedAt().toString());
                ordersArray.put(orderJson);
            }
            sendJsonResponse2(exchange, 200, ordersArray);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void getOrderDetails(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        try {
            String token = authHeader.substring(7);
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            long orderId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));

            Order order = orderDAO.findById(orderId);

            if (order == null) {
                sendErrorResponse(exchange, 404, "Order not found");
                return;
            }
            if (!order.getCustomerId().equals(buyerId)) {
                sendErrorResponse(exchange, 403, "Forbidden: You do not own this order");
                return;
            }

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                session.refresh(order);
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

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length > 1 && !pair[1].isEmpty()) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }
    private void submitOrder(HttpExchange exchange) throws IOException {
        System.out.println("\n--- SUBMIT ORDER REQUEST RECEIVED ---"); // لاگ ۱: برای اطمینان از اجرای متد

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        Session session = null;
        org.hibernate.Transaction tx = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();

            String token = authHeader.substring(7);
            Long buyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Forbidden for this role");
                return;
            }

            Buyer buyer = session.get(Buyer.class, buyerId);
            if (buyer == null || buyer.getCart().isEmpty()) {
                sendErrorResponse(exchange, 400, "Buyer not found or cart is empty");
                return;
            }

            Hibernate.initialize(buyer.getCart());
            for (CartItem item : buyer.getCart()) {
                Hibernate.initialize(item.getFood());
            }

            JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String deliveryAddress = body.getString("delivery_address");
            long vendorId = body.getLong("vendor_id");
            long couponId = body.optLong("coupon_id", -1);
            PaymentMethod paymentMethod = PaymentMethod.valueOf(body.getString("payment_method").toUpperCase());

            System.out.println("Order received for vendorId: " + vendorId); // لاگ ۲: شناسه رستوران
            System.out.println("Coupon ID from request: " + couponId); // لاگ ۳: شناسه کوپن

            Restaurant restaurant = session.get(Restaurant.class, vendorId);
            if (restaurant == null) {
                sendErrorResponse(exchange, 404, "Restaurant not found");
                return;
            }

            double subTotal = buyer.totalCartPrice();
            double taxFee = subTotal * restaurant.getTaxFee() / 100.0;
            double packagingFee = restaurant.getAdditionalFee();
            double finalPrice = subTotal + taxFee + packagingFee;
            double discount = 0;

            if (couponId != -1) {
                System.out.println("--- Starting Coupon Check ---");
                Coupon coupon = session.createQuery(
                                "SELECT c FROM Coupon c LEFT JOIN FETCH c.restaurant WHERE c.id = :couponId", Coupon.class)
                        .setParameter("couponId", couponId)
                        .uniqueResult();

                if (coupon != null) {
                    System.out.println("Coupon found in DB: " + coupon.getCouponCode());
                    if (coupon.getRestaurant() != null) {
                        System.out.println("Coupon is for restaurant ID: " + coupon.getRestaurant().getId());
                    } else {
                        System.out.println("Coupon is a general coupon (admin-created).");
                    }
                } else {
                    System.out.println("Coupon with ID " + couponId + " was NOT found in the database.");
                }

                if (coupon != null &&
                        (coupon.getRestaurant() == null || coupon.getRestaurant().getId().equals(vendorId)) &&
                        coupon.getStartDate().isBefore(LocalDate.now().plusDays(1)) &&
                        coupon.getEndDate().isAfter(LocalDate.now().minusDays(1)) &&
                        coupon.getUserCount() > 0 && finalPrice >= coupon.getMinPrice()) {

                    System.out.println("Validation PASSED. Applying discount.");
                    if (coupon.getType() == CouponType.PERCENT) {
                        discount = (subTotal * coupon.getValue()) / 100.0;
                    } else {
                        discount = coupon.getValue();
                    }

                    finalPrice -= discount;
                    if (finalPrice < 0) finalPrice = 0;

                    coupon.setUserCount(coupon.getUserCount() - 1);
                    session.merge(coupon);
                } else {
                    System.out.println("Validation FAILED. Coupon not applied.");
                }
            }

            if (paymentMethod == PaymentMethod.WALLET) {
                if (buyer.getAmount() < finalPrice) {
                    if (tx != null) tx.rollback();
                    sendErrorResponse(exchange, 402, "Payment Required: Insufficient wallet balance.");
                    return;
                }
                buyer.setAmount(buyer.getAmount() - finalPrice);
            }

            Order newOrder = new Order();
            newOrder.setCustomerId(buyerId);
            newOrder.setVendorId(vendorId);
            newOrder.setDeliveryAddress(deliveryAddress);
            newOrder.setRawPrice(subTotal);
            newOrder.setTaxFee(taxFee);
            newOrder.setAdditionalFee(packagingFee);
            newOrder.setPayPrice(finalPrice);
            newOrder.setCouponId(couponId != -1 ? couponId : null);
            newOrder.setStatus(OrderStatus.SUBMITTED);
            newOrder.setPaymentMethod(paymentMethod);

            List<CartItem> itemsToMove = new ArrayList<>(buyer.getCart());
            buyer.getCart().clear();

            for (CartItem cartItem : itemsToMove) {
                cartItem.setBuyer(null);
                cartItem.setOrder(newOrder);
                newOrder.getItems().add(cartItem);
            }

            session.persist(newOrder);
            session.merge(buyer);

            Transaction transaction = new Transaction();
            transaction.setOrderId(newOrder.getId());
            transaction.setUserId(buyerId);
            transaction.setAmount(finalPrice);
            transaction.setMethod(paymentMethod.name());
            transaction.setStatus(OrderStatus.SUBMITTED);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setTransactionId("TXN-" + System.currentTimeMillis() + "-" + newOrder.getId());
            session.persist(transaction);
            tx.commit();
            sendResponse(exchange, 200, "{\"message\":\"Order submitted successfully!\", \"order_id\":" + newOrder.getId() + "}");
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rbEx) {
                    System.err.println("Error during rollback: " + rbEx.getMessage());
                }
            }
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
    private void removeFromCart(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        org.hibernate.Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            String token = authHeader.substring(7);
            Long buyerId = JwtUtil.getUserIdFromToken(token);

            JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            long foodId = body.getLong("foodId");

            Buyer buyer = session.get(Buyer.class, buyerId);
            Food foodToRemove = session.get(Food.class, foodId);

            if (buyer == null || foodToRemove == null) {
                sendErrorResponse(exchange, 404, "Buyer or Food not found");
                return;
            }

            buyer.removeFromCart(foodToRemove);
            session.merge(buyer);
            tx.commit();

            sendResponse(exchange, 200, "{\"message\":\"Item removed from cart\"}");

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void submitRating(HttpExchange exchange) throws IOException {
        try {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendErrorResponse(exchange, 401, "Unauthorized");
                return;
            }
            String token = authHeader.substring("Bearer ".length()).trim();
            Long currentBuyerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);

            if (!"BUYER".equals(role)) {
                sendErrorResponse(exchange, 403, "Only buyers can submit ratings");
                return;
            }

            JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            long orderId = body.optLong("order_id", -1);
            int ratingValue = body.optInt("rating", -1);
            String comment = body.optString("comment", null);

            JSONArray imageBase64Json = body.optJSONArray("imageBase64");
            List<String> imageBase64List = new ArrayList<>();
            if (imageBase64Json != null) {
                for (int i = 0; i < imageBase64Json.length(); i++) {
                    imageBase64List.add(imageBase64Json.getString(i));
                }
            }

            if (orderId == -1 || ratingValue < 1 || ratingValue > 5 || comment == null || comment.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Invalid input: order_id, rating, and comment are required.");
                return;
            }

            Order order = orderDAO.findById(orderId);
            if (order == null) {
                sendErrorResponse(exchange, 404, "Order not found");
                return;
            }
            if (!order.getCustomerId().equals(currentBuyerId)) {
                sendErrorResponse(exchange, 403, "You are not allowed to review this order");
                return;
            }
            if (order.getStatus() != OrderStatus.COMPLETED) {
                sendErrorResponse(exchange, 409, "You can only rate completed orders.");
                return;
            }

            if (ratingDAO.existsByOrderId(orderId)) {
                sendErrorResponse(exchange, 409, "Rating already submitted for this order");
                return;
            }

            Rating rating = new Rating();
            rating.setUserId(currentBuyerId);
            rating.setOrderId(orderId);
            rating.setRestaurantId(order.getVendorId());
            rating.setRating(ratingValue);
            rating.setComment(comment);

            if (!imageBase64List.isEmpty()) {
                rating.setImageBase64(imageBase64List);
            }

            ratingDAO.save(rating);

            sendJsonResponse(exchange, 200, new JSONObject().put("message", "Rating submitted successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    private void getRatingsForRestaurant(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId = Long.parseLong(parts[3]);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Rating> ratings = ratingDAO.findByRestaurantId(restaurantId);

            if (ratings.isEmpty()) {
                JSONObject emptyResponse = new JSONObject();
                emptyResponse.put("averageRating", "0.0");
                emptyResponse.put("ratings", new JSONArray());
                sendJsonResponse(exchange, 200, emptyResponse);
                return;
            }

            Set<Long> orderIds = ratings.stream().map(Rating::getOrderId).collect(Collectors.toSet());
            List<Order> relatedOrders = orderDAO.findByIdsWithItems(orderIds);
            Map<Long, Order> orderMap = relatedOrders.stream().collect(Collectors.toMap(Order::getId, order -> order));


            JSONArray commentsArray = new JSONArray();
            double totalRating = 0;

            for (Rating rating : ratings) {
                JSONObject commentJson = new JSONObject();
                User user = userDAO.findById(rating.getUserId());
                Order order = orderMap.get(rating.getOrderId());

                commentJson.put("userName", user != null ? user.getFullName() : "Unknown User");
                commentJson.put("rating", rating.getRating());
                commentJson.put("comment", rating.getComment());
                commentJson.put("createdAt", rating.getCreatedAt().toString());

                if (order != null && order.getItems() != null) {
                    JSONArray orderedItems = new JSONArray();
                    for (CartItem item : order.getItems()) {
                        orderedItems.put(item.getFood().getName());
                    }
                    commentJson.put("orderedItems", orderedItems);
                }


                if (rating.getImageBase64() != null && !rating.getImageBase64().isEmpty()) {
                    commentJson.put("imageBase64", new JSONArray(rating.getImageBase64()));
                }
                commentsArray.put(commentJson);
                totalRating += rating.getRating();
            }

            double averageRating = totalRating / ratings.size();

            JSONObject responseJson = new JSONObject();
            responseJson.put("averageRating", String.format("%.1f", averageRating));
            responseJson.put("ratings", commentsArray);

            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    public void sendJsonResponse2(HttpExchange exchange, int statusCode, JSONArray jsonArray) throws IOException {
        byte[] bytes = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    private void sendErrorResponse3(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
        byte[] responseBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = exchange.getResponseBody();

        os.write(responseBytes);

        os.close();
    }
}
