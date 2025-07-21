package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_NUMBER;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_TOTAL_PRICE;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderSummaryActivity extends AppCompatActivity {

    private static final String TAG = "OrderSummaryActivity";
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference itemsOrderedRef; // Reference to the subcollection of ordered items
    private DocumentReference tableDocRef; // Reference to the table document

    private OrderSummaryAdapter orderSummaryAdapter; // Assuming you have an adapter for the summary
    private TextView textViewSummaryTableInfo;
    private RecyclerView recyclerViewOrderSummary;
    private Button buttonCashPayment; // New button
    private Button buttonCardPayment; // New button
    private ProgressBar progressBar;

    private String restaurantId;
    private String tableId;
    private int tableNumber;
    private double currentTableTotalPrice;
    public static String EXTRA_INVOICE_PDF_URL = "EXTRA_INVOICE_PDF_URL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_summary);

        textViewSummaryTableInfo = findViewById(R.id.text_view_summary_table_info);
        recyclerViewOrderSummary = findViewById(R.id.recycler_view_order_summary);
        buttonCashPayment = findViewById(R.id.button_cash_payment);
        buttonCardPayment = findViewById(R.id.button_card_payment);
        progressBar = findViewById(R.id.progress_bar_loading);

        // Get data from the Intent
        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_NUMBER) &&
                getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE)) {

            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);

            // Initialize Firestore references
            tableDocRef = db.collection("restaurants").document(restaurantId).collection("tables").document(tableId);
            itemsOrderedRef = tableDocRef.collection("currentOrder");

            setTitle("Order Summary - Table " + tableNumber);
            updateSummaryTableInfoDisplay();

            setUpOrderSummaryRecyclerView();

        } else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
        }

        // Set up click listeners for the new buttons
        buttonCashPayment.setOnClickListener(v -> {
            Log.d(TAG, "Cash button clicked for Table " + tableNumber);

            new AlertDialog.Builder(this)
                    .setTitle("Confirm Cash Payment")
                    .setMessage("Are you sure you want to mark this order as paid in cash?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        showProgressBar();

                        String description = "Cash payment for Table " + tableNumber;

                        // Step 1: Fetch all ordered items from Firestore
                        itemsOrderedRef.get().addOnSuccessListener(querySnapshot -> {
                            List<OrderItem> orderItems = new ArrayList<>();

                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                OrderItem item = doc.toObject(OrderItem.class);
                                if (item != null) {
                                    item.setId(doc.getId()); // Ensure ID is set
                                    orderItems.add(item);
                                }
                            }

                            // Step 2: Pass item list to custom provider
                            CustomConnectionTokenProvider provider = new CustomConnectionTokenProvider();
                            provider.createCashPayment(orderItems, description, new CustomConnectionTokenProvider.CreateCashCallback() {
                                @Override
                                public void onSuccess(String invoiceUrl, String invoicePdfUrl) {
                                    hideProgressBar();

                                    // Optionally open the invoice
                                    Intent qr = new Intent(OrderSummaryActivity.this, InvoiceQRCodeActivity.class);
                                    qr.putExtra(EXTRA_INVOICE_PDF_URL, invoiceUrl);
                                    startActivity(qr);

                                    Toast.makeText(OrderSummaryActivity.this, "Cash payment recorded successfully.", Toast.LENGTH_SHORT).show();
                                    finalizeOrder();
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    hideProgressBar();
                                    Toast.makeText(OrderSummaryActivity.this, "Failed to process cash payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    Log.e(TAG, "Cash payment error", e);
                                }
                            });

                        }).addOnFailureListener(e -> {
                            hideProgressBar();
                            Toast.makeText(OrderSummaryActivity.this, "Failed to fetch order items: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Error fetching order items", e);
                        });

                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        buttonCardPayment.setOnClickListener(v -> {
            showProgressBar();
            Log.d(TAG, "Card button clicked for Table " + tableNumber);

            Intent discoverIntent = new Intent(OrderSummaryActivity.this, DiscoverReadersActivity.class);
            discoverIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, currentTableTotalPrice);
            discoverIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
            discoverIntent.putExtra(EXTRA_TABLE_ID, tableId);
            discoverIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);

            startActivity(discoverIntent);

            hideProgressBar();
        });
    }

    private void updateSummaryTableInfoDisplay() {
        textViewSummaryTableInfo.setText("Table: " + tableNumber + " - Total: €" + String.format("%.2f", currentTableTotalPrice));
    }

    private void setUpOrderSummaryRecyclerView() {
        Query query = itemsOrderedRef.orderBy("name", Query.Direction.ASCENDING); // Order by item name
        FirestoreRecyclerOptions<OrderItem> options = new FirestoreRecyclerOptions.Builder<OrderItem>()
                .setQuery(query, OrderItem.class) // Assuming OrderItem.java is your model class
                .build();

        orderSummaryAdapter = new OrderSummaryAdapter(options); // Initialize your OrderSummaryAdapter
        recyclerViewOrderSummary.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrderSummary.setAdapter(orderSummaryAdapter);

        orderSummaryAdapter.setOnItemActionListener(new OrderSummaryAdapter.OnItemActionListener() {
            @Override
            public void onIncrementClick(OrderItem orderItem) {
                updateOrderItemQuantity(orderItem, 1);
            }

            @Override
            public void onDecrementClick(OrderItem orderItem) {
                updateOrderItemQuantity(orderItem, -1);
            }

            @Override
            public void onRemoveClick(OrderItem orderItem) {
                removeOrderItem(orderItem);
            }
        });

        // Listen for changes in the total price from Firestore
        tableDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Table table = snapshot.toObject(Table.class);
                if (table != null) {
                    currentTableTotalPrice = table.getTotalPrice();
                    updateSummaryTableInfoDisplay();
                }
            } else {
                Log.d(TAG, "Current table data: null");
            }
        });

    }

    private void updateOrderItemQuantity(OrderItem orderItem, int change) {
        DocumentReference orderItemDocRef = itemsOrderedRef.document(orderItem.getId());

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(orderItemDocRef);
            DocumentSnapshot tableSnapshot = transaction.get(tableDocRef);

            int newQuantity = orderItem.getQuantity();
            double itemPrice = orderItem.getPrice();
            double currentTotal = 0.0;

            if (snapshot.exists()) {
                OrderItem existingOrderItem = snapshot.toObject(OrderItem.class);
                if (existingOrderItem != null) {
                    newQuantity = existingOrderItem.getQuantity() + change;
                }
            } else {
                // This shouldn't happen if the item is already in the adapter, but good for safety
                Log.e(TAG, "Order item not found in transaction: " + orderItem.getName());
            }

            if (tableSnapshot.exists()) {
                Table table = tableSnapshot.toObject(Table.class);
                if (table != null) {
                    currentTotal = table.getTotalPrice();
                }
            }

            if (newQuantity <= 0) {
                // If quantity goes to 0 or less, remove the item
                transaction.delete(orderItemDocRef);
                currentTotal -= (orderItem.getQuantity() * itemPrice); // Subtract the full previous total for this item
                Log.d(TAG, "Removing item: " + orderItem.getName() + ", new total: " + currentTotal);
            } else {
                // Update quantity and recalculate total
                OrderItem updatedOrderItem = new OrderItem(
                        orderItem.getId(),
                        orderItem.getName(),
                        orderItem.getPrice(),
                        newQuantity,
                        orderItem.getCategory(),
                        orderItem.getType(),
                        orderItem.getStatus()
                );
                transaction.set(orderItemDocRef, updatedOrderItem);

                currentTotal += (change * itemPrice);
                Log.d(TAG, "Updating quantity for " + orderItem.getName() + " to " + newQuantity + ", new total: " + currentTotal);
            }

            // Update the table's total price
            Map<String, Object> tableUpdates = new HashMap<>();
            tableUpdates.put("totalPrice", currentTotal);
            transaction.update(tableDocRef, tableUpdates);

            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Order item quantity updated successfully.");
            // UI update handled by snapshot listener on tableDocRef and FirestoreRecyclerAdapter
        }).addOnFailureListener(e -> {
            Toast.makeText(OrderSummaryActivity.this, "Error updating item quantity: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Transaction failed for updating item quantity: " + e.getMessage(), e);
        });
    }


    private void removeOrderItem(OrderItem orderItem) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Item")
                .setMessage("Are you sure you want to remove " + orderItem.getName() + " from the order?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    DocumentReference orderItemDocRef = itemsOrderedRef.document(orderItem.getId());

                    db.runTransaction(transaction -> {
                        DocumentSnapshot tableSnapshot = transaction.get(tableDocRef);

                        double currentTotal = 0.0;
                        if (tableSnapshot.exists()) {
                            Table table = tableSnapshot.toObject(Table.class);
                            if (table != null) {
                                currentTotal = table.getTotalPrice();
                            }
                        }

                        // Subtract the full price of the item (quantity * price)
                        currentTotal -= (orderItem.getQuantity() * orderItem.getPrice());

                        // Delete the order item
                        transaction.delete(orderItemDocRef);

                        // Update the table's total price
                        Map<String, Object> tableUpdates = new HashMap<>();
                        tableUpdates.put("totalPrice", currentTotal);
                        transaction.update(tableDocRef, tableUpdates);

                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        Toast.makeText(OrderSummaryActivity.this, orderItem.getName() + " removed from order.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Order item removed successfully.");
                        // UI update handled by snapshot listener on tableDocRef and FirestoreRecyclerAdapter
                    }).addOnFailureListener(e -> {
                        Toast.makeText(OrderSummaryActivity.this, "Error removing item: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Transaction failed for removing item: " + e.getMessage(), e);
                    });
                })
                .setNegativeButton("No", null)
                .show();
    }


    private void finalizeOrder() {
        showProgressBar();
        // Fetch all documents in the 'currentOrder' subcollection
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
                                Toast.makeText(OrderSummaryActivity.this, "Order finalized for Table " + tableNumber + ". Table now Available.", Toast.LENGTH_LONG).show();
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
                                Toast.makeText(OrderSummaryActivity.this, "Error finalizing order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Failed to commit batch operations for order finalization: " + e.getMessage(), e);
                                hideProgressBar();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(OrderSummaryActivity.this, "Error fetching order items to finalize: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to get current order items for finalization: " + e.getMessage(), e);
                    hideProgressBar();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (orderSummaryAdapter != null) {
            orderSummaryAdapter.startListening();
            Log.d(TAG, "Order summary adapter started listening.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (orderSummaryAdapter != null) {
            orderSummaryAdapter.stopListening();
            Log.d(TAG, "Order summary adapter stopped listening.");
        }
    }

    public void showProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            buttonCashPayment.setEnabled(false);
            buttonCardPayment.setEnabled(false);
        }
    }

    public void hideProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
            buttonCashPayment.setEnabled(true);
            buttonCardPayment.setEnabled(true);
        }
    }
}