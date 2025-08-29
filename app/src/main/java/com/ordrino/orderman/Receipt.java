package com.ordrino.orderman;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Receipt {

    private String url;
    private Date timestamp;

    public Receipt() {
        // Public empty constructor needed for Firestore
    }

    public Receipt(String url, Date timestamp) {
        this.url = url;
        this.timestamp = timestamp;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @ServerTimestamp
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}