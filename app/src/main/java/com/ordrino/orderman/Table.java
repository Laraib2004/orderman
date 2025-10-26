package com.ordrino.orderman;

import com.google.firebase.firestore.DocumentId;
import java.io.Serializable;
// In Table.java
// In Table.java

public class Table implements Serializable {
    @DocumentId
    private String id;
    private int number;
    private int capacity;
    private String status;
    private String section;
    private String currentOrderId;
    private double totalPrice;
    private String activeOrderQueueId;

    public Table() {
        // No-argument constructor needed for Firestore
    }

    // You might already have a constructor, update it to include currentOrderId
    public Table(int number, int capacity, String status, String section, String currentOrderId, double totalPrice) {
        this.number = number;
        this.capacity = capacity;
        this.status = status;
        this.section = section;
        this.currentOrderId = currentOrderId;
        this.totalPrice = totalPrice;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getCurrentOrderId() { // <--- ADD GETTER
        return currentOrderId;
    }

    public void setCurrentOrderId(String currentOrderId) { // <--- ADD SETTER
        this.currentOrderId = currentOrderId;
    }

    public double getTotalPrice() { // <--- ADD GETTER
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) { // <--- ADD SETTER
        this.totalPrice = totalPrice;
    }
    public String getActiveOrderQueueId() { return activeOrderQueueId; }
    public void setActiveOrderQueueId(String id) { this.activeOrderQueueId = id; }

}