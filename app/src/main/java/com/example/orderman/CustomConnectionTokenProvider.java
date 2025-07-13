package com.example.orderman;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback;
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider;
import com.stripe.stripeterminal.external.models.ConnectionTokenException;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomConnectionTokenProvider implements ConnectionTokenProvider {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
    public void createPaymentIntent(int amount, CreateIntentCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://ordrino-backend.onrender.com/create_payment_intent");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("amount", amount); // in cents

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
    public void capturePaymentIntent(String paymentIntentId, CaptureIntentCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://ordrino-backend.onrender.com/capture_payment_intent");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("payment_intent_id", paymentIntentId);

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
        void onSuccess(String status);
        void onFailure(Exception e);
    }
}
