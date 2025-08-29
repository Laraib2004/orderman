package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback;
import com.stripe.stripeterminal.external.models.CollectConfiguration;
import com.stripe.stripeterminal.external.models.PaymentIntent;
import com.stripe.stripeterminal.external.models.TerminalException;

import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    private Cancelable collectCancelable;
    private double currentTableTotalPrice;
    private String restaurantId;
    private String tableId;
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
                getIntent().hasExtra(EXTRA_TABLE_NUMBER)) {
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
        }
        else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
            return;
        }

        // STEP 1: Create PaymentIntent via backend
        CustomConnectionTokenProvider tokenProvider = new CustomConnectionTokenProvider();
        tokenProvider.createPaymentIntent((int) (currentTableTotalPrice*100), new CustomConnectionTokenProvider.CreateIntentCallback() {
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
                                                                        restaurantId, tableId,
                                                                        confirmedIntent.getId(),
                                                                        new CustomConnectionTokenProvider.CaptureIntentCallback() {
                                                                            @Override
                                                                            public void onSuccess(String status, String invoiceUrl, String invoicePdf) {
                                                                                Log.d(TAG, "Payment captured! Status: " + status);
                                                                                runOnUiThread(() -> {
                                                                                    Toast.makeText(PaymentActivity.this, "Payment completed!", Toast.LENGTH_LONG).show();
                                                                                });
                                                                                finalizeOrder();
                                                                                // Optionally open the invoice
                                                                                Intent qr = new Intent(PaymentActivity.this, InvoiceQRCodeActivity.class);
                                                                                qr.putExtra(EXTRA_INVOICE_PDF_URL, invoiceUrl);
                                                                                addReceiptToHistory(invoiceUrl, tableId, restaurantId);
                                                                                startActivity(qr);
                                                                                setResult(RESULT_OK);
                                                                                finish();
                                                                            }

                                                                            @Override
                                                                            public void onFailure(Exception e) {
                                                                                Log.e(TAG, "Capture failed: ", e);
                                                                                runOnUiThread(() -> {
                                                                                    Toast.makeText(PaymentActivity.this, "Capture failed!", Toast.LENGTH_SHORT).show();
                                                                                });
                                                                            }
                                                                        });
                                                            }

                                                            @Override
                                                            public void onFailure(@NotNull TerminalException exception) {
                                                                Log.e(TAG, "Failed to confirm PaymentIntent: " + exception.getErrorMessage());
                                                                // Handle payment confirmation failure
                                                                runOnUiThread(() -> {
                                                                    Toast.makeText(PaymentActivity.this, "Payment failed: " + exception.getErrorMessage(), Toast.LENGTH_LONG).show();
                                                                    finish(); // Return to OrderSummaryActivity
                                                                });
                                                            }
                                                        }
                                                );
                                            }

                                            @Override
                                            public void onFailure(@NotNull TerminalException exception) {
                                                Log.e(TAG, "Failed to collect payment method: " + exception.getErrorMessage());
                                                // Handle payment collection failure (most likely a user cancellation)
                                                runOnUiThread(() -> {
                                                    Toast.makeText(PaymentActivity.this, "Payment cancelled or failed.", Toast.LENGTH_LONG).show();
                                                    finish(); // Go back to OrderSummaryActivity
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
                                // Handle the initial retrieval failure
                                runOnUiThread(() -> {
                                    Toast.makeText(PaymentActivity.this, "Failed to retrieve payment intent.", Toast.LENGTH_LONG).show();
                                    finish(); // Go back to OrderSummaryActivity
                                });
                            }
                        }
                );
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to create PaymentIntent via backend", e);
                // Handle the backend failure
                runOnUiThread(() -> {
                    Toast.makeText(PaymentActivity.this, "Failed to prepare payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish(); // Go back to OrderSummaryActivity
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

    private void addReceiptToHistory(String url, String tableId, String restaurantId) {
        if (tableId == null || tableId.isEmpty()) {
            Log.e(TAG, "Table ID is missing, cannot add receipt to history.");
            return;
        }

        // Create the document reference to the specific table
        // This implicitly creates the 'historyReceiptToday' subcollection if it doesn't exist.
        CollectionReference historyRef = db.collection("restaurants")
                .document(restaurantId)
                .collection("tables")
                .document(tableId)
                .collection("historyReceiptToday");

        // Prepare the data to be stored
        Map<String, Object> receiptData = new HashMap<>();
        receiptData.put("url", url);
        receiptData.put("timestamp", new Date()); // Use a Java Date object for server-side timestamp
        // You could also add other information like total amount, payment method, etc.

        // Add the new document to the subcollection. Firestore automatically generates a document ID.
        historyRef.add(receiptData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Receipt URL added to history for table " + tableId + " with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding receipt to history for table " + tableId, e);
                });
    }

    private void finalizeOrder() {
        // Fetch all documents in the 'currentOrder' subcollection
        tableDocRef = db.collection("restaurants").document(restaurantId).collection("tables").document(tableId);
        itemsOrderedRef = tableDocRef.collection("currentOrder");
        itemsOrderedRef.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch(); // Create a new batch for atomic operations

                    // 1. Delete all documents in the currentOrder subcollection
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        batch.delete(doc.getReference());
                        Log.d(TAG, "Adding delete operation for order item: " + doc.getId());
                    }

                    // 2. Update the table document: status to "Available", totalPrice to 0.0
                    Map<String, Object> tableUpdates = new HashMap<>();
                    tableUpdates.put("status", "Available");
                    tableUpdates.put("totalPrice", 0.0);
                    batch.update(tableDocRef, tableUpdates);
                    Log.d(TAG, "Adding update operation for table " + tableId + ": status=Available, totalPrice=0.0");

                    // Commit the batch writes
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(PaymentActivity.this, "Order finalized for Table " + tableNumber + ". Table now Available.", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Batch commit successful. Order cleared and table status updated.");

                                // After successful finalization, navigate back.
                                // If OrderTakingActivity is still in the backstack, its snapshot listener will
                                // automatically update the UI as soon as it comes to foreground.
                                // Finishing this activity will take user back to OrderTakingActivity,
                                // which will then update its TextView. If you want to go straight to TablesActivity,
                                // you might need to use FLAG_ACTIVITY_CLEAR_TOP.
                                finish(); // Finish OrderSummaryActivity

                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(PaymentActivity.this, "Error finalizing order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Failed to commit batch operations for order finalization: " + e.getMessage(), e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(PaymentActivity.this, "Error fetching order items to finalize: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to get current order items for finalization: " + e.getMessage(), e);
                });
    }
}