// File: OrderItem.java
package com.ordrino.orderman;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.firestore.DocumentId;

public class OrderItem implements  Parcelable {

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
        // Required empty constructor for Firestore
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

    // Getters and Setters for each field...
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

    // START PARCELABLE IMPLEMENTATION

    protected OrderItem(Parcel in) {
        id = in.readString();
        name = in.readString();
        price = in.readDouble();
        quantity = in.readInt();
        category = in.readString();
        type = in.readString();
        status = in.readString();
    }

    public static final Parcelable.Creator<OrderItem> CREATOR = new Parcelable.Creator<OrderItem>() {
        @Override
        public OrderItem createFromParcel(Parcel in) {
            return new OrderItem(in);
        }

        @Override
        public OrderItem[] newArray(int size) {
            return new OrderItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeDouble(price);
        dest.writeInt(quantity);
        dest.writeString(category);
        dest.writeString(type);
        dest.writeString(status);
    }
}