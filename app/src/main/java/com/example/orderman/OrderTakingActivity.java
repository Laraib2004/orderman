package com.example.orderman;

import static com.example.orderman.LoginActivity.EXTRA_RESTAURANT_ID;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button; // Import Button
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrderTakingActivity extends AppCompatActivity
        implements CategoryFilterAdapter.OnCategoryClickListener,
            MenuItemAdapter.OnItemQuantityChangeListener {

    private static final String TAG = "OrderTakingActivity";

    public static final String EXTRA_TABLE_ID = "extraTableId";
    public static final String EXTRA_TABLE_NUMBER = "extraTableNumber";
    public static final String EXTRA_TABLE_STATUS = "extraTableStatus";
    public static final String EXTRA_TABLE_TOTAL_PRICE = "extraTableTotalPrice";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference menuItemsRef;
    private DocumentReference tableDocRef;
    // We will no longer directly interact with 'itemsOrderedRef' here,
    // as changes are batched and sent on 'Send Order'
    private CollectionReference currentOrderSubcollectionRef; // Renamed for clarity to reflect its purpose

    private MenuItemAdapter menuAdapter;
    private ListenerRegistration tableListenerRegistration;

    private String restaurantId;
    private String tableId;
    private int tableNumber;
    private String tableStatus;
    private double currentTableTotalPrice; // This will track the *actual* total on the table in Firestore
    private double temporaryOrderTotal = 0.0; // This will track the *current temporary total* of unsent items
    private CategoryFilterAdapter categoryAdapter; // New adapter for categories

    private TextView textViewTableInfo;
    private RecyclerView recyclerViewMenuForOrder;
    private RecyclerView recyclerViewCategories; // New RecyclerView for categories
    private Button buttonSendOrder; // Declare the send order button

    // Map to store quantities of items selected by the user *before* sending the order
    private Map<String, PendingOrderItemData> pendingOrderItems = new HashMap<>(); // Key: MenuItemId, Value: Quantity
    private Set<String> currentSelectedCategories = new HashSet<>(); // Tracks selected categories for filtering


    // Private static helper class to hold all necessary data for an OrderItem
    private static class PendingOrderItemData {
        String menuItemId;
        int quantity; // This is the *absolute desired quantity* from the UI for this item
        double price;
        String name;
        String category;
        String type;

        public PendingOrderItemData(String menuItemId, int quantity, double price, String name, String category, String type) {
            this.menuItemId = menuItemId;
            this.quantity = quantity;
            this.price = price;
            this.name = name;
            this.category = category;
            this.type = type;
        }

        // Getters
        public String getMenuItemId() { return menuItemId; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getType() { return type; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_taking);

        textViewTableInfo = findViewById(R.id.text_view_order_table_info);
        recyclerViewMenuForOrder = findViewById(R.id.recycler_view_menu_for_order);
        buttonSendOrder = findViewById(R.id.button_send_order); // Initialize the send order button
        recyclerViewCategories = findViewById(R.id.recycler_view_categories);
        if (recyclerViewCategories == null) {
            Log.e(TAG, "onCreate: RecyclerView with ID recycler_view_categories not found in layout.");
            Toast.makeText(this, "Layout error: Category RecyclerView not found.", Toast.LENGTH_LONG).show();
            return;
        }

        findViewById(R.id.button_view_order_summary).setOnClickListener(v -> {
            // The summary should ideally show confirmed items + pending items
            // For now, it will show the total from Firestore + our pending changes (if we apply them)
            Toast.makeText(this, "Order summary for Table " + tableNumber + " (Current Confirmed Total: €" + String.format("%.2f", currentTableTotalPrice) + ")", Toast.LENGTH_SHORT).show();
            Intent summaryIntent = new Intent(OrderTakingActivity.this, OrderSummaryActivity.class);
            summaryIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
            summaryIntent.putExtra(EXTRA_TABLE_ID, tableId);
            summaryIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);
            // Pass the current *confirmed* total from Firestore for the summary
            summaryIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, currentTableTotalPrice);
            startActivity(summaryIntent);
        });

        // Get categories from string-array
        String[] categoryArray = getResources().getStringArray(R.array.category_item);
        List<String> allCategories = Arrays.asList(categoryArray);

        // Initialize CategoryFilterAdapter
        categoryAdapter = new CategoryFilterAdapter(allCategories, this); // 'this' implements OnCategoryClickListener
        recyclerViewCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerViewCategories.setAdapter(categoryAdapter);

        buttonSendOrder.setOnClickListener(v -> sendOrderToFirestore()); // Set click listener for Send Order button

        // Get data from the Intent
        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_NUMBER) &&
                getIntent().hasExtra(EXTRA_TABLE_STATUS)) {
            if (getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE)) {
                restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
                tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
                tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
                tableStatus = getIntent().getStringExtra(EXTRA_TABLE_STATUS);
                currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);
                temporaryOrderTotal = currentTableTotalPrice; // Initialize temporary total with current confirmed total

                menuItemsRef = db.collection("restaurants").document(restaurantId).collection("menuItems");
                tableDocRef = db.collection("restaurants").document(restaurantId).collection("tables").document(tableId);
                currentOrderSubcollectionRef = tableDocRef.collection("currentOrder"); // Reference to the subcollection

                setTitle("Table " + tableNumber);
                updateTableInfoDisplay();

                Log.d(TAG, "OrderTakingActivity for Table ID: " + tableId + ", Initial Status: " + tableStatus + ", Initial Confirmed Total: " + currentTableTotalPrice);

            } else {
                Toast.makeText(this, "Error: Initial table total price missing.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Required Intent extra EXTRA_TABLE_TOTAL_PRICE missing.");
                finish();
            }

        } else {
            Toast.makeText(this, "Error: Table information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing (Restaurant ID, Table ID, Number, Status).");
            finish();
        }
    }

    private void updateTableInfoDisplay() {
        // Display the temporary total which includes unsent items
        textViewTableInfo.setText("Table: " + tableNumber + " - Status: " + tableStatus + " - Total: €" + String.format("%.2f", temporaryOrderTotal));
    }

    private void setUpMenuRecyclerView() {
        Log.d(TAG, "setUpMenuRecyclerView: Initializing or re-initializing adapter.");
        if (menuItemsRef == null) {
            Log.e(TAG, "setUpMenuRecyclerView: menuItemsRef is null, cannot create query.");
            Toast.makeText(this, "Error: Menu items reference not initialized for RecyclerView.", Toast.LENGTH_SHORT).show();
            return;
        }

        Query query = menuItemsRef.orderBy("name", Query.Direction.ASCENDING);

        // Apply category filtering if categories are selected
        if (!currentSelectedCategories.isEmpty()) {
            // Firestore 'whereIn' clause has a limit of 10 items.
            // Your category_item array has 8 items, so this is fine.
            query = query.whereIn("category", new ArrayList<>(currentSelectedCategories));
            Log.d(TAG, "setUpRecyclerViewAndAdapter: Filtering by categories: " + currentSelectedCategories.toString());
        } else {
            Log.d(TAG, "setUpRecyclerViewAndAdapter: No categories selected, showing all menu items.");
        }

        FirestoreRecyclerOptions<MenuItem> options = new FirestoreRecyclerOptions.Builder<MenuItem>()
                .setQuery(query, MenuItem.class)
                .build();

        if (menuAdapter != null) {
            menuAdapter.stopListening();
            Log.d(TAG, "setUpMenuRecyclerView: Stopped previous adapter before re-creation.");
        }

        menuAdapter = new MenuItemAdapter(options);
        Log.d(TAG, "setUpMenuRecyclerView: MenuItemAdapter initialized.");

        // Set the new quantity change listener
        menuAdapter.setOnItemQuantityChangeListener(this); // 'this' refers to OrderTakingActivity

        Log.d(TAG, "setUpMenuRecyclerView: Adapter and quantity change listeners prepared.");

        recyclerViewMenuForOrder.setAdapter(menuAdapter);
    }

    @Override
    public void onPlusClick(MenuItem menuItem, int position, int currentQuantity) {
        int newQuantity = currentQuantity + 1;
        Log.d(TAG, "onPlusClick: " + menuItem.getName() + " quantity to " + newQuantity);
        updatePendingOrderItem(menuItem, newQuantity);
    }

    @Override
    public void onMinusClick(MenuItem menuItem, int position, int currentQuantity) {
        if (currentQuantity > 0) {
            int newQuantity = currentQuantity - 1;
            Log.d(TAG, "onMinusClick: " + menuItem.getName() + " quantity to " + newQuantity);
            updatePendingOrderItem(menuItem, newQuantity);
        } else {
            Toast.makeText(this, "Quantity cannot be less than 0.", Toast.LENGTH_SHORT).show();
        }
    }


    private void updatePendingOrderItem(MenuItem menuItem, int quantity) {
        int oldQuantity = 0;
        // Get old quantity from our custom object if it exists
        if (pendingOrderItems.containsKey(menuItem.getId())) {
            oldQuantity = pendingOrderItems.get(menuItem.getId()).getQuantity();
        }

        // Update the quantity in the adapter's internal map for UI display
        menuAdapter.updateItemQuantity(menuItem.getId(), quantity);

        // Update the pendingOrderItems map with the full data
        if (quantity > 0) {
            pendingOrderItems.put(menuItem.getId(), new PendingOrderItemData(
                    menuItem.getId(),
                    quantity, // This is the absolute desired quantity for the item
                    menuItem.getPrice(),
                    menuItem.getName(),
                    menuItem.getCategory(),
                    menuItem.getType()
            ));
        } else {
            pendingOrderItems.remove(menuItem.getId()); // Remove if quantity becomes 0
        }

        // Update the temporary order total based on the quantity change
        temporaryOrderTotal += (quantity - oldQuantity) * menuItem.getPrice();
        updateTableInfoDisplay(); // Update UI to reflect temporary total
    }


    // Inside OrderTakingActivity.java

    private void sendOrderToFirestore() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "sendOrderToFirestore: User is NOT authenticated! Writes will fail due to rules.");
            Toast.makeText(this, "You must be logged in to send an order.", Toast.LENGTH_LONG).show();
            return; // Prevent attempting the transaction
        }

        if (pendingOrderItems.isEmpty()) {
            Toast.makeText(this, "No items to send. Please add items first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "sendOrderToFirestore: Attempting to send order with " + pendingOrderItems.size() + " unique items.");

        db.runTransaction(transaction -> {
            // --- ALL READS FIRST ---
            // Read the current state of the table
            DocumentSnapshot tableSnapshot = transaction.get(tableDocRef);
            double currentConfirmedTableTotal = 0.0;
            if (tableSnapshot.exists()) {
                Table table = tableSnapshot.toObject(Table.class);
                if (table != null) {
                    currentConfirmedTableTotal = table.getTotalPrice();
                    Log.d(TAG, "sendOrderToFirestore: Current confirmed table total from DB: " + currentConfirmedTableTotal);
                }
            } else {
                Log.e(TAG, "sendOrderToFirestore: Table document not found for ID: " + tableId + ". Assuming new table with 0 total.");
                // If table doesn't exist, it means this is the very first order for it.
                // We'll proceed with currentConfirmedTableTotal = 0.0 and it will be created implicitly by transaction.update.
                // However, if tableDocRef *must* exist beforehand, you should throw an exception here.
                // throw new FirebaseFirestoreException("Table document not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            // Read all existing OrderItems that are part of the pending order
            // This needs to be done *before* any writes in the loop.
            // We'll collect the existing quantities here.
            Map<String, Integer> existingQuantitiesMap = new HashMap<>();
            for (String menuItemId : pendingOrderItems.keySet()) {
                DocumentReference orderItemDocRef = currentOrderSubcollectionRef.document(menuItemId);
                DocumentSnapshot orderItemSnapshot = transaction.get(orderItemDocRef);
                if (orderItemSnapshot.exists()) {
                    OrderItem existingOrderItem = orderItemSnapshot.toObject(OrderItem.class);
                    if (existingOrderItem != null) {
                        existingQuantitiesMap.put(menuItemId, existingOrderItem.getQuantity());
                    }
                }
            }

            // --- ALL WRITES AFTER ALL READS ---
            // Now, calculate changes and prepare writes based on all collected reads
            for (Map.Entry<String, PendingOrderItemData> entry : pendingOrderItems.entrySet()) {
                String menuItemId = entry.getKey();
                PendingOrderItemData itemData = entry.getValue(); // Get the custom object with all details

                int quantityFromUI = itemData.getQuantity(); // This is the absolute quantity from the UI

                DocumentReference orderItemDocRef = currentOrderSubcollectionRef.document(menuItemId);

                int existingQuantity = existingQuantitiesMap.containsKey(menuItemId) ? existingQuantitiesMap.get(menuItemId) : 0;

                // Calculate the NEW TOTAL quantity that should be in Firestore
                // This logic assumes `quantityFromUI` is the *final desired quantity* from the UI.
                // If `quantityFromUI` is the *delta* (e.g., just +1 or -1), then `newTotalQuantity = existingQuantity + quantityFromUI;`
                // Based on your previous logs and the UI, `quantityFromUI` seems to be the absolute quantity.
                // So, the change in total price is (quantityFromUI - existingQuantity) * itemPrice.
                // And the quantity to write is `quantityFromUI`.

                // Let's stick to the interpretation that pendingOrderItems.getQuantity() is the *absolute total* the user wants.
                int newTotalQuantity = quantityFromUI; // This is the quantity to write to Firestore

                double itemPrice = itemData.getPrice(); // Get price directly from itemData
                double priceChange = (newTotalQuantity - existingQuantity) * itemPrice; // Calculate actual price change for the total

                // Update total price for the table (this is a local variable in the transaction)
                currentConfirmedTableTotal += priceChange;

                if (newTotalQuantity > 0) {
                    // Create or update the OrderItem document using transaction.set()
                    OrderItem updatedOrderItem = new OrderItem(
                            itemData.getMenuItemId(),
                            itemData.getName(),
                            itemData.getPrice(),
                            newTotalQuantity, // Use the new total quantity
                            itemData.getCategory(),
                            itemData.getType(),
                            "Preparing" // Default status when ordered
                    );
                    transaction.set(orderItemDocRef, updatedOrderItem);
                    Log.d(TAG, "Transaction SET for item " + itemData.getName() + " (ID: " + itemData.getMenuItemId() + ") with NEW TOTAL quantity " + newTotalQuantity);
                } else {
                    // If the new total quantity is 0, delete the item from the order
                    transaction.delete(orderItemDocRef);
                    Log.d(TAG, "Transaction DELETE for item " + itemData.getName() + " (ID: " + itemData.getMenuItemId() + ")");
                }
            }

            // Update the table's status and total price using transaction.update()
            Map<String, Object> tableUpdates = new HashMap<>();
            tableUpdates.put("status", "Occupied"); // Table becomes occupied if items are sent
            tableUpdates.put("totalPrice", currentConfirmedTableTotal);
            transaction.update(tableDocRef, tableUpdates);
            Log.d(TAG, "Transaction UPDATE for table total to " + currentConfirmedTableTotal);

            return null; // Return null for successful transaction
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(OrderTakingActivity.this, "Order sent successfully!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Order transaction completed successfully.");

            // Clear pending items as they are now sent and confirmed in Firestore
            pendingOrderItems.clear();

            // Notify adapter to reset displayed quantities to 0 for pending items
            // This is crucial for UI consistency, as these items are no longer "pending"
            java.util.Map<String, Integer> selectedItems = menuAdapter.getSelectedItemsWithQuantities();
            for (java.util.Map.Entry<String, Integer> entry : selectedItems.entrySet()) {
                String itemId = entry.getKey();
                menuAdapter.updateItemQuantity(itemId, 0); // Set displayed quantity back to 0 (for the *pending* part)
            }

            // Update temporary total to reflect the new confirmed total from the transaction
            // The table listener will eventually update currentTableTotalPrice, but this provides immediate UI sync.
            temporaryOrderTotal = currentTableTotalPrice; // Sync temporary with confirmed after send
            updateTableInfoDisplay();

        }).addOnFailureListener(e -> {
            Toast.makeText(OrderTakingActivity.this, "Error sending order: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Order transaction failed: " + e.getMessage(), e);
            // IMPORTANT: Check Logcat for the exact error message from Firebase.
            // This is where "needs to execute all reads before writes" would appear.
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: OrderTakingActivity entered. Starting fresh RecyclerView setup.");

        if (recyclerViewMenuForOrder != null) {
            recyclerViewMenuForOrder.setLayoutManager(new LinearLayoutManager(this));
            setUpMenuRecyclerView();

            if (menuAdapter != null) {
                recyclerViewMenuForOrder.setAdapter(menuAdapter);
                recyclerViewMenuForOrder.post(() -> {
                    if (menuAdapter != null) {
                        menuAdapter.startListening();
                        Log.d(TAG, "onStart: Menu adapter started listening (delayed via post()).");

                        if (tableDocRef != null) {
                            tableListenerRegistration = tableDocRef.addSnapshotListener(this, (snapshot, e) -> {
                                if (e != null) {
                                    Log.w(TAG, "Table snapshot listener failed.", e);
                                    return;
                                }
                                if (snapshot != null && snapshot.exists()) {
                                    Table table = snapshot.toObject(Table.class);
                                    if (table != null) {
                                        currentTableTotalPrice = table.getTotalPrice(); // Update confirmed total
                                        tableStatus = table.getStatus();
                                        // When table data updates, ensure temporary total accounts for pending items
                                        // This is a subtle point: if a listener updates currentTableTotalPrice,
                                        // our temporaryOrderTotal should incorporate it.
                                        // The simplest way is to assume currentTableTotalPrice is the base
                                        // and then add our pending changes.
                                        temporaryOrderTotal = currentTableTotalPrice;
                                        for (Map.Entry<String, PendingOrderItemData> entry : pendingOrderItems.entrySet()) {
                                            String menuItemId = entry.getKey();
                                            int pendingQuantity = entry.getValue().getQuantity();
                                            // Find the MenuItem to get its price
                                            MenuItem pendingMenuItem = null;
                                            for (int i = 0; i < menuAdapter.getItemCount(); i++) {
                                                if (menuAdapter.getItem(i).getId().equals(menuItemId)) {
                                                    pendingMenuItem = menuAdapter.getItem(i);
                                                    break;
                                                }
                                            }
                                            if (pendingMenuItem != null) {
                                                // We need to compare pending quantities with *existing* quantities
                                                // on the table from the DB to truly calculate the difference.
                                                // For simplicity here, we're assuming pendingOrderItems represents
                                                // the *net change* to be applied.
                                                // A more robust solution might fetch current order items from DB here too.
                                                // For now, let's just add the total of *all* pending items.
                                                temporaryOrderTotal += (pendingQuantity * pendingMenuItem.getPrice());
                                            }
                                        }
                                        updateTableInfoDisplay();
                                        Log.d(TAG, "Table info updated via snapshot listener: Confirmed Total=" + String.format("%.2f", currentTableTotalPrice) + ", Temporary Total=" + String.format("%.2f", temporaryOrderTotal) + ", Status=" + tableStatus);
                                    }
                                } else {
                                    Log.d(TAG, "Table document does not exist or was removed.");
                                    Toast.makeText(this, "Table information unavailable.", Toast.LENGTH_LONG).show();
                                    finish();
                                }
                            });
                            Log.d(TAG, "Table document snapshot listener added.");
                        } else {
                            Log.e(TAG, "tableDocRef is null, cannot add snapshot listener in onStart().");
                        }

                    } else {
                        Log.w(TAG, "onStart: Menu adapter is null after post() delay (unexpected).");
                    }
                });
            } else {
                Log.e(TAG, "onStart: Menu adapter is null immediately after setUpMenuRecyclerView(). Cannot proceed.");
                Toast.makeText(this, "Failed to initialize menu list.", Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            Log.e(TAG, "onStart: recyclerViewMenuForOrder is null. Layout not found or initialized correctly.");
            Toast.makeText(this, "Layout error: Menu RecyclerView not found.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: OrderTakingActivity entered.");
        if (menuAdapter != null) {
            menuAdapter.stopListening();
            Log.d(TAG, "onStop: Menu adapter stopped listening.");
        } else {
            Log.w(TAG, "onStop: Menu adapter is null, no need to stop listening.");
        }

        if (tableListenerRegistration != null) {
            tableListenerRegistration.remove();
            Log.d(TAG, "onStop: Table document snapshot listener removed.");
        } else {
            Log.w(TAG, "onStop: Table listener registration is null, no need to remove.");
        }
    }

    // This method is called when a category chip is clicked
    @Override
    public void onCategoryClick(String categoryName, boolean isSelected) {
        if (isSelected) {
            currentSelectedCategories.add(categoryName);
        } else {
            currentSelectedCategories.remove(categoryName);
        }
        Log.d(TAG, "Category clicked: " + categoryName + ", New selected state: " + isSelected + ". Current selected categories: " + currentSelectedCategories.toString());

        // Re-setup the menu RecyclerView (this will create a new adapter with the updated filter)
        setUpMenuRecyclerView();

        // IMPORTANT: Start the NEW adapter listening for data
        if (menuAdapter != null) {
            menuAdapter.startListening();
            Log.d(TAG, "onCategoryClick: Menu adapter restarted listening with new filter.");
        }
    }
}