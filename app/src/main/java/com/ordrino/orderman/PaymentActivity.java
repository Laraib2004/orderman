package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_SELECTED_ITEMS;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_BACKEND_URL;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_SUBTOTAL_AMOUNT;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_TIP_AMOUNT;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_NUMBER;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_TOTAL_PRICE;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback;
import com.stripe.stripeterminal.external.models.CollectConfiguration;
import com.stripe.stripeterminal.external.models.PaymentIntent;
import com.stripe.stripeterminal.external.models.TerminalException;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    private Cancelable collectCancelable;
    private double currentTableTotalPrice;
    private ArrayList<OrderItem> selectedItemsList;
    private String restaurantId;
    private String backendUrl;
    private String tableId;
    private double tip;
    private double subTotal;
    private  int tableNumber;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference itemsOrderedRef; // Reference to the subcollection of ordered items
    private DocumentReference tableDocRef; // Reference to the table document

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        Log.d(TAG, "Starting PaymentActivity");

        if (getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE) &&
                getIntent().hasExtra(EXTRA_RESTAURANT_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_NUMBER) &&
                getIntent().hasExtra(EXTRA_SELECTED_ITEMS) &&
                getIntent().hasExtra(EXTRA_TIP_AMOUNT) &&
                getIntent().hasExtra(EXTRA_SUBTOTAL_AMOUNT) &&
                getIntent().hasExtra(EXTRA_BACKEND_URL)) {
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
            selectedItemsList = getIntent().getParcelableArrayListExtra(EXTRA_SELECTED_ITEMS);
            tip = getIntent().getDoubleExtra(EXTRA_TIP_AMOUNT, 0.0);
            subTotal = getIntent().getDoubleExtra(EXTRA_SUBTOTAL_AMOUNT, 0.0);
            backendUrl = getIntent().getStringExtra(EXTRA_BACKEND_URL);
        }
        else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
            return;
        }

        // STEP 1: Create PaymentIntent via backend
        CustomConnectionTokenProvider tokenProvider = new CustomConnectionTokenProvider(restaurantId, backendUrl);
        tokenProvider.createPaymentIntent(restaurantId, (int) (currentTableTotalPrice*100), new CustomConnectionTokenProvider.CreateIntentCallback() {
            @Override
            public void onSuccess(String clientSecret) {
                Log.d(TAG, "Client secret received: " + clientSecret);

                // STEP 2: Retrieve the PaymentIntent from Stripe
                Terminal.getInstance().retrievePaymentIntent(
                        clientSecret,
                        new PaymentIntentCallback() {
                            @Override
                            public void onSuccess(@NotNull PaymentIntent paymentIntent) {
                                Log.d(TAG, "PaymentIntent retrieved: " + paymentIntent);

                                // STEP 3: Collect Payment Method
                                collectCancelable = Terminal.getInstance().collectPaymentMethod(
                                        paymentIntent,
                                        new PaymentIntentCallback() {
                                            @Override
                                            public void onSuccess(@NotNull PaymentIntent collectedIntent) {
                                                Log.d(TAG, "Payment method collected. Confirming...");

                                                // STEP 4: Confirm the payment
                                                Terminal.getInstance().confirmPaymentIntent(
                                                        collectedIntent,
                                                        new PaymentIntentCallback() {
                                                            @Override
                                                            public void onSuccess(@NotNull PaymentIntent confirmedIntent) {
                                                                Log.d(TAG, "Payment confirmed: " + confirmedIntent.getId());

                                                                // STEP 5: Capture it via backend
                                                                tokenProvider.capturePaymentIntent(
                                                                        (int)(tip*100),
                                                                        (int)(subTotal*100),
                                                                        restaurantId, tableId,
                                                                        selectedItemsList,
                                                                        confirmedIntent.getId(),
                                                                        new CustomConnectionTokenProvider.CaptureIntentCallback() {
                                                                            @Override
                                                                            public void onSuccess(String status, String invoiceUrl, String invoicePdf) {
                                                                                Log.d(TAG, "Payment captured! Status: " + status);
                                                                                runOnUiThread(() -> {
                                                                                    Toast.makeText(PaymentActivity.this, "Payment completed!", Toast.LENGTH_LONG).show();
                                                                                });

                                                                                // **CRUCIAL CHANGE HERE**
                                                                                // Instead of finalizing the payment here, send the result back to OrderSummaryActivity.
                                                                                Intent resultIntent = new Intent();
                                                                                resultIntent.putParcelableArrayListExtra(EXTRA_SELECTED_ITEMS, selectedItemsList);
                                                                                resultIntent.putExtra(EXTRA_INVOICE_PDF_URL, invoiceUrl);
                                                                                setResult(RESULT_OK, resultIntent);

                                                                                // The rest of the logic remains.
                                                                                addReceiptToHistory(invoiceUrl, tableId, restaurantId);
                                                                                Intent qr = new Intent(PaymentActivity.this, InvoiceQRCodeActivity.class);
                                                                                qr.putExtra(EXTRA_INVOICE_PDF_URL, invoiceUrl);
                                                                                startActivity(qr);
                                                                                finish();
                                                                            }

                                                                            @Override
                                                                            public void onFailure(Exception e) {
                                                                                Log.e(TAG, "Capture failed: ", e);
                                                                                runOnUiThread(() -> {
                                                                                    Toast.makeText(PaymentActivity.this, "Capture failed!", Toast.LENGTH_SHORT).show();
                                                                                    // It's a good practice to still finish the activity even on capture failure
                                                                                    // so the user doesn't get stuck.
                                                                                    setResult(RESULT_CANCELED);
                                                                                    finish();
                                                                                });
                                                                            }
                                                                        });
                                                            }

                                                            @Override
                                                            public void onFailure(@NotNull TerminalException exception) {
                                                                Log.e(TAG, "Failed to confirm PaymentIntent: " + exception.getErrorMessage());
                                                                runOnUiThread(() -> {
                                                                    Toast.makeText(PaymentActivity.this, "Payment failed: " + exception.getErrorMessage(), Toast.LENGTH_LONG).show();
                                                                    setResult(RESULT_CANCELED);
                                                                    finish();
                                                                });
                                                            }
                                                        }
                                                );
                                            }

                                            @Override
                                            public void onFailure(@NotNull TerminalException exception) {
                                                Log.e(TAG, "Failed to collect payment method: " + exception.getErrorMessage());
                                                runOnUiThread(() -> {
                                                    Toast.makeText(PaymentActivity.this, "Payment cancelled or failed.", Toast.LENGTH_LONG).show();
                                                    setResult(RESULT_CANCELED);
                                                    finish();
                                                });
                                            }
                                        },
                                        new CollectConfiguration.Builder()
                                                .build()
                                );
                            }

                            @Override
                            public void onFailure(@NotNull TerminalException exception) {
                                Log.e(TAG, "Failed to retrieve PaymentIntent: " + exception.getErrorMessage());
                                runOnUiThread(() -> {
                                    Toast.makeText(PaymentActivity.this, "Failed to retrieve payment intent.", Toast.LENGTH_LONG).show();
                                    setResult(RESULT_CANCELED);
                                    finish();
                                });
                            }
                        }
                );
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to create PaymentIntent via backend", e);
                runOnUiThread(() -> {
                    Toast.makeText(PaymentActivity.this, "Failed to prepare payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (collectCancelable != null) {
            collectCancelable.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "collectCancelable canceled successfully");
                }

                @Override
                public void onFailure(@NotNull TerminalException e) {
                    Log.e(TAG, "Failed to cancel collectCancelable", e);
                }
            });
        }
    }

    // Keep the addReceiptToHistory method here as it's a direct consequence of a successful payment.
    private void addReceiptToHistory(String url, String tableId, String restaurantId) {
        if (tableId == null || tableId.isEmpty()) {
            Log.e(TAG, "Table ID is missing, cannot add receipt to history.");
            return;
        }

        CollectionReference historyRef = db.collection("restaurants")
                .document(restaurantId)
                .collection("tables")
                .document(tableId)
                .collection("historyReceiptToday");

        Map<String, Object> receiptData = new HashMap<>();
        receiptData.put("url", url);
        receiptData.put("timestamp", new Date());

        historyRef.add(receiptData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Receipt URL added to history for table " + tableId + " with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding receipt to history for table " + tableId, e);
                });
    }
}