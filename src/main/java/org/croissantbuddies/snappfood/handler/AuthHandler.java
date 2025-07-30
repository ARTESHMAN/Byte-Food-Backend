package org.croissantbuddies.snappfood.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.croissantbuddies.snappfood.ENUM.Role;
import org.croissantbuddies.snappfood.dao.UserDAO;
import org.croissantbuddies.snappfood.dto.StatusResult;
import org.croissantbuddies.snappfood.entity.*;
import org.croissantbuddies.snappfood.util.HibernateUtil;
import org.croissantbuddies.snappfood.util.JwtUtil;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;


public class AuthHandler implements HttpHandler {

    private final UserDAO userDAO = new UserDAO(HibernateUtil.getSessionFactory());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String contextPath = exchange.getHttpContext().getPath();
        String relativePath = path.substring(contextPath.length());
        if ("/register".equals(relativePath) && "POST".equalsIgnoreCase(method)) {
            handleRegister(exchange);
        } else if ("/login".equals(relativePath) && "POST".equalsIgnoreCase(method)) {
            handleLogin(exchange);
        } else if ("/profile".equals(relativePath) && "GET".equalsIgnoreCase(method)) {
            handleProfile(exchange);
        } else if ("/profile".equals(relativePath) && "PUT".equalsIgnoreCase(method)) {
            handleProfile(exchange);
        } else {
            sendResponse(exchange, 404, "Not Found");
        }

    }


    private void handleRegister(HttpExchange exchange) throws IOException {
        try {
            JSONObject json = new JSONObject(readRequestBody(exchange));

            String fullName = json.getString("full_name");
            String phone = json.getString("phone");
            String password = json.getString("password");
            String role = json.getString("role");
            String email = json.optString("email", null);
            String profileImageBase64 = json.optString("profileImageBase64", null);
            String address = json.optString("address", null);

            JSONObject bankInfoJson = json.optJSONObject("bank_info");
            String bankName = null;
            String accountNumber = null;
            if (bankInfoJson != null) {
                bankName = bankInfoJson.optString("bank_name", null);
                accountNumber = bankInfoJson.optString("account_number", null);
                if (bankName != null && bankName.isEmpty()) bankName = null;
                if (accountNumber != null && accountNumber.isEmpty()) accountNumber = null;
            }
            User user;
            switch (role.toLowerCase()) {
                case "buyer" -> {
                    Buyer buyer = new Buyer();
                    if (bankName != null) buyer.setBankName(bankName);
                    if (accountNumber != null) buyer.setAccountNumber(accountNumber);
                    user = buyer;
                    user.setRole(Role.BUYER);
                    System.out.println("Role before saving: " + user.getRole());
                }
                case "seller" -> {
                    Seller seller = new Seller();
                    if (bankName != null) seller.setBankName(bankName);
                    if (accountNumber != null) seller.setAccountNumber(accountNumber);
                    user = seller;
                    user.setRole(Role.SELLER);
                }
                case "courier" -> {
                    user = new Courier();
                    user.setRole(Role.COURIER);
                }
                default -> {
                    sendResponse(exchange, 400, "Invalid role");
                    return;
                }
            }
            String fullNameC = json.getString("full_name").trim();
            if (fullNameC.isEmpty() || !fullNameC.matches("^[\\p{L} .'-]+$")) {
                JSONObject response = new JSONObject();
                response.put("error", "Invalid full name");
                sendJsonResponse(exchange, 400, response);
                return;
            }


            if (!phone.matches("^09\\d{9}$")) {
                JSONObject response = new JSONObject();
                response.put("error", "Invalid phone number format. It must start with 09 and have exactly 11 digits.");
                sendJsonResponse(exchange, 400, response);
                return;
            }
            if (bankName != null && (!bankName.isEmpty() && !bankName.matches("^[\\p{L} .'-]+$"))) {
                JSONObject response = new JSONObject();
                response.put("error", "Invalid Bank name");
                sendJsonResponse(exchange, 400, response);
                return;
            }

            if (accountNumber!=null&&!accountNumber.matches("\\d{16}")&&!accountNumber.isEmpty()) {
                JSONObject response = new JSONObject();
                response.put("error", "Invalid cart number format. It must be exactly 16 digits.");
                sendJsonResponse(exchange, 400, response);
                return;
            }

            user.setFullName(fullName);
            user.setPhone(phone);
           if(email!= null&&email.isEmpty()) {user.setEmail(email);}
            user.setPassword(password);
            if(profileImageBase64!= null&&profileImageBase64.isEmpty()) user.setProfileImageBase64(profileImageBase64);
            if (address != null) user.setAddress(address);

            StatusResult result = userDAO.register(user);

            JSONObject response = new JSONObject();
            if (result.getStatusCode() == 200) {
                JwtUtil.TokenWhitelist.add(result.getToken());
                response.put("message", result.getMessage());
                response.put("userId", result.getUserId());
                response.put("token", result.getToken());
                sendJsonResponse(exchange, 200, response);
            } else {
                response.put("error", result.getMessage());
                sendJsonResponse(exchange, result.getStatusCode(), response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 400, "Invalid input data");
        }
    }



    private void handleLogin(HttpExchange exchange) throws IOException {
        try {
            JSONObject json = new JSONObject(readRequestBody(exchange));
            String phone = json.getString("phone");
            String password = json.getString("password");

            if ("admin".equals(phone) && "adminpass".equals(password)) {
                String adminToken = JwtUtil.generateToken(0L, "ADMIN");
                JwtUtil.TokenWhitelist.add(adminToken);
                JSONObject adminUserJson = new JSONObject();
                adminUserJson.put("id", 0);
                adminUserJson.put("full_name", "Administrator");
                adminUserJson.put("phone", "admin");
                adminUserJson.put("email", "admin@system.com");
                adminUserJson.put("role", "ADMIN");
                adminUserJson.put("address", "N/A");
                adminUserJson.put("profileImageBase64", "");
                adminUserJson.put("bank_info", new JSONObject());

                String jsonResponse = String.format(
                        "{\"message\":\"Admin logged in successfully\",\"token\":\"%s\",\"user\":%s}",
                        escapeJson(adminToken),
                        adminUserJson.toString()
                );
                sendRawJsonResponse(exchange, 200, jsonResponse);
                return;
            }
            StatusResult result = userDAO.login(phone, password);


            if (!phone.matches("^09\\d{9}$")) {
                JSONObject response = new JSONObject();
                response.put("error", "Invalid phone number format. It must start with 09 and have exactly 11 digits.");
                sendJsonResponse(exchange, 400, response);
                return;
            }
            if (result.getStatusCode() == 200) {
                User user = result.getUser();
                JwtUtil.TokenWhitelist.add(result.getToken());
                JSONObject bankInfoJson = new JSONObject();
                bankInfoJson.put("bank_name", user.getBankName() != null ? user.getBankName() : JSONObject.NULL);
                bankInfoJson.put("account_number", user.getAccountNumber() != null ? user.getAccountNumber() : JSONObject.NULL);

                JSONObject userJson = new JSONObject();
                userJson.put("id", user.getId() != null ? user.getId() : 0);
                userJson.put("full_name", user.getFullName() != null ? user.getFullName() : "");
                userJson.put("phone", user.getPhone() != null ? user.getPhone() : "");
                userJson.put("email", user.getEmail() != null ? user.getEmail() : "");
                userJson.put("role", user.getRole() != null ? user.getRole().toString() : "");
                String address = "";
                if (user instanceof Buyer) {
                    address = ((Buyer) user).getAddress() != null ? ((Buyer) user).getAddress() : "";
                } else if (user instanceof Seller) {
                    address = ((Seller) user).getAddress() != null ? ((Seller) user).getAddress() : "";
                }
                userJson.put("address", address);

                userJson.put("profileImageBase64", user.getProfileImageBase64() != null ? user.getProfileImageBase64() : "");
                userJson.put("bank_info", bankInfoJson);
                String jsonResponse = String.format(
                        "{\"message\":\"%s\",\"token\":\"%s\",\"user\":%s}",
                        escapeJson(result.getMessage() != null ? result.getMessage() : ""),
                        escapeJson(result.getToken() != null ? result.getToken() : ""),
                        userJson.toString()
                );
                sendRawJsonResponse(exchange, 200, jsonResponse);



            } else {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", result.getMessage());
                sendJsonResponse(exchange, result.getStatusCode(), errorResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 400, "Invalid input");
        }
    }

    private void handleProfile(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "Unauthorized: No token provided");
            return;
        }
        String token = authHeader.substring(7);
        Long userId;
        try {
            userId = JwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            sendResponse(exchange, 401, "Invalid token: " + e.getMessage());
            return;
        }

        try {
            User user = userDAO.findById(userId);
            if (user == null) {
                sendResponse(exchange, 404, "User not found with ID: " + userId);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", user.getId());
                userJson.put("full_name", user.getFullName());
                userJson.put("phone", user.getPhone());
                userJson.put("email", user.getEmail());
                userJson.put("role", user.getRole().toString());
                userJson.put("address", user.getAddress());
                userJson.put("profileImageBase64", user.getProfileImageBase64());
                userJson.put("amount", user.getAmount());

                JSONObject bankInfoJson = new JSONObject();
                bankInfoJson.put("bank_name", user.getBankName() != null ? user.getBankName() : "");
                bankInfoJson.put("account_number", user.getAccountNumber() != null ? user.getAccountNumber() : "");
                userJson.put("bank_info", bankInfoJson);

                if (user instanceof Seller) {
                    userJson.put("status", ((Seller) user).getStatus().name());
                } else if (user instanceof Courier) {
                    userJson.put("status", ((Courier) user).getStatus().name());
                } else {
                    userJson.put("status", "N/A");
                }


                sendJsonResponse(exchange, 200, userJson);

            } else if ("PUT".equalsIgnoreCase(method)) {
                // منطق PUT بدون تغییر باقی می‌ماند...
                JSONObject json = new JSONObject(parseRequestBody2(exchange));
                user.setFullName(json.optString("full_name", user.getFullName()));
                user.setPhone(json.optString("phone", user.getPhone()));
                user.setEmail(json.optString("email", user.getEmail()));
                user.setAddress(json.optString("address", user.getAddress()));
                user.setProfileImageBase64(json.optString("profileImageBase64", user.getProfileImageBase64()));

                JSONObject bankInfo = json.optJSONObject("bank_info");
                if (bankInfo != null) {
                    user.setBankName(bankInfo.optString("bank_name"));
                    user.setAccountNumber(bankInfo.optString("account_number"));
                }

                userDAO.update(user);
                sendResponse(exchange, 200, "Profile updated successfully");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void sendRawJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    private String escapeJson(String s) {
        return s.replace("\"", "\\\"");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        JSONObject json = new JSONObject();
        json.put("message", message);
        sendJsonResponse(exchange, statusCode, json);
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