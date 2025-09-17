package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_NUMBER;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_TOTAL_PRICE;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderSummaryActivity extends AppCompatActivity {

    private static final String TAG = "OrderSummaryActivity";
    public static final String EXTRA_SELECTED_ITEMS = "EXTRA_SELECTED_ITEMS";
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
    private ImageButton buttonTransferTables;
    private ProgressBar progressBar;

    private String restaurantId;
    private String tableId;
    private int tableNumber;
    private double currentTableTotalPrice;
    private NfcAdapter nfcAdapter;
    public static String EXTRA_INVOICE_PDF_URL = "EXTRA_INVOICE_PDF_URL";

    // Your ActivityResultLauncher should be updated to use the finalizeSelectedItemsPayment method.
// You need to get the selected items from the adapter, but the adapter state might be gone
// if the activity was recreated. A safer way is to pass the list of selected items
// to the next activity and get them back.
// Since that's more complex, for now we will just re-fetch the items to be sure.
    private ActivityResultLauncher<Intent> cardPaymentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                hideProgressBar();
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Payment was successful.
                    // Get the items that were selected for payment before launching the activity.
                    Map<String, OrderItem> paidItems = orderSummaryAdapter.getSelectedItems();
                } else {
                    // Payment was cancelled or failed.
                    Toast.makeText(this, "Card payment was cancelled or failed.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_summary);

        textViewSummaryTableInfo = findViewById(R.id.text_view_summary_table_info);
        recyclerViewOrderSummary = findViewById(R.id.recycler_view_order_summary);
        buttonCashPayment = findViewById(R.id.button_cash_payment);
        buttonCardPayment = findViewById(R.id.button_card_payment);
        buttonTransferTables = findViewById(R.id.button_transfer_tables);
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

        } else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
        }

        buttonCashPayment.setOnClickListener(v -> {
            Log.d(TAG, "Cash button clicked for Table " + tableNumber);
            Map<String, OrderItem> selectedItems = orderSummaryAdapter.getSelectedItems();

            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "Please select at least one item to pay for.", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Confirm Cash Payment")
                    .setMessage("Are you sure you want to mark the selected items as paid in cash?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        showProgressBar();
                        String description = "Cash payment for Table " + tableNumber;

                        List<OrderItem> selectedItemsList = new ArrayList<>(selectedItems.values());

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

                                    CustomConnectionTokenProvider provider = new CustomConnectionTokenProvider();
                                    provider.createCashPayment(
                                            address, city, country, name, province, recipientCode, vatNumber,
                                            selectedItemsList, description, new CustomConnectionTokenProvider.CreateCashCallback() {
                                                @Override
                                                public void onSuccess(String invoiceUrl, String invoicePdfUrl) {
                                                    hideProgressBar();
                                                    Intent qr = new Intent(OrderSummaryActivity.this, InvoiceQRCodeActivity.class);
                                                    qr.putExtra(EXTRA_INVOICE_PDF_URL, invoiceUrl);
                                                    addReceiptToHistory(invoiceUrl, tableId, restaurantId);
                                                    startActivity(qr);
                                                    Toast.makeText(OrderSummaryActivity.this, "Cash payment recorded successfully.", Toast.LENGTH_SHORT).show();

                                                    finalizeSelectedItemsPayment(selectedItems);
                                                }

                                                @Override
                                                public void onFailure(Exception e) {
                                                    hideProgressBar();
                                                    Toast.makeText(OrderSummaryActivity.this, "Failed to process cash payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                    Log.e(TAG, "Cash payment error", e);
                                                }
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
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) {
                // Device does not support NFC
                showAlertDialog(
                        "NFC Not Supported",
                        "This device does not support NFC. The app may not work correctly.",
                        false
                );
                hideProgressBar();
            }
            else {
                if (!nfcAdapter.isEnabled()) {
                    // NFC supported but disabled
                    new AlertDialog.Builder(this)
                            .setTitle("Enable NFC")
                            .setMessage("This app requires NFC to work. Do you want to enable it now?")
                            .setCancelable(false)
                            .setPositiveButton("Yes", (dialog, which) -> {
                                // Open NFC settings
                                Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
                                startActivity(intent);
                            })
                            .setNegativeButton("No", (dialog, which) -> {
                                dialog.dismiss();
                            })
                            .show();

                    hideProgressBar();
                }
                else  {
                    Log.d(TAG, "Card button clicked for Table " + tableNumber);
                    Intent discoverIntent = new Intent(OrderSummaryActivity.this, DiscoverReadersActivity.class);
                    // You'll want to pass the total price of all selected items, not the whole table
                    Map<String, OrderItem> selectedItems = orderSummaryAdapter.getSelectedItems();
                    if (selectedItems.isEmpty()) {
                        Toast.makeText(this, "Please select at least one item to pay for.", Toast.LENGTH_SHORT).show();
                        hideProgressBar();
                        return;
                    }
                    double totalPriceOfSelectedItems = 0.0;
                    for (OrderItem item : selectedItems.values()) {
                        totalPriceOfSelectedItems += item.getPrice() * item.getQuantity();
                    }

                    ArrayList<OrderItem> selectedItemsList = new ArrayList<>(selectedItems.values());

                    discoverIntent.putParcelableArrayListExtra(EXTRA_SELECTED_ITEMS, selectedItemsList);
                    discoverIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, totalPriceOfSelectedItems);
                    discoverIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
                    discoverIntent.putExtra(EXTRA_TABLE_ID, tableId);
                    discoverIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);

                    // Now, launch the activity using the launcher
                    cardPaymentLauncher.launch(discoverIntent);
                }

            }

        });
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

    private void transferSelectedItems(Map<String, OrderItem> itemsToTransfer, Table destinationTable) {
        showProgressBar();
        Log.d(TAG, "Attempting to transfer " + itemsToTransfer.size() + " items from Table " + tableNumber + " to Table " + destinationTable.getNumber());

        DocumentReference destTableDocRef = tablesRef.document(destinationTable.getId());
        CollectionReference destOrderRef = destTableDocRef.collection("currentOrder");

        db.runTransaction(transaction -> {
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

            for (Map.Entry<String, OrderItem> entry : itemsToTransfer.entrySet()) {
                OrderItem itemToTransfer = entry.getValue();

                DocumentReference sourceItemRef = itemsOrderedRef.document(itemToTransfer.getId());
                transaction.delete(sourceItemRef);

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
                        itemToTransfer.getStatus()
                );

                transaction.set(destOrderRef.document(itemToTransfer.getId()), updatedItem);

                transferPrice += (itemToTransfer.getQuantity() * itemToTransfer.getPrice());
            }

            sourceTotal -= transferPrice;
            destTotal += transferPrice;

            transaction.update(tableDocRef, "totalPrice", sourceTotal);
            transaction.update(destTableDocRef, "totalPrice", destTotal);
            transaction.update(destTableDocRef, "status", "Occupied");

            if (sourceTotal <= 0.0) {
                transaction.update(tableDocRef, "status", "Available");
            }

            return null;
        }).addOnSuccessListener(aVoid -> {
            hideProgressBar();
            Toast.makeText(this, "Selected items transferred to Table " + destinationTable.getNumber() + "!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Transfer transaction successful.");
           // orderSummaryAdapter.clearSelectedItems();
        }).addOnFailureListener(e -> {
            hideProgressBar();
            Toast.makeText(this, "Failed to transfer items: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Transfer transaction failed: ", e);
        });
    }

    private void updateSummaryTableInfoDisplay() {
        textViewSummaryTableInfo.setText("Table: " + tableNumber + " - Total: €" + String.format("%.2f", currentTableTotalPrice));
    }

    // In OrderSummaryActivity.java

    private void setUpOrderSummaryRecyclerView() {
        // If an adapter already exists, stop it before creating a new one
        if (orderSummaryAdapter != null) {
            orderSummaryAdapter.stopListening();
        }

        Query query = itemsOrderedRef.orderBy("name", Query.Direction.ASCENDING);
        FirestoreRecyclerOptions<OrderItem> options = new FirestoreRecyclerOptions.Builder<OrderItem>()
                .setQuery(query, OrderItem.class)
                .build();

        // Create the new adapter instance with the listener
        orderSummaryAdapter = new OrderSummaryAdapter(options, new OrderSummaryAdapter.OnItemActionListener() {
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

        recyclerViewOrderSummary.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrderSummary.setAdapter(orderSummaryAdapter);

        // We don't need to call startListening() here as it's handled in onStart()
        // but leaving it here doesn't hurt as long as the adapter is not null when onStart() is called
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

    private void finalizeSelectedItemsPayment(Map<String, OrderItem> paidItems) {
        showProgressBar();

        double paidTotal = 0.0;
        for (Map.Entry<String, OrderItem> entry : paidItems.entrySet()) {
            paidTotal += entry.getValue().getQuantity() * entry.getValue().getPrice();
        }

        double finalPaidTotal = paidTotal;

        tableDocRef.get().addOnSuccessListener(tableSnapshot -> {
            if (tableSnapshot.exists()) {
                double currentTotal = tableSnapshot.getDouble("totalPrice");
                double newTotal = currentTotal - finalPaidTotal;

                WriteBatch batch = db.batch();

                for (Map.Entry<String, OrderItem> entry : paidItems.entrySet()) {
                    DocumentReference itemDocRef = itemsOrderedRef.document(entry.getKey());
                    batch.delete(itemDocRef);
                }

                Map<String, Object> tableUpdates = new HashMap<>();
                tableUpdates.put("totalPrice", newTotal);
                if (newTotal <= 0.0) {
                    tableUpdates.put("status", "Available");
                }
                batch.update(tableDocRef, tableUpdates);

                batch.commit()
                        .addOnSuccessListener(aVoid -> {
                            hideProgressBar();
                            Toast.makeText(OrderSummaryActivity.this, "Selected items successfully paid for!", Toast.LENGTH_LONG).show();
                            Log.d(TAG, "Partial payment batch committed successfully.");
                            // REMOVE THIS LINE: orderSummaryAdapter.clearSelectedItems();

                            if (newTotal <= 0.0) {
                                finish();
                            }
                        })
                        .addOnFailureListener(e -> {
                            hideProgressBar();
                            Toast.makeText(OrderSummaryActivity.this, "Error finalizing payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Finalize selected items payment batch failed: ", e);
                        });
            } else {
                hideProgressBar();
                Log.e(TAG, "Table document not found during payment finalization.");
                Toast.makeText(OrderSummaryActivity.this, "Error: Table data missing.", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> {
            hideProgressBar();
            Log.e(TAG, "Failed to get table document for payment: " + e.getMessage());
            Toast.makeText(OrderSummaryActivity.this, "Error finalizing payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
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
        setUpOrderSummaryRecyclerView();
        if (orderSummaryAdapter != null) {
            orderSummaryAdapter.startListening();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (orderSummaryAdapter != null) {
            orderSummaryAdapter.stopListening();
        }
    }

    private void showAlertDialog(String title, String message, boolean openSettings) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    if (openSettings) {
                        // Open NFC settings
                        Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
                        startActivity(intent);
                    }
                    dialog.dismiss();
                });

        if (openSettings) {
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        }

        AlertDialog alert = builder.create();
        alert.show();
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