# 🍔 Byte Food - Core Java Backend

This repository contains the server-side application for **Byte Food**, a full-stack food delivery platform inspired by Snappfood.

Unlike typical backend projects built on heavyweight frameworks like Spring, this application is developed **entirely using Core Java** to demonstrate a deep, low-level understanding of backend fundamentals — from raw HTTP handling to secure authentication and ORM-based persistence.

---

## 🧩 Core Architecture & Technologies

### ⚙️ Custom HTTP Server
Built using Java’s native `com.sun.net.httpserver` package to manually handle:
- Low-level HTTP requests & responses  
- URL routing and parameter parsing  
- Multithreaded request handling

This minimalist approach provides full control over the server's behavior and emphasizes a strong understanding of web protocols.

### 🗃️ Database & ORM
- **Database**: MySQL  
- **ORM**: Hibernate (JPA)
  - Manages entity mapping, session handling, and transactions
  - Configured for automatic schema updates (`hbm2ddl.auto = update`)

### 🔐 Authentication & Security
- Stateless authentication via **JSON Web Tokens (JWT)**
- Role-Based Access Control (**RBAC**) enforced across API endpoints
- Secure user registration, login, and token refresh

### 🌐 RESTful API
All client-server communication is handled through cleanly designed **RESTful JSON endpoints**, following best practices in naming, status codes, and error handling.

---

## 👥 User Roles & Functionality

### 👤 Buyer
- Browse/search restaurants by name
- Advanced food filtering (name, keywords, price range)
- Add to cart, apply coupons, place orders
- View order history (filter by status/date)
- Submit ratings, comments, and upload multiple images for completed orders

### 🍽️ Seller
- Full CRUD for restaurant profiles (including logo upload)
- Manage menus and food items
- Update the status of incoming orders
- Create and manage discount coupons

### 🚚 Courier
- View available delivery tasks
- Accept orders for delivery
- Access delivery history

### 🛠️ Admin
- Central dashboard to manage:
  - Users (approval for Sellers/Couriers)
  - Orders
  - Transactions
- Create platform-wide discount coupons

---

## 🚀 Getting Started

### 📦 Prerequisites
- **Java 17+**
- **Apache Maven**
- **MySQL Server**

### 🧰 Setup Instructions

1. **Clone the Repository**
   ```bash
   git clone https://github.com/ARTESHMAN/Byte-Food-Backend.git
   ```

2. **Configure MySQL Database**
   - Create a schema named `snappfood` in your MySQL instance
   - Open `src/main/resources/hibernate.cfg.xml`
     - Set `connection.username` and `connection.password` to match your DB credentials
     - Leave `hbm2ddl.auto` as `update` for automatic schema sync

3. **Run the Server**
   - Open the project in **IntelliJ IDEA** (or your favorite IDE)
   - Navigate to:
     ```java
     src/main/java/org/croissantbuddies/snappfood/main/MainServer.java
     ```
   - Run the `main` method
   - Your backend will be live at:
     ```
     http://localhost:8000
     ```

---

## 💡 Highlights
- No Spring or external frameworks — only **pure Java**
- Demonstrates **clean architecture** with separation of concerns
- **Scalable** design ready for integration with frontend (JavaFX) or mobile clients
- Great for learning or showcasing **backend craftsmanship**

---

## 📫 Contact
Feel free to reach out with feedback, contributions, or questions:

📧 alimoghaddam5966@gmail.com  
🔗 GitHub: [@ARTESHMAN](https://github.com/ARTESHMAN)
