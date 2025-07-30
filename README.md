Byte Food - Core Java Backend
This repository contains the server-side application for the Byte Food delivery platform, a full-stack project inspired by Snappfood. This backend is built entirely from the ground up using core Java technologies, deliberately avoiding major frameworks like Spring to showcase a deep understanding of fundamental backend concepts.

This server powers all business logic, data persistence, and API endpoints for the accompanying JavaFX Desktop Client.

Core Architecture & Technology ⚙️
Custom HTTP Server: The server is implemented using Java's native com.sun.net.httpserver, handling raw HTTP requests and routing them to dedicated handlers. This demonstrates a foundational grasp of web protocols and multithreading.

Database & ORM: The application uses MySQL as its relational database. Hibernate serves as the powerful Object-Relational Mapping (ORM) framework, managing all database sessions, transactions, and entity mappings.

Authentication: The API is secured using JSON Web Tokens (JWT). This provides a stateless, secure, and scalable method for authenticating users and managing role-based access control (RBAC).

RESTful API Design: The backend exposes a comprehensive set of RESTful endpoints to handle all application functionalities. All communication with the client is performed using the JSON data format.

Key Features 🚀
The platform is designed to support four distinct user roles, each with a unique set of permissions and capabilities:

User & Authentication
Secure registration and login for all roles.

Role-based access control restricting API access.

Admin approval system for new Seller and Courier accounts.

Buyer Functionality
Browse and search for restaurants by name.

Advanced food search by name, keywords, and price range (min/max).

Complete order workflow: manage a shopping cart, apply coupons, and submit orders.

View detailed order history with filtering by date and status.

Submit ratings and reviews (including comments and multiple image uploads) for completed orders.

Seller Functionality
Full CRUD (Create, Read, Update, Delete) operations for restaurant details, including logo management.

Comprehensive menu management: create, edit, and delete menus and food items.

View and update the status of incoming orders.

Create and manage restaurant-specific discount coupons.

Courier Functionality
View a list of available orders ready for pickup.

Accept delivery tasks.

View a complete history of past deliveries.

Admin Functionality
A central dashboard to monitor and manage all users, orders, and financial transactions across the platform.

Create and manage global discount coupons that are valid for all restaurants.

Getting Started 🏃‍♂️
Follow these steps to get the backend server running locally.

Prerequisites
Java Development Kit (JDK) 17 or higher.

Apache Maven.

A running MySQL Server instance.

Installation & Setup
Clone the Repository

Bash

git clone https://github.com/ARTESHMAN/Byte-Food-Backend.git
Configure the Database

In your MySQL instance, create a new database schema named snappfood.

Open the src/main/resources/hibernate.cfg.xml file.

Update the connection.username and connection.password properties with your local MySQL credentials.

The hbm2ddl.auto property is set to update, which allows Hibernate to automatically create or update the necessary database tables on the first run.

Run the Server

Open the project in your preferred Java IDE (e.g., IntelliJ IDEA).

Locate the main method in the src/main/java/org/croissantbuddies/snappfood/main/MainServer.java class and run it.

The server will start and listen for incoming requests on http://localhost:8000. It is now ready to connect to the frontend client.
