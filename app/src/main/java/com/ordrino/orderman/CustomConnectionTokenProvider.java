package com.ordrino.orderman;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback;
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider;
import com.stripe.stripeterminal.external.models.ConnectionTokenException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomConnectionTokenProvider implements ConnectionTokenProvider {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference itemsOrderedRef; // Reference to the subcollection of ordered items
    private DocumentReference tableDocRef; // Reference to the table document
    private DocumentReference restaurantDocRef;


    @Override
    public void fetchConnectionToken(final ConnectionTokenCallback callback) {
        executor.execute(() -> {
            try {
                // Replace with your server URL
                URL url = new URL("https://ordrino-backend.onrender.com/connection_token");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write("{}".getBytes());
                os.flush();
                os.close();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String secret = jsonResponse.getString("secret");
                Log.d("CustomConnection", secret);

                // Run callback on the main thread
                mainHandler.post(() -> callback.onSuccess(secret));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(
                        new ConnectionTokenException("Failed to fetch connection token", e)));
            }
        });
    }

    // ✅ Method to create a PaymentIntent
    public void createPaymentIntent(String restaurantId, int amount, CreateIntentCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://ordrino-backend.onrender.com/create_payment_intent");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("amount", amount); // in cents
                body.put("restaurant_id", restaurantId);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.flush();
                os.close();

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String clientSecret = jsonResponse.getString("client_secret");

                mainHandler.post(() -> callback.onSuccess(clientSecret));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    // ✅ Method to capture a PaymentIntent
    public void capturePaymentIntent(int tip, int subTotal,
            String restaurantId, String tableId, List<OrderItem> selectedOrderItems, String paymentIntentId, CaptureIntentCallback callback
    ) {
        // Initialize Firestore references
        tableDocRef = db.collection("restaurants").document(restaurantId).collection("tables").document(tableId);
        itemsOrderedRef = tableDocRef.collection("currentOrder");
        restaurantDocRef = db.collection("restaurants").document(restaurantId);
        restaurantDocRef.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String address = document.getString("address");
                            String city = document.getString("city");
                            String country = document.getString("country");
                            String name = document.getString("name");
                            String province = document.getString("province");
                            String recipientCode = document.getString("recipient_code");
                            String vatNumber = document.getString("vat_number");
                            GeoPoint location = document.getGeoPoint("location"); // Assuming it's a String
                            itemsOrderedRef.get().addOnSuccessListener(querySnapshot -> {
                                executor.execute(() -> {
                                    try {
                                        /*List<OrderItem> orderItems = new ArrayList<>();

                                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                            OrderItem item = doc.toObject(OrderItem.class);
                                            if (item != null) {
                                                item.setId(doc.getId()); // Ensure ID is set
                                                orderItems.add(item);
                                            }
                                        }*/
                                        URL url = new URL("https://ordrino-backend.onrender.com/capture_payment_intent");

                                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                        conn.setRequestMethod("POST");
                                        conn.setRequestProperty("Content-Type", "application/json");
                                        conn.setDoOutput(true);

                                        JSONObject body = new JSONObject();
                                        body.put("tip_amount_cents", tip);
                                        body.put("subtotal_amount_cents", subTotal);
                                        body.put("payment_intent_id", paymentIntentId);
                                        body.put("business_address", address);
                                        body.put("business_city", city);
                                        body.put("business_country", country);
                                        body.put("business_name", name);
                                        body.put("province", province);  // Note: Check spelling ("province" vs "province")
                                        body.put("recipient_code", recipientCode);
                                        body.put("business_vat", vatNumber);
                                        body.put("restaurant_id", restaurantId);

                                        JSONArray itemsArray = new JSONArray();
                                        for (OrderItem item : selectedOrderItems) {
                                            Log.d("SELECTED ITEMS: ", item.getId());
                                            JSONObject itemJson = new JSONObject();
                                            itemJson.put("name", item.getName());
                                            itemJson.put("quantity", item.getQuantity());
                                            itemJson.put("unit_price", (int) (item.getPrice()*100)); // In cents
                                            itemsArray.put(itemJson);
                                        }

                                        body.put("items", itemsArray);

                                        OutputStream os = conn.getOutputStream();
                                        os.write(body.toString().getBytes());
                                        os.flush();
                                        os.close();

                                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                        StringBuilder response = new StringBuilder();
                                        String inputLine;

                                        while ((inputLine = in.readLine()) != null) {
                                            response.append(inputLine);
                                        }
                                        in.close();

                                        JSONObject jsonResponse = new JSONObject(response.toString());
                                        String status = jsonResponse.getString("status");
                                        String invoiceUrl = jsonResponse.getString("hosted_invoice_url");
                                        String invoicePdfUrl = jsonResponse.getString("invoice_pdf");

                                        mainHandler.post(() -> callback.onSuccess(status, invoiceUrl, invoicePdfUrl));

                                    } catch (Exception e) {
                                        mainHandler.post(() -> callback.onFailure(e));
                                    }
                                });
                            });
                        }
                    }
                });
    }

    public void createCashPayment(int subTotal, int tip, String address, String city, String country,
          String name, String province, String recipientCode, String vatNumber, String restaurantId,
          List<OrderItem> items, String description, CreateCashCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://ordrino-backend.onrender.com/cash_payment");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("tip_amount_cents", tip);
                body.put("subtotal_amount_cents", subTotal);
                body.put("currency", "eur");
                body.put("description", description);
                body.put("business_address", address);
                body.put("business_city", city);
                body.put("business_country", country);
                body.put("business_name", name);
                body.put("province", province);  // Note: Check spelling ("province" vs "province")
                body.put("recipient_code", recipientCode);
                body.put("business_vat", vatNumber);
                body.put("restaurant_id", restaurantId);

                JSONArray itemsArray = new JSONArray();
                for (OrderItem item : items) {
                    JSONObject itemJson = new JSONObject();
                    itemJson.put("name", item.getName());
                    itemJson.put("quantity", item.getQuantity());
                    itemJson.put("unit_price", (int) (item.getPrice()*100)); // In cents
                    itemsArray.put(itemJson);
                }

                body.put("items", itemsArray);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.flush();
                os.close();

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String invoiceUrl = jsonResponse.getString("hosted_invoice_url");
                String invoicePdf = jsonResponse.getString("invoice_pdf");

                mainHandler.post(() -> callback.onSuccess(invoiceUrl, invoicePdf));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    public void createOrupdateProduct(String restaurantId, MenuItem item, boolean create, CreateUpdateProductCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://ordrino-backend.onrender.com/create-update-product");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("create", create);
                body.put("itemName", item.getName());
                body.put("unit_amount", (int)(item.getPrice()*100)); // in cents
                body.put("available", item.isAvailable());
                body.put("description", item.getDescription());
                body.put("tax_code", item.getTaxCode());
                body.put("prod_id", item.getProdId());
                body.put("restaurant_id", restaurantId);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.flush();
                os.close();

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String status = jsonResponse.getString("prodId");

                mainHandler.post(() -> callback.onSuccess(status));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }



    // 🔹 Callback Interfaces
    public interface CreateIntentCallback {
        void onSuccess(String clientSecret);
        void onFailure(Exception e);
    }

    public interface CaptureIntentCallback {
        void onSuccess(String status, String invoiceUrl, String invoicePdf);
        void onFailure(Exception e);
    }

    public interface CreateCashCallback {
        void onSuccess(String hostedInvoiceUrl, String invoicePdfUrl);
        void onFailure(Exception e);
    }

    public interface CreateUpdateProductCallback {
        void onSuccess(String prodId);
        void onFailure(Exception e);
    }

}
