package org.croissantbuddies.snappfood.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.ENUM.CouponType;
import org.croissantbuddies.snappfood.ENUM.OrderStatus;
import org.croissantbuddies.snappfood.dao.*;
import org.croissantbuddies.snappfood.entity.*;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class RestaurantHandler implements HttpHandler {

    private final RestaurantDAO restaurantDAO;
    private final UserDAO userDAO;
    private final FoodDAO foodDAO;
    private final OrderDAO orderDAO;
    private final MenuDAO menuDAO;
    private final CouponDAO couponDAO;



    public RestaurantHandler(RestaurantDAO restaurantDAO, UserDAO userDAO, FoodDAO foodDAO, OrderDAO orderDAO, MenuDAO menuDAO, CouponDAO couponDAO) {
        this.restaurantDAO = restaurantDAO;
        this.userDAO = userDAO;
        this.foodDAO= foodDAO;
        this.orderDAO = orderDAO;
        this.menuDAO = menuDAO;
        this.couponDAO = couponDAO;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());

        if ("POST".equalsIgnoreCase(method) && relativePath.equals("")) {
            newRestaurants(exchange);
        }else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/\\d+/coupons")) {
                getRestaurantCoupons(exchange);
        }else if ("POST".equalsIgnoreCase(method) && relativePath.matches("/\\d+/coupons")) {
                addCouponToRestaurant(exchange);
        }else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/orders/\\d+/details")) {
                getOrderDetails(exchange);
        } else if ("PUT".equalsIgnoreCase(method) && relativePath.matches("/\\d+")) {
            updateRestaurant(exchange, relativePath.substring(1));
        } else if ("GET".equalsIgnoreCase(method) && relativePath.equals("/mine")) {
            getMyRestaurants(exchange);
        } else if  ("POST".equalsIgnoreCase(method) && relativePath.matches("/\\d+/item")) {
            addItemToRestaurant(exchange);
        } else if ("PUT".equalsIgnoreCase(method) && path.matches("/restaurants/\\d+/item/\\d+")) {
            updateFoodItem(exchange);
        } else if ("DELETE".equalsIgnoreCase(method) && path.matches("/restaurants/\\d+/item/\\d+")) {
            deleteFoodItem(exchange);
        } else if ("POST".equalsIgnoreCase(method) && path.matches("/restaurants/\\d+/menu")) {
            addMenu(exchange);
        }else if ("DELETE".equalsIgnoreCase(method) && path.matches("/restaurants/\\d+/menu/\\d+")) {
            deleteMenu(exchange);
        }else if ("POST".equalsIgnoreCase(method) && path.matches("/restaurants/\\d+/menu/\\d+/food/\\d+")) {
            addFoodToMenu(exchange);
        }else if ("DELETE".equalsIgnoreCase(method) && relativePath.matches("/\\d+/menu/[^/]+/food/\\d+")) {
            removeFoodFromMenu(exchange);
        }else if("GET".equalsIgnoreCase(method)&& path.matches("/restaurants/\\d+/orders")) {
            getOrdersForRestaurant(exchange);
        } else if ("PATCH".equalsIgnoreCase(method) && path.matches("/restaurants/orders/\\d+")) {
            updateOrderStatus(exchange);
        }else if("GET".equalsIgnoreCase(method) && relativePath.matches("/\\d+/details")) {
            getRestaurantFood(exchange);
        }else if("GET".equalsIgnoreCase(method) && relativePath.matches("/\\d+/menu")) {
            getRestaurantMenu(exchange);
        }else if ("GET".equalsIgnoreCase(method) && relativePath.matches("/\\d+/menu/\\d+/foods")) {
            getFoodsInMenu(exchange);
        }else if("GET".equalsIgnoreCase(method) &&relativePath.matches("/orders/mine")){
            getOrdersForSeller(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Not Found");
        }
    }
    public void newRestaurants(HttpExchange exchange) throws IOException {
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
        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can create a restaurant");
            return;
        }
        User user = userDAO.findById(userId);
        if (user == null || !(user instanceof Seller)) {
            sendErrorResponse(exchange, 403, "Forbidden: You are not a seller");
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);

        String name = json.optString("name", "").trim();
        String address = json.optString("address", "").trim();
        String phone = json.optString("phone", null).trim();
        String logoBase64 = json.optString("logoBase64", null).trim();
        double taxFee = json.optDouble("tax_fee", 0);
        double additionalFee = json.optDouble("additional_fee", 0);

        if (name.isEmpty() || address.isEmpty() || phone.isEmpty()) {
            sendResponse(exchange, 400, "Bad Request: Missing required fields");
            return;
        }
        if(!name.matches("^[a-zA-Zآ-یءئ\\s]{2,50}$")) {
            sendErrorResponse(exchange, 400, "error: Restaurant name contains invalid characters");
            return;
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setAddress(address);
        restaurant.setPhone(phone);
        restaurant.setLogoBase64(logoBase64);
        restaurant.setTaxFee(taxFee);
        restaurant.setAdditionalFee(additionalFee);
        restaurant.setSeller((Seller) user);
        restaurantDAO.save(restaurant);

        JSONObject restaurantJson = new JSONObject();
        restaurantJson.put("id", restaurant.getId());
        restaurantJson.put("name", name);
        restaurantJson.put("address", address);
        restaurantJson.put("phone", phone);
        restaurantJson.put("tax_fee", taxFee);
        restaurantJson.put("additional_fee", additionalFee);
        sendResponse(exchange, 201, restaurantJson.toString());
    }
    public void getMyRestaurants(HttpExchange exchange) throws IOException {
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

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can view their restaurants");
            return;
        }

        User user = userDAO.findById(userId);
        if (user == null || !(user instanceof Seller)) {
            sendErrorResponse(exchange, 403, "Forbidden: You are not a seller");
            return;
        }

        Seller seller = (Seller) user;

        List<Restaurant> restaurants = restaurantDAO.findBySellerId(userId);

        JSONArray jsonRestaurants = new JSONArray();
        for (Restaurant r : restaurants) {
            JSONObject jsonRestaurant = new JSONObject();
            jsonRestaurant.put("id", r.getId());
            jsonRestaurant.put("name", r.getName());
            jsonRestaurant.put("address", r.getAddress());
            jsonRestaurant.put("phone", r.getPhone());
            jsonRestaurant.put("tax_fee", r.getTaxFee());
            jsonRestaurant.put("additional_fee", r.getAdditionalFee());
            jsonRestaurant.put("logoBase64", r.getLogoBase64() != null ? r.getLogoBase64() : "");
            jsonRestaurants.put(jsonRestaurant);
        }
        JSONObject response = new JSONObject();
        response.put("restaurants", jsonRestaurants);
        sendJsonResponse(exchange, 200, response);
    }
    private void updateRestaurant(HttpExchange exchange, String idStr) throws IOException {
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
        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can update a restaurant");
            return;
        }
        Restaurant restaurant = restaurantDAO.findById(Long.parseLong(idStr));
        if (restaurant == null) {
            sendErrorResponse(exchange, 404, "Restaurant not found");
            return;
        }

        if (!restaurant.getSeller().getId().equals(userId)) {
            sendErrorResponse(exchange, 403, "Forbidden: You do not own this restaurant");
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);

        String name = json.optString("name", restaurant.getName()).trim();
        String address = json.optString("address", restaurant.getAddress()).trim();
        String phone = json.optString("phone", restaurant.getPhone()).trim();
        String logoBase64 = json.optString("logoBase64", restaurant.getLogoBase64()).trim();
        double taxFee = json.optDouble("tax_fee", restaurant.getTaxFee());
        double additionalFee = json.optDouble("additional_fee", restaurant.getAdditionalFee());

        if (!name.matches("^[a-zA-Zآ-یءئ\\s]{2,50}$")) {
            sendErrorResponse(exchange, 400, "error: Restaurant name contains invalid characters");
            return;
        }
        restaurant.setName(name);
        restaurant.setAddress(address);
        restaurant.setPhone(phone);
        restaurant.setLogoBase64(logoBase64);
        restaurant.setTaxFee(taxFee);
        restaurant.setAdditionalFee(additionalFee);
        restaurantDAO.update(restaurant);
        //out put
        JSONObject updatedJson = new JSONObject();
        updatedJson.put("id", restaurant.getId());
        updatedJson.put("name", name);
        updatedJson.put("address", address);
        updatedJson.put("phone", phone);
        updatedJson.put("logoBase64", logoBase64);
        updatedJson.put("tax_fee", taxFee);
        updatedJson.put("additional_fee", additionalFee);

        sendJsonResponse(exchange, 200, updatedJson);

    }
    public void addItemToRestaurant(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 3) {
            sendErrorResponse(exchange, 400, "Invalid path");
            return;
        }

        Long restaurantId;
        try {
            restaurantId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid restaurant ID");
            return;
        }

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

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can add food");
            return;
        }

        Restaurant restaurant = restaurantDAO.findById(restaurantId);
        if (restaurant == null) {
            sendErrorResponse(exchange, 404, "Restaurant not found");
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);

        String name = json.optString("name", "").trim();
        String imageBase64 = json.optString("imageBase64", "").trim();
        String description = json.optString("description", "").trim();
        double price = json.optDouble("price", 0);
        int supply = json.optInt("supply", 0);

        List<String> keywords = new ArrayList<>();
        if (json.has("keywords")) {
            for (Object k : json.getJSONArray("keywords")) {
                keywords.add(k.toString());
            }
        }

        if (name.isEmpty() || price <= 0 || supply < 0) {
            sendErrorResponse(exchange, 400, "Invalid input data");
            return;
        }
        Food food = new Food();
        food.setName(name);
        food.setImageBase64(imageBase64);
        food.setDescription(description);
        food.setPrice(price);
        food.setSupply(supply);
        food.setKeywords(keywords);
        food.setRestaurant(restaurant);

        try {
            foodDAO.save(food);
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Error saving food: " + e.getMessage());
            return;
        }

        JSONObject responseJson = new JSONObject();
        responseJson.put("id", food.getId());
        responseJson.put("name", food.getName());
        responseJson.put("description", food.getDescription());
        responseJson.put("price", food.getPrice());
        responseJson.put("supply", food.getSupply());
        responseJson.put("keywords", food.getKeywords());

        sendJsonResponse(exchange, 201, responseJson);
    }
    private void updateFoodItem(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId = Long.parseLong(parts[2]);
        Long itemId = Long.parseLong(parts[4]);
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        String token = authHeader.substring(7);
        Long userId = JwtUtil.getUserIdFromToken(token);
        String userRole = JwtUtil.getRoleFromToken(token);

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can update food items");
            return;
        }
        Restaurant restaurant = restaurantDAO.findById(restaurantId);
        if (restaurant == null) {
            sendErrorResponse(exchange, 404, "Restaurant not found");
            return;
        }

        if (!restaurant.getSeller().getId().equals(userId)) {
            sendErrorResponse(exchange, 403, "Forbidden: You don't own this restaurant");
            return;
        }
        Food food = foodDAO.findById(itemId);
        if (food == null || !food.getRestaurant().getId().equals(restaurantId)) {
            sendErrorResponse(exchange, 404, "Food not found for this restaurant");
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);
        String name = json.optString("name", food.getName()).trim();
        String imageBase64 = json.optString("imageBase64", food.getImageBase64()).trim();
        String description = json.optString("description", food.getDescription()).trim();
        double price = json.optDouble("price", food.getPrice());
        int supply = json.optInt("supply", food.getSupply());
        food.setName(name);
        food.setImageBase64(imageBase64);
        food.setDescription(description);
        food.setPrice(price);
        food.setSupply(supply);
        if (json.has("keywords")) {
            List<String> keywords = new ArrayList<>();
            for (Object obj : json.getJSONArray("keywords")) {
                keywords.add((String) obj);
            }
            food.setKeywords(keywords);
        }
        foodDAO.update(food);
        JSONObject responseJson = new JSONObject();
        responseJson.put("id", food.getId());
        responseJson.put("name", food.getName());
        responseJson.put("description", food.getDescription());
        responseJson.put("price", food.getPrice());
        responseJson.put("supply", food.getSupply());
        responseJson.put("keywords", food.getKeywords());
        sendJsonResponse(exchange, 200, responseJson);
    }
    private void deleteFoodItem(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId = Long.parseLong(parts[2]);
        Long itemId = Long.parseLong(parts[4]);

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

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can delete food items");
            return;
        }
        org.hibernate.Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Restaurant restaurant = session.get(Restaurant.class, restaurantId);
            if (restaurant == null || !restaurant.getSeller().getId().equals(userId)) {
                sendErrorResponse(exchange, 403, "Forbidden: You don't own this restaurant");
                return;
            }

            Food foodToDelete = session.get(Food.class, itemId);
            if (foodToDelete == null || !foodToDelete.getRestaurant().getId().equals(restaurantId)) {
                sendErrorResponse(exchange, 404, "Not Found: Food item not found in your restaurant");
                return;
            }
            for (Menu menu : restaurant.getMenus()) {
                if(menu.getItems().contains(foodToDelete)) {
                    menu.getItems().remove(foodToDelete);
                }
            }
            session.remove(foodToDelete);
            tx.commit();
            sendResponse(exchange, 200, "Food item deleted successfully");
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }
    private void addMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId = Long.parseLong(parts[2]);

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        String token = authHeader.substring(7);
        Long userId = JwtUtil.getUserIdFromToken(token);
        String userRole = JwtUtil.getRoleFromToken(token);

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can add menu items");
            return;
        }

        Restaurant restaurant = restaurantDAO.findById(restaurantId);
        if (restaurant == null || !restaurant.getSeller().getId().equals(userId)) {
            sendErrorResponse(exchange, 403, "Forbidden: You don't own this restaurant");
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);

        String title = json.optString("title", "").trim();

        if (title.isEmpty()) {
            sendErrorResponse(exchange, 400, "Invalid data: title is required");
            return;
        }
        Menu newMenu = new Menu(title, restaurant);
        menuDAO.save(newMenu);

        JSONObject responseJson = new JSONObject();
        responseJson.put("id", newMenu.getId());
        responseJson.put("title", newMenu.getTitle());
        sendJsonResponse(exchange, 201, responseJson);
    }


    public void deleteMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId;
        Long menuId;

        try {
            restaurantId = Long.parseLong(parts[2]);
            menuId = Long.parseLong(parts[4]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sendErrorResponse(exchange, 400, "Invalid restaurant or menu ID in URL");
            return;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        String token = authHeader.substring(7);
        Long userId = JwtUtil.getUserIdFromToken(token);
        String userRole = JwtUtil.getRoleFromToken(token);

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can delete menus");
            return;
        }

        Restaurant restaurant = restaurantDAO.findById(restaurantId);
        if (restaurant == null || !restaurant.getSeller().getId().equals(userId)) {
            sendErrorResponse(exchange, 403, "Forbidden: You don't own this restaurant");
            return;
        }

        Optional<Menu> menuToDeleteOpt = Optional.ofNullable(menuDAO.findById(menuId));

        if (menuToDeleteOpt.isEmpty()) {
            sendErrorResponse(exchange, 404, "Not Found: Menu with ID " + menuId + " not found.");
            return;
        }

        Menu menuToDelete = menuToDeleteOpt.get();

        if (!menuToDelete.getRestaurant().getId().equals(restaurantId)) {
            sendErrorResponse(exchange, 403, "Forbidden: Menu does not belong to this restaurant.");
            return;
        }

        try {
            menuDAO.delete(menuToDelete);
            sendResponse(exchange, 200, "Menu with ID " + menuId + " has been deleted successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: Could not delete the menu.");
        }
    }
    public void addFoodToMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId, menuId, foodId;

        try {
            restaurantId = Long.parseLong(parts[2]);
            menuId = Long.parseLong(parts[4]);
            foodId = Long.parseLong(parts[6]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sendErrorResponse(exchange, 400, "Invalid IDs in URL.");
            return;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        String token = authHeader.substring(7);
        Long userId = JwtUtil.getUserIdFromToken(token);
        org.hibernate.Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Restaurant restaurant = session.get(Restaurant.class, restaurantId);
            Menu menu = session.get(Menu.class, menuId);
            Food food = session.get(Food.class, foodId);

            if (restaurant == null || !restaurant.getSeller().getId().equals(userId)) {
                sendErrorResponse(exchange, 403, "Forbidden: You don't own this restaurant");
                if (tx != null) tx.rollback();
                return;
            }
            if (menu == null || food == null) {
                sendErrorResponse(exchange, 404, "Menu or Food not found");
                if (tx != null) tx.rollback();
                return;
            }
            menu.getItems().size();

            if (menu.getItems().contains(food)) {
                sendErrorResponse(exchange, 409, "Conflict: This food item is already in the menu.");
                if (tx != null) tx.rollback();
                return;
            }

            menu.getItems().add(food);

            tx.commit();

            sendResponse(exchange, 200, "Food '" + food.getName() + "' added to menu '" + menu.getTitle() + "' successfully.");

        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    ///restaurants/{id}/menu/{title}/{item_id}
    public void removeFoodFromMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId, menuId, foodId;
        try {
            restaurantId = Long.parseLong(parts[2]);
            menuId = Long.parseLong(parts[4]);
            foodId = Long.parseLong(parts[6]);
        } catch (Exception e) {
            sendErrorResponse(exchange, 400, "Invalid IDs in URL.");
            return;
        }


        org.hibernate.Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Menu menu = session.get(Menu.class, menuId);
            Food food = session.get(Food.class, foodId);

            if (menu == null || food == null) {
                sendErrorResponse(exchange, 404, "Menu or Food not found");
                return;
            }

            if (!menu.getItems().contains(food)) {
                sendErrorResponse(exchange, 404, "Food item not found in this menu.");
                return;
            }

            menu.getItems().remove(food);
            session.merge(menu);
            tx.commit();
            sendResponse(exchange, 200, "Food item removed successfully from menu.");
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    public void getOrdersForRestaurant(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length < 3) {
            sendErrorResponse(exchange, 400, "Invalid path");
            return;
        }

        Long restaurantId;
        try {
            restaurantId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid restaurant ID");
            return;
        }

        // Authorization
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

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can view orders");
            return;
        }

        Restaurant restaurant = restaurantDAO.findById(restaurantId);
        if (restaurant == null) {
            sendErrorResponse(exchange, 404, "Restaurant not found");
            return;
        }

        if (!restaurant.getSeller().getId().equals(userId)) {
            sendErrorResponse(exchange, 403, "You do not own this restaurant");
            return;
        }

        List<Order> orders = orderDAO.findByRestaurantId(restaurantId);
        JSONArray responseArray = new JSONArray();
        for (Order order : orders) {
            JSONObject json = new JSONObject();
            json.put("id", order.getId());
            json.put("delivery_address", order.getDeliveryAddress());
            json.put("customer_id", order.getCustomerId());
            json.put("vendor_id", order.getVendorId());
            json.put("coupon_id", order.getCouponId());

            // --- START OF FIX ---
            // Replace the call to the old getItemIds() method
            if (order.getItems() != null) {
                List<Long> itemIds = order.getItems().stream()
                        .map(item -> item.getFood().getId())
                        .collect(Collectors.toList());
                json.put("item_ids", new JSONArray(itemIds));
            } else {
                json.put("item_ids", new JSONArray());
            }
            // --- END OF FIX ---

            json.put("raw_price", order.getRawPrice());
            json.put("tax_fee", order.getTaxFee());
            json.put("additional_fee", order.getAdditionalFee());
            json.put("courier_fee", order.getCourierFee());
            json.put("pay_price", order.getPayPrice());
            json.put("courier_id", order.getCourierId());
            json.put("status", order.getStatus());
            json.put("created_at", order.getCreatedAt().toString());
            json.put("updated_at", order.getUpdatedAt().toString());
            responseArray.put(json);
        }

        sendJsonResponse2(exchange, 200, responseArray);
    }
    public void updateOrderStatus(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long orderId = Long.parseLong(parts[3]);

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }

        String token = authHeader.substring(7);
        Long userId;
        String role;
        try {
            userId = JwtUtil.getUserIdFromToken(token);
            role = JwtUtil.getRoleFromToken(token);
        } catch (Exception e) {
            sendErrorResponse(exchange, 401, "Invalid token: " + e.getMessage());
            return;
        }

        if (!"SELLER".equalsIgnoreCase(role)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can update order status");
            return;
        }

        Order order = orderDAO.findById(orderId);
        if (order == null) {
            sendErrorResponse(exchange, 404, "Order not found");
            return;
        }

        Restaurant restaurant = restaurantDAO.findById(order.getVendorId());
        if (restaurant == null || !restaurant.getSeller().getId().equals(userId)) {
            sendErrorResponse(exchange, 403, "You do not own this restaurant");
            return;
        }

        JSONObject requestBody = parseRequestBody(exchange);
        String statusStr = requestBody.optString("status", null);
        if (statusStr == null) {
            sendErrorResponse(exchange, 400, "Missing status field");
            return;
        }

        try {
            OrderStatus newStatus = OrderStatus.valueOf(statusStr.toUpperCase());
            order.setStatus(newStatus);
            orderDAO.update(order);
            sendResponse(exchange, 200, "Order status updated successfully");
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, "Invalid status value");
        }
    }

    public void getRestaurantFood(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId;
        try {
            restaurantId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid restaurant ID");
            return;
        }

        Restaurant restaurant = restaurantDAO.findByIdWithFoods(restaurantId);

        if (restaurant == null) {
            sendErrorResponse(exchange, 404, "Restaurant not found");
            return;
        }

        var foodsOfRestaurant = restaurant.getFoods();

        JSONArray foodsArray = new JSONArray();
        JSONObject responseJson = new JSONObject();

        for (Food food : foodsOfRestaurant) {
            JSONObject foodJson = new JSONObject();
            foodJson.put("id", food.getId());
            foodJson.put("name", food.getName());
            foodJson.put("description", food.getDescription());
            foodJson.put("price", food.getPrice());
            foodJson.put("supply", food.getSupply());
            foodJson.put("keywords", food.getKeywords());
            foodsArray.put(foodJson);
        }

        responseJson.put("foods", foodsArray);

        sendJsonResponse(exchange, 200, responseJson);
    }



    private void getOrderDetails(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String path = exchange.getRequestURI().getPath();
            long orderId = Long.parseLong(path.split("/")[3]);

            Order order = session.get(Order.class, orderId);
            if (order == null) {
                sendErrorResponse(exchange, 404, "Order not found");
                return;
            }

            Hibernate.initialize(order.getItems());

            JSONArray itemsArray = new JSONArray();
            for (CartItem item : order.getItems()) {
                Hibernate.initialize(item.getFood());
                JSONObject itemJson = new JSONObject();
                itemJson.put("foodName", item.getFood().getName());
                itemJson.put("quantity", item.getQuantity());
                itemJson.put("price", item.getFood().getPrice());
                itemsArray.put(itemJson);
            }

            sendJsonResponse2(exchange, 200, itemsArray);
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid Order ID format in URL");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal server error");
        }
    }
    public void getRestaurantMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long restaurantId;
        try {
            restaurantId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid restaurant ID");
            return;
        }

        Restaurant restaurant = restaurantDAO.findByIdWithMenus(restaurantId);

        if (restaurant == null) {
            sendErrorResponse(exchange, 404, "Restaurant not found");
            return;
        }

        JSONArray menusArray = new JSONArray();

        if (restaurant.getMenus() != null) {
            for (Menu menu : restaurant.getMenus()) {
                JSONObject menuJson = new JSONObject();
                menuJson.put("id", menu.getId());
                menuJson.put("title", menu.getTitle());
                menusArray.put(menuJson);
            }
        }


        sendJsonResponse2(exchange, 200, menusArray);
    }
    public void getFoodsInMenu(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long menuId;
        try {
            menuId = Long.parseLong(parts[4]);
        } catch (Exception e) {
            sendErrorResponse(exchange, 400, "Invalid Menu ID.");
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Menu menu = session.get(Menu.class, menuId);
            if (menu == null) {
                sendErrorResponse(exchange, 404, "Menu not found.");
                return;
            }
            Hibernate.initialize(menu.getItems());
            JSONArray itemsArray = new JSONArray();
            for (Food food : menu.getItems()) {
                JSONObject foodJson = new JSONObject();
                foodJson.put("id", food.getId());
                foodJson.put("name", food.getName());
                itemsArray.put(foodJson);
            }
            sendJsonResponse2(exchange, 200, itemsArray);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }
    public void getOrdersForSeller(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }
        String token = authHeader.substring(7);
        Long userId = JwtUtil.getUserIdFromToken(token);
        String userRole = JwtUtil.getRoleFromToken(token);

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            sendErrorResponse(exchange, 403, "Forbidden: Only sellers can view their orders.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Restaurant> sellerRestaurants = session.createQuery(
                            "FROM Restaurant WHERE seller.id = :sellerId", Restaurant.class)
                    .setParameter("sellerId", userId)
                    .list();

            if (sellerRestaurants.isEmpty()) {
                sendJsonResponse2(exchange, 200, new JSONArray());
                return;
            }
            Map<Long, String> restaurantIdToNameMap = sellerRestaurants.stream()
                    .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));

            List<Long> restaurantIds = new ArrayList<>(restaurantIdToNameMap.keySet());
            List<Order> orders = session.createQuery(
                            "FROM Order WHERE vendorId IN (:vendorIds) ORDER BY createdAt DESC", Order.class)
                    .setParameter("vendorIds", restaurantIds)
                    .list();

            JSONArray responseArray = new JSONArray();
            for (Order order : orders) {
                JSONObject json = new JSONObject();
                json.put("id", order.getId());
                json.put("delivery_address", order.getDeliveryAddress());
                json.put("customer_id", order.getCustomerId());
                json.put("vendor_id", order.getVendorId());
                json.put("vendorName", restaurantIdToNameMap.getOrDefault(order.getVendorId(), "N/A"));
                json.put("pay_price", order.getPayPrice());
                json.put("status", order.getStatus().name());
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

    private void getRestaurantCoupons(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        try {
            String token = authHeader.substring(7);
            Long sellerId = JwtUtil.getUserIdFromToken(token);
            if (!"SELLER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden: Only sellers can view coupons.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            long restaurantId = Long.parseLong(parts[2]);

            Restaurant restaurant = restaurantDAO.findById(restaurantId);
            if (restaurant == null || !restaurant.getSeller().getId().equals(sellerId)) {
                sendErrorResponse(exchange, 403, "Forbidden: You do not own this restaurant.");
                return;
            }
            List<Coupon> coupons = couponDAO.findByRestaurantId(restaurantId);

            JSONArray responseArray = new JSONArray();
            for (Coupon coupon : coupons) {
                responseArray.put(couponToJson(coupon));
            }

            sendJsonResponse2(exchange, 200, responseArray);

        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Bad Request: Invalid restaurant ID.");
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void addCouponToRestaurant(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Unauthorized");
            return;
        }

        try {
            String token = authHeader.substring(7);
            Long sellerId = JwtUtil.getUserIdFromToken(token);
            if (!"SELLER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
                sendErrorResponse(exchange, 403, "Forbidden: Only sellers can add coupons.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            long restaurantId = Long.parseLong(parts[2]);

            Restaurant restaurant = restaurantDAO.findById(restaurantId);
            if (restaurant == null || !restaurant.getSeller().getId().equals(sellerId)) {
                sendErrorResponse(exchange, 403, "Forbidden: You do not own this restaurant.");
                return;
            }
            JSONObject json = new JSONObject(parseRequestBody2(exchange));

            String couponCode = json.optString("coupon_code", null);
            String typeStr = json.optString("type", null);

            if (couponCode == null || couponCode.trim().isEmpty() || typeStr == null || typeStr.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Invalid Input: Missing required fields.");
                return;
            }

            CouponType type = CouponType.valueOf(typeStr.toUpperCase());
            double value = json.getDouble("value");
            double minPrice = json.getDouble("min_price");
            int userCount = json.getInt("user_count");
            LocalDate startDate = LocalDate.parse(json.getString("start_date"));
            LocalDate endDate = LocalDate.parse(json.getString("end_date"));

            if (endDate.isBefore(startDate)) {
                sendErrorResponse(exchange, 400, "Invalid Input: End date cannot be before start date.");
                return;
            }

            Coupon newCoupon = new Coupon(couponCode, type, value, minPrice, userCount, startDate, endDate);
            newCoupon.setRestaurant(restaurant);

            couponDAO.save(newCoupon);

            JSONObject responseJson = couponToJson(newCoupon);
            sendJsonResponse(exchange, 201, responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
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
        if (coupon.getRestaurant() != null) {
            json.put("restaurant_id", coupon.getRestaurant().getId());
        }
        return json;
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

}