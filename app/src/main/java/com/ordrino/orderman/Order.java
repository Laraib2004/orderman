package com.ordrino.orderman;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.List;

public class Order {

    private String tableId;
    private int tableNr;
    private DocumentReference documentReference;
    private List<OrderItem> orderedItems;
    private String status; // e.g., "New", "Preparing", "Ready"
    private @ServerTimestamp Date timestamp;

    public Order() {
        // Required public no-argument constructor for Firestore deserialization
    }

    // Getters and Setters
    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public List<OrderItem> getOrderedItems() {
        return orderedItems;
    }

    public void setOrderedItems(List<OrderItem> orderedItems) {
        this.orderedItems = orderedItems;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public int getTableNr() {
        return tableNr;
    }

    public void setTableNr(int tableNr) {
        this.tableNr = tableNr;
    }

    public DocumentReference getDocumentReference() {
        return documentReference;
    }

    public void setDocumentReference(DocumentReference documentReference) {
        this.documentReference = documentReference;
    }
}