package com.ordrino.orderman;

import com.google.firebase.firestore.DocumentId;
import java.io.Serializable;

public class MenuItem implements Serializable {

    @DocumentId
    private String id; // This will automatically be populated with the Firestore document ID

    private String name;
    private String description;
    private double price;
    private String category;
    private String type;
    private boolean available;
    private String imageUrl;

    // Required public no-argument constructor for Firestore deserialization
    public MenuItem() {}

    // Constructor for creating new MenuItems (id is usually null initially for new items)
    public MenuItem(String name, String description, double price, String category, String type, boolean available, String imageUrl) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.type = type;
        this.available = available;
        this.imageUrl = imageUrl;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}