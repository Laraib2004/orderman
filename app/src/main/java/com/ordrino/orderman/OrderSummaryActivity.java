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
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderSummaryActivity extends AppCompatActivity {

    private static final String TAG = "OrderSummaryActivity";
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference itemsOrderedRef;
    private DocumentReference tableDocRef;
    private DocumentReference restaurantDocRef;
    private CollectionReference tablesRef;

    private OrderSummaryAdapter orderSummaryAdapter;
    private TextView textViewSummaryTableInfo;
    private RecyclerView recyclerViewOrderSummary;
    private Button buttonCashPayment;
    private Button buttonCardPayment;
    private Button buttonTransferTables; // New button
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
        buttonTransferTables = findViewById(R.id.button_transfer_tables); // Initialize the new button
        progressBar = findViewById(R.id.progress_bar_loading);

        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_NUMBER) &&
                getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE)) {

            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);

            tablesRef = db.collection("restaurants").document(restaurantId).collection("tables");
            tableDocRef = tablesRef.document(tableId);
            itemsOrderedRef = tableDocRef.collection("currentOrder");
            restaurantDocRef = db.collection("restaurants").document(restaurantId);

            setTitle("Order Summary - Table " + tableNumber);
            updateSummaryTableInfoDisplay();

            setUpOrderSummaryRecyclerView();

        } else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
        }

        buttonCashPayment.setOnClickListener(v -> {
            Log.d(TAG, "Cash button clicked for Table " + tableNumber);
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Cash Payment")
                    .setMessage("Are you sure you want to mark this order as paid in cash?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        showProgressBar();
                        String description = "Cash payment for Table " + tableNumber;
                        restaurantDocRef.get().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if(document.exists()) {
                                    String address = document.getString("address");
                                    String city = document.getString("city");
                                    String country = document.getString("country");
                                    String name = document.getString("name");
                                    String province = document.getString("province");
                                    String recipientCode = document.getString("recipient_code");
                                    String vatNumber = document.getString("vat_number");
                                    GeoPoint location = document.getGeoPoint("location");

                                    itemsOrderedRef.get().addOnSuccessListener(querySnapshot -> {
                                        List<OrderItem> orderItems = new ArrayList<>();
                                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                            OrderItem item = doc.toObject(OrderItem.class);
                                            if (item != null) {
                                                item.setId(doc.getId());
                                                orderItems.add(item);
                                            }
                                        }

                                        CustomConnectionTokenProvider provider = new CustomConnectionTokenProvider();
                                        provider.createCashPayment(
                                                address, city, country, name, province, recipientCode, vatNumber,
                                                orderItems, description, new CustomConnectionTokenProvider.CreateCashCallback() {
                                                    @Override
                                                    public void onSuccess(String invoiceUrl, String invoicePdfUrl) {
                                                        hideProgressBar();
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
                                } else {
                                    Log.d(TAG, "No such document for restaurantId: " + restaurantId);
                                }
                            } else {
                                Log.e(TAG, "Failed to get restaurant document: ", task.getException());
                            }
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
            finish();
        });

        // Set up the click listener for the new Transfer button
        buttonTransferTables.setOnClickListener(v -> {
            Map<String, OrderItem> selectedItems = orderSummaryAdapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "Please select at least one item to transfer.", Toast.LENGTH_SHORT).show();
                return;
            }
            showTableSelectionDialog(selectedItems);
        });
    }

    private void showTableSelectionDialog(Map<String, OrderItem> itemsToTransfer) {
        tablesRef.orderBy("number").get().addOnSuccessListener(querySnapshot -> {
            List<Table> allTables = new ArrayList<>();
            int selectedTableIndex = -1;

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Table table = doc.toObject(Table.class);
                if (table != null) {
                    table.setId(doc.getId());
                    allTables.add(table);
                }
            }

            // Exclude the current table from the selection list
            List<Table> destinationTables = new ArrayList<>();
            for (Table table : allTables) {
                if (!table.getId().equals(tableId)) {
                    destinationTables.add(table);
                }
            }

            if (destinationTables.isEmpty()) {
                Toast.makeText(this, "No other tables available for transfer.", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] tableNumbers = new String[destinationTables.size()];
            for (int i = 0; i < destinationTables.size(); i++) {
                tableNumbers[i] = "Table " + destinationTables.get(i).getNumber();
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select Destination Table")
                    .setSingleChoiceItems(tableNumbers, -1, (dialog, which) -> {
                        Table selectedTable = destinationTables.get(which);
                        dialog.dismiss();
                        transferSelectedItems(itemsToTransfer, selectedTable);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to fetch tables for selection: " + e.getMessage(), e);
            Toast.makeText(this, "Error fetching table list.", Toast.LENGTH_SHORT).show();
        });
    }

    private void transferSelectedItems(Map<String, OrderItem> itemsToTransfer, Table destinationTable) {
        showProgressBar();
        Log.d(TAG, "Attempting to transfer " + itemsToTransfer.size() + " items from Table " + tableNumber + " to Table " + destinationTable.getNumber());

        DocumentReference destTableDocRef = tablesRef.document(destinationTable.getId());
        CollectionReference destOrderRef = destTableDocRef.collection("currentOrder");

        db.runTransaction(transaction -> {
            // Read both table documents and the destination order items first
            DocumentSnapshot sourceTableSnapshot = transaction.get(tableDocRef);
            DocumentSnapshot destTableSnapshot = transaction.get(destTableDocRef);

            Map<String, DocumentSnapshot> destOrderSnapshots = new HashMap<>();
            for(String itemId : itemsToTransfer.keySet()){
                DocumentReference destItemRef = destOrderRef.document(itemId);
                destOrderSnapshots.put(itemId, transaction.get(destItemRef));
            }

            double sourceTotal = sourceTableSnapshot.exists() ? sourceTableSnapshot.getDouble("totalPrice") : 0.0;
            double destTotal = destTableSnapshot.exists() ? destTableSnapshot.getDouble("totalPrice") : 0.0;

            double transferPrice = 0.0;

            // Perform all writes
            for (Map.Entry<String, OrderItem> entry : itemsToTransfer.entrySet()) {
                OrderItem itemToTransfer = entry.getValue();

                // 1. Delete item from source table's order
                DocumentReference sourceItemRef = itemsOrderedRef.document(itemToTransfer.getId());
                transaction.delete(sourceItemRef);

                // 2. Add or update item in destination table's order
                DocumentSnapshot destItemSnapshot = destOrderSnapshots.get(itemToTransfer.getId());
                int newQuantityForDest = itemToTransfer.getQuantity();

                if (destItemSnapshot != null && destItemSnapshot.exists()) {
                    OrderItem existingDestItem = destItemSnapshot.toObject(OrderItem.class);
                    newQuantityForDest += existingDestItem.getQuantity();

                }

                OrderItem updatedItem = new OrderItem(
                        itemToTransfer.getId(),
                        itemToTransfer.getName(),
                        itemToTransfer.getPrice(),
                        newQuantityForDest,
                        itemToTransfer.getCategory(),
                        itemToTransfer.getType(),
                        "Preparing" // Status can be set to preparing or kept as is
                );

                transaction.set(destOrderRef.document(itemToTransfer.getId()), updatedItem);

                // 3. Update transfer total
                transferPrice += (itemToTransfer.getQuantity() * itemToTransfer.getPrice());
            }

            // 4. Update total prices on both tables
            sourceTotal -= transferPrice;
            destTotal += transferPrice;

            transaction.update(tableDocRef, "totalPrice", sourceTotal);
            transaction.update(destTableDocRef, "totalPrice", destTotal);
            transaction.update(destTableDocRef, "status", "Occupied"); // Set destination table as occupied

            if (sourceTotal <= 0.0) {
                transaction.update(tableDocRef, "status", "Available"); // Free up source table if total is zero
            }

            return null;
        }).addOnSuccessListener(aVoid -> {
            hideProgressBar();
            Toast.makeText(this, "Selected items transferred to Table " + destinationTable.getNumber() + "!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Transfer transaction successful.");
            orderSummaryAdapter.clearSelectedItems();
        }).addOnFailureListener(e -> {
            hideProgressBar();
            Toast.makeText(this, "Failed to transfer items: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Transfer transaction failed: ", e);
        });
    }

    private void updateSummaryTableInfoDisplay() {
        textViewSummaryTableInfo.setText("Table: " + tableNumber + " - Total: €" + String.format("%.2f", currentTableTotalPrice));
    }

    private void setUpOrderSummaryRecyclerView() {
        Query query = itemsOrderedRef.orderBy("name", Query.Direction.ASCENDING);
        FirestoreRecyclerOptions<OrderItem> options = new FirestoreRecyclerOptions.Builder<OrderItem>()
                .setQuery(query, OrderItem.class)
                .build();

        orderSummaryAdapter = new OrderSummaryAdapter(options);
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
                Log.e(TAG, "Order item not found in transaction: " + orderItem.getName());
            }
            if (tableSnapshot.exists()) {
                Table table = tableSnapshot.toObject(Table.class);
                if (table != null) {
                    currentTotal = table.getTotalPrice();
                }
            }
            if (newQuantity <= 0) {
                transaction.delete(orderItemDocRef);
                currentTotal -= (orderItem.getQuantity() * itemPrice);
                Log.d(TAG, "Removing item: " + orderItem.getName() + ", new total: " + currentTotal);
            } else {
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
            Map<String, Object> tableUpdates = new HashMap<>();
            tableUpdates.put("totalPrice", currentTotal);
            transaction.update(tableDocRef, tableUpdates);
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Order item quantity updated successfully.");
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
                        currentTotal -= (orderItem.getQuantity() * orderItem.getPrice());
                        transaction.delete(orderItemDocRef);
                        Map<String, Object> tableUpdates = new HashMap<>();
                        tableUpdates.put("totalPrice", currentTotal);
                        transaction.update(tableDocRef, tableUpdates);
                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        Toast.makeText(OrderSummaryActivity.this, orderItem.getName() + " removed from order.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Order item removed successfully.");
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
        itemsOrderedRef.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        batch.delete(doc.getReference());
                        Log.d(TAG, "Adding delete operation for order item: " + doc.getId());
                    }
                    Map<String, Object> tableUpdates = new HashMap<>();
                    tableUpdates.put("status", "Available");
                    tableUpdates.put("totalPrice", 0.0);
                    batch.update(tableDocRef, tableUpdates);
                    Log.d(TAG, "Adding update operation for table " + tableId + ": status=Available, totalPrice=0.0");
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(OrderSummaryActivity.this, "Order finalized for Table " + tableNumber + ". Table now Available.", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Batch commit successful. Order cleared and table status updated.");
                                finish();
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
            if(buttonTransferTables != null) {
                buttonTransferTables.setEnabled(false);
            }
        }
    }

    public void hideProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
            buttonCashPayment.setEnabled(true);
            buttonCardPayment.setEnabled(true);
            if(buttonTransferTables != null) {
                buttonTransferTables.setEnabled(true);
            }
        }
    }
}