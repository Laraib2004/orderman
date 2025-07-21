package com.ordrino.orderman.models;

import com.google.firebase.firestore.DocumentId;

public class OrderItem {

    @DocumentId
    private String id; // This will store the MenuItem's ID (the document ID for the OrderItem)
    private String menuItemId; // Explicitly store the MenuItem's ID for clarity/querying
    private String name;
    private double price;
    private int quantity;
    private String category;
    private String type;
    private String status; // e.g., "Preparing", "Sent", "Served"

    public OrderItem() {
        // Required public no-argument constructor for Firestore deserialization
    }

    public OrderItem(String menuItemId, String name, double price, int quantity, String category, String type, String status) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.category = category;
        this.type = type;
        this.status = status;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(String menuItemId) {
        this.menuItemId = menuItemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}