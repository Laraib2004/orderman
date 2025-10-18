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
    // In your OrderSummaryActivity.java
    // OrderSummaryActivity.java

    private ActivityResultLauncher<Intent> cardPaymentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                hideProgressBar();
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Payment was successful.
                    Intent data = result.getData();
                    if (data != null) {
                        // Retrieve the list of paid items from the Intent
                        ArrayList<OrderItem> paidItemsList = data.getParcelableArrayListExtra(EXTRA_SELECTED_ITEMS);
                        if (paidItemsList != null && !paidItemsList.isEmpty()) {

                            // *** CONVERT the returned OrderItem list into the final QUANTITY map ***
                            Map<String, Integer> itemsToFinalizeQuantities = new HashMap<>();
                            for (OrderItem item : paidItemsList) {
                                // Map: Item ID -> Quantity Paid
                                itemsToFinalizeQuantities.put(item.getId(), item.getQuantity());
                            }

                            // *** CALL THE UPDATED FINALIZATION METHOD ***
                            finalizeSelectedItemsPayment(itemsToFinalizeQuantities);

                        } else {
                            Toast.makeText(this, "Error: No items were returned after payment.", Toast.LENGTH_LONG).show();
                        }
                    }
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
            Map<String, Integer> itemsToPayQuantities = orderSummaryAdapter.getItemsToPay();

            if (itemsToPayQuantities.isEmpty()) {
                Toast.makeText(this, "Please select at least one unit to pay for.", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Confirm Cash Payment")
                    .setMessage("Are you sure you want to mark the selected items as paid in cash?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        showProgressBar();
                        String description = "Cash payment for Table " + tableNumber;

                        // 2. CONVERT the quantity map back into a list of OrderItems for the provider/API
                        List<OrderItem> selectedItemsList = createOrderItemListFromSelection(itemsToPayQuantities);

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

                                                    finalizeSelectedItemsPayment(itemsToPayQuantities);
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
                    Map<String, Integer> itemsToPayQuantities = orderSummaryAdapter.getItemsToPay();
                    if (itemsToPayQuantities.isEmpty()) {
                        Toast.makeText(this, "Please select at least one unit to pay for.", Toast.LENGTH_SHORT).show();
                        hideProgressBar();
                        return;
                    }
                    // *** 2. BUILD LIST AND CALCULATE TOTAL PRICE BASED ON SELECTED QUANTITIES ***
                    double totalPriceOfSelectedItems = 0.0;
                    ArrayList<OrderItem> selectedItemsList = new ArrayList<>();
                    // Iterate through adapter items to find selected ones and build the list
                    for (int i = 0; i < orderSummaryAdapter.getItemCount(); i++) {
                        OrderItem originalItem = orderSummaryAdapter.getItem(i);
                        String itemId = originalItem.getId();

                        if (itemsToPayQuantities.containsKey(itemId)) {
                            int selectedQuantity = itemsToPayQuantities.get(itemId);

                            totalPriceOfSelectedItems += originalItem.getPrice() * selectedQuantity;

                            // Create a new OrderItem object representing *only* the quantity being paid
                            OrderItem paidItem = new OrderItem(
                                    itemId,
                                    originalItem.getName(),
                                    originalItem.getPrice(),
                                    selectedQuantity, // Use the selected quantity
                                    originalItem.getCategory(),
                                    originalItem.getType(),
                                    originalItem.getStatus()
                            );
                            paidItem.setId(originalItem.getId());
                            selectedItemsList.add(paidItem);
                        }
                    }


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
        // OrderSummaryActivity.java

        buttonTransferTables.setOnClickListener(v -> {
            // 1. Get the new quantity-based selection map
            Map<String, Integer> itemsToTransferQuantities = orderSummaryAdapter.getItemsToPay();

            if (itemsToTransferQuantities.isEmpty()) {
                Toast.makeText(this, "Please select at least one unit to transfer.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Convert the quantity map into the Map<String, OrderItem> structure
            //    that showTableSelectionDialog and transferSelectedItems expect.
            Map<String, OrderItem> itemsToTransferMap = new HashMap<>();

            double totalToTransfer = 0;

            // Iterate through the adapter's items to retrieve the full OrderItem details
            for (int i = 0; i < orderSummaryAdapter.getItemCount(); i++) {
                OrderItem originalItem = orderSummaryAdapter.getItem(i);
                String itemId = originalItem.getId();

                if (itemsToTransferQuantities.containsKey(itemId)) {
                    int transferQuantity = itemsToTransferQuantities.get(itemId);

                    // Create a new OrderItem representing *only* the quantity being transferred
                    OrderItem transferItem = new OrderItem(
                            itemId,
                            originalItem.getName(),
                            originalItem.getPrice(),
                            transferQuantity, // Use the selected quantity
                            originalItem.getCategory(),
                            originalItem.getType(),
                            originalItem.getStatus()
                    );
                    transferItem.setId(originalItem.getId());
                    totalToTransfer += (transferItem.getPrice() * transferQuantity);
                    itemsToTransferMap.put(itemId, transferItem);
                }
            }

            // 3. Launch the transfer process with the newly constructed map
            showTableSelectionDialog(itemsToTransferMap);
            currentTableTotalPrice -= totalToTransfer;
        });
    }

    // Create this helper method in OrderSummaryActivity.java
    private List<OrderItem> createOrderItemListFromSelection(Map<String, Integer> itemsToPayQuantities) {
        List<OrderItem> list = new ArrayList<>();
        for (int i = 0; i < orderSummaryAdapter.getItemCount(); i++) {
            OrderItem originalItem = orderSummaryAdapter.getItem(i);
            String itemId = originalItem.getId();

            if (itemsToPayQuantities.containsKey(itemId)) {
                int selectedQuantity = itemsToPayQuantities.get(itemId);

                // Create a new OrderItem object representing *only* the quantity being paid
                OrderItem paidItem = new OrderItem(
                        itemId, originalItem.getName(), originalItem.getPrice(), selectedQuantity,
                        originalItem.getCategory(), originalItem.getType(), originalItem.getStatus()
                );
                list.add(paidItem);
            }
        }
        return list;
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
                        updateSummaryTableInfoDisplay();
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

            // --- PHASE 1: READS ---

            DocumentSnapshot sourceTableSnapshot = transaction.get(tableDocRef);
            DocumentSnapshot destTableSnapshot = transaction.get(destTableDocRef);

            // 1. Read all destination items for merging
            Map<String, DocumentSnapshot> destOrderSnapshots = new HashMap<>();
            for(String itemId : itemsToTransfer.keySet()){
                DocumentReference destItemRef = destOrderRef.document(itemId);
                destOrderSnapshots.put(itemId, transaction.get(destItemRef));
            }

            // 2. Read all SOURCE items for updating/deleting
            Map<String, DocumentSnapshot> sourceOrderSnapshots = new HashMap<>();
            for(String itemId : itemsToTransfer.keySet()){
                DocumentReference sourceItemRef = itemsOrderedRef.document(itemId);
                sourceOrderSnapshots.put(itemId, transaction.get(sourceItemRef));
            }

            double sourceTotal = sourceTableSnapshot.exists() ? sourceTableSnapshot.getDouble("totalPrice") : 0.0;
            double destTotal = destTableSnapshot.exists() ? destTableSnapshot.getDouble("totalPrice") : 0.0;

            double transferPrice = 0.0;

            // --- PHASE 2: CALCULATIONS AND WRITES ---

            for (Map.Entry<String, OrderItem> entry : itemsToTransfer.entrySet()) {
                OrderItem itemToTransfer = entry.getValue();
                String itemId = itemToTransfer.getId();
                int transferQuantity = itemToTransfer.getQuantity(); // e.g., 1x Pommes

                // --- A. SOURCE TABLE UPDATE ---
                DocumentReference sourceItemRef = itemsOrderedRef.document(itemId);
                DocumentSnapshot sourceItemSnapshot = sourceOrderSnapshots.get(itemId);

                if (sourceItemSnapshot != null && sourceItemSnapshot.exists()) {
                    OrderItem existingSourceItem = sourceItemSnapshot.toObject(OrderItem.class);
                    int originalSourceQuantity = existingSourceItem.getQuantity(); // e.g., 7x Pommes
                    int remainingSourceQuantity = originalSourceQuantity - transferQuantity; // 7 - 1 = 6

                    if (remainingSourceQuantity > 0) {
                        // PARTIAL TRANSFER: Update the source item's quantity
                        transaction.update(sourceItemRef, "quantity", remainingSourceQuantity);
                    } else {
                        // FULL TRANSFER: Delete the source item
                        transaction.delete(sourceItemRef);
                    }
                }

                // --- B. DESTINATION TABLE UPDATE ---

                DocumentSnapshot destItemSnapshot = destOrderSnapshots.get(itemId);
                int newQuantityForDest = transferQuantity; // Start with the quantity being transferred

                if (destItemSnapshot != null && destItemSnapshot.exists()) {
                    OrderItem existingDestItem = destItemSnapshot.toObject(OrderItem.class);
                    newQuantityForDest += existingDestItem.getQuantity(); // Add to existing destination quantity
                }

                // Write the new item or updated item to the destination
                OrderItem updatedItem = new OrderItem(
                        itemId,
                        itemToTransfer.getName(),
                        itemToTransfer.getPrice(),
                        newQuantityForDest,
                        itemToTransfer.getCategory(),
                        itemToTransfer.getType(),
                        itemToTransfer.getStatus()
                );

                transaction.set(destOrderRef.document(itemId), updatedItem);

                // --- C. TOTAL PRICE UPDATE ---
                transferPrice += (transferQuantity * itemToTransfer.getPrice());
            }

            sourceTotal -= transferPrice;
            destTotal += transferPrice;

            // --- D. TABLE TOTALS UPDATE ---
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
            // Clear the selection after successful transfer
            orderSummaryAdapter.getItemsToPay().clear();
            orderSummaryAdapter.notifyDataSetChanged();
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

            @Override
            public void onSelectionChange(OrderItem orderItem, int delta) {
                // 1. Get the current quantity selected for payment
                Map<String, Integer> itemsToPay = orderSummaryAdapter.getItemsToPay();
                int currentSelected = itemsToPay.getOrDefault(orderItem.getId(), 0);
                int newSelected = currentSelected + delta;

                // 2. Validate against the total quantity
                if (newSelected >= 0 && newSelected <= orderItem.getQuantity()) {
                    if (newSelected == 0) {
                        itemsToPay.remove(orderItem.getId());
                    } else {
                        itemsToPay.put(orderItem.getId(), newSelected);
                    }
                    // Notify the adapter to refresh the view where the selection count changed
                    orderSummaryAdapter.notifyDataSetChanged();

                    // Optionally, update the header total price here
                    updatePaymentButtonTotalPrice();
                }
            }
        },
            new OrderSummaryAdapter.OnSelectionChangedListener() {
                @Override
                public void onSelectionChanged(int selectedCount, int totalCount) {
                    // This callback updates the main checkbox based on list selection

                }
        });

        recyclerViewOrderSummary.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrderSummary.setAdapter(orderSummaryAdapter);

        // We don't need to call startListening() here as it's handled in onStart()
        // but leaving it here doesn't hurt as long as the adapter is not null when onStart() is called
    }

    // Add a helper method to update the button text
    private void updatePaymentButtonTotalPrice() {
        double totalPriceOfSelectedItems = 0.0;

        // Get the map containing item IDs and the quantity selected for payment
        Map<String, Integer> itemsToPay = orderSummaryAdapter.getItemsToPay();

        // Iterate through the adapter's list by index to get the OrderItem model object directly
        for (int i = 0; i < orderSummaryAdapter.getItemCount(); i++) {

            // Use the adapter's built-in method to get the OrderItem model at the current position
            OrderItem item = orderSummaryAdapter.getItem(i);

            // Get the unique ID for the lookup
            String itemId = item.getId();

            // Check if this item is one the user selected for payment
            if (itemsToPay.containsKey(itemId)) {
                int selectedQuantity = itemsToPay.get(itemId);

                // Calculate the price for the selected quantity
                totalPriceOfSelectedItems += item.getPrice() * selectedQuantity;
            }
        }

        String priceText = String.format("€%.2f", totalPriceOfSelectedItems);
        buttonCashPayment.setText("CASH (" + priceText + ")");
        buttonCardPayment.setText("CARD (" + priceText + ")");
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
            currentTableTotalPrice = currentTotal;
            transaction.update(tableDocRef, tableUpdates);
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Order item quantity updated successfully.");
            updateSummaryTableInfoDisplay();
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
                        currentTableTotalPrice = currentTotal;
                        transaction.update(tableDocRef, tableUpdates);
                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        Toast.makeText(OrderSummaryActivity.this, orderItem.getName() + " removed from order.", Toast.LENGTH_SHORT).show();
                        updateSummaryTableInfoDisplay();
                        Log.d(TAG, "Order item removed successfully.");
                    }).addOnFailureListener(e -> {
                        Toast.makeText(OrderSummaryActivity.this, "Error removing item: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Transaction failed for removing item: " + e.getMessage(), e);
                    });
                })
                .setNegativeButton("No", null)
                .show();
    }

    // OrderSummaryActivity.java

    // RENAME and UPDATE this method to handle QUANTITY map
    private void finalizeSelectedItemsPayment(Map<String, Integer> itemsToPayQuantities) {
        showProgressBar();

        // Use transaction for safe concurrent quantity update
        db.runTransaction(transaction -> {

            // --- PHASE 1: READS AND CALCULATION ---

            // 1. Read the Table document (First Read)
            DocumentSnapshot tableSnapshot = transaction.get(tableDocRef);
            double currentTotal = tableSnapshot.exists() ? tableSnapshot.getDouble("totalPrice") : 0.0;
            double paidTotal = 0.0;

            // 2. Storage for new data (must be calculated before writes)
            // Store the final action and new quantity for each item
            Map<String, Object> itemUpdates = new HashMap<>(); // Key: itemId, Value: Integer (remainingQty) or null (for delete)

            // 3. Loop through all selected items to READ and calculate updates
            for (Map.Entry<String, Integer> entry : itemsToPayQuantities.entrySet()) {
                String itemId = entry.getKey();
                int selectedQuantity = entry.getValue();

                DocumentReference itemDocRef = itemsOrderedRef.document(itemId);
                // !! CRITICAL: This is the second read. All item reads must happen now.
                DocumentSnapshot itemSnapshot = transaction.get(itemDocRef);

                if (itemSnapshot.exists()) {
                    OrderItem item = itemSnapshot.toObject(OrderItem.class);

                    if (item != null) {
                        paidTotal += item.getPrice() * selectedQuantity;

                        int originalQuantity = item.getQuantity();
                        int remainingQuantity = originalQuantity - selectedQuantity;

                        if (remainingQuantity > 0) {
                            // Store the new quantity for an update later
                            itemUpdates.put(itemId, remainingQuantity);
                        } else {
                            // Store null to indicate deletion later
                            itemUpdates.put(itemId, null);
                        }
                    }
                }
            }

            // --- PHASE 2: WRITES ---

            // 4. Loop through the calculated updates and apply the writes
            for (Map.Entry<String, Object> entry : itemUpdates.entrySet()) {
                String itemId = entry.getKey();
                Object remainingQuantityOrNull = entry.getValue();

                DocumentReference itemDocRef = itemsOrderedRef.document(itemId);

                if (remainingQuantityOrNull != null) {
                    // PARTIAL PAYMENT: Update the item's quantity
                    int remainingQuantity = (Integer) remainingQuantityOrNull;
                    transaction.update(itemDocRef, "quantity", remainingQuantity);
                } else {
                    // FULL PAYMENT: Delete the item from the order
                    transaction.delete(itemDocRef);
                }
            }

            // 5. Update Table Total Price (Final Write)
            double newTotal = currentTotal - paidTotal;
            Map<String, Object> tableUpdates = new HashMap<>();
            tableUpdates.put("totalPrice", newTotal);
            currentTableTotalPrice = newTotal;

            if (newTotal <= 0.0) {
                tableUpdates.put("status", "Available");
            }
            transaction.update(tableDocRef, tableUpdates);

            // Return the total amount paid
            return paidTotal;

        }).addOnSuccessListener(totalPaid -> {
            // ... (Success logic remains the same)
            hideProgressBar();
            orderSummaryAdapter.getItemsToPay().clear();
            orderSummaryAdapter.notifyDataSetChanged();

            Toast.makeText(OrderSummaryActivity.this,
                    String.format("Payment of €%.2f finalized.", (Double) totalPaid),
                    Toast.LENGTH_LONG).show();

            updateSummaryTableInfoDisplay();
            if (currentTableTotalPrice <= 0.0) {
                finish();
            }
        }).addOnFailureListener(e -> {
            // ... (Failure logic remains the same)
            hideProgressBar();
            Log.e(TAG, "Finalize payment transaction failed: ", e);
            Toast.makeText(OrderSummaryActivity.this, "Payment processing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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