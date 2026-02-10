package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Date;
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

    // Firestore
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference menuItemsRef;
    private DocumentReference tableDocRef;
    private CollectionReference currentOrderSubcollectionRef;
    private ListenerRegistration tableListenerRegistration;

    // UI Components
    private TextView textViewTableInfo;
    private RecyclerView recyclerViewMenuForOrder;
    private RecyclerView recyclerViewCategories;
    private Button buttonSendOrder;
    private Button buttonSummaryOrder;
    private ProgressBar progressBarLoading;
    private ImageButton buttonViewHistory;

    // Adapters
    private MenuItemAdapter menuAdapter;
    private CategoryFilterAdapter categoryAdapter;

    // Data State
    private String restaurantId;
    private String tableId;
    private int tableNumber;
    private String tableStatus;
    private double currentTableTotalPrice;
    private double temporaryOrderTotal = 0.0;
    private Map<String, PendingOrderItemData> pendingOrderItems = new HashMap<>();
    private Set<String> currentSelectedCategories = new HashSet<>();

    // Internal Data Class
    private static class PendingOrderItemData {
        String menuItemId;
        int quantity;
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

        // 1. Initialize Views
        textViewTableInfo = findViewById(R.id.text_view_order_table_info);
        recyclerViewMenuForOrder = findViewById(R.id.recycler_view_menu_for_order);
        buttonSendOrder = findViewById(R.id.button_send_order);
        buttonSummaryOrder = findViewById(R.id.button_view_order_summary);
        recyclerViewCategories = findViewById(R.id.recycler_view_categories);
        progressBarLoading = findViewById(R.id.progress_bar_loading);
        buttonViewHistory = findViewById(R.id.button_view_history);

        // 2. Setup Recycler Views Layout Managers
        recyclerViewMenuForOrder.setLayoutManager(new WrapContentLinearLayoutManager(this));
        recyclerViewCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 3. Setup Categories
        String[] categoryArray = getResources().getStringArray(R.array.category_item);
        List<String> allCategories = Arrays.asList(categoryArray);
        categoryAdapter = new CategoryFilterAdapter(allCategories, this);
        recyclerViewCategories.setAdapter(categoryAdapter);

        // 4. Retrieve Intent Data & Initialize Firestore
        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_NUMBER) &&
                getIntent().hasExtra(EXTRA_TABLE_STATUS) &&
                getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE)) {

            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
            tableStatus = getIntent().getStringExtra(EXTRA_TABLE_STATUS);
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);
            temporaryOrderTotal = currentTableTotalPrice;

            // Initialize References
            menuItemsRef = db.collection("restaurants").document(restaurantId).collection("menuItems");
            tableDocRef = db.collection("restaurants").document(restaurantId).collection("tables").document(tableId);
            currentOrderSubcollectionRef = tableDocRef.collection("currentOrder");

            setTitle("Table " + tableNumber);
            updateTableInfoDisplay();

            // 5. Initialize Menu Adapter (Fix: Done once here)
            setUpMenuRecyclerView();

        } else {
            Toast.makeText(this, "Error: Missing table information.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 6. Setup Buttons
        buttonSendOrder.setOnClickListener(v -> sendOrderToFirestore());

        buttonSummaryOrder.setOnClickListener(v -> {
            if (currentTableTotalPrice > 0) {
                Intent summaryIntent = new Intent(OrderTakingActivity.this, OrderSummaryActivity.class);
                summaryIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
                summaryIntent.putExtra(EXTRA_TABLE_ID, tableId);
                summaryIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);
                summaryIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, currentTableTotalPrice);
                startActivity(summaryIntent);
            } else {
                Toast.makeText(this, "No confirmed orders yet!", Toast.LENGTH_SHORT).show();
            }
        });

        buttonViewHistory.setOnClickListener(v -> {
            Intent historyIntent = new Intent(OrderTakingActivity.this, HistoryReceiptActivity.class);
            historyIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
            historyIntent.putExtra(EXTRA_TABLE_ID, tableId);
            startActivity(historyIntent);
        });
    }

    /**
     * FIX: Configures the adapter. If it exists, updates options. If not, creates it.
     */
    private void setUpMenuRecyclerView() {
        if (menuItemsRef == null) return;

        // Base Query
        Query query = menuItemsRef
                .whereEqualTo("available", true)
                .orderBy("name", Query.Direction.ASCENDING);

        // Apply Category Filter
        if (!currentSelectedCategories.isEmpty()) {
            query = query.whereIn("category", new ArrayList<>(currentSelectedCategories));
            Log.d(TAG, "Filtering by: " + currentSelectedCategories.toString());
        }

        FirestoreRecyclerOptions<MenuItem> options = new FirestoreRecyclerOptions.Builder<MenuItem>()
                .setQuery(query, MenuItem.class)
                .build();

        if (menuAdapter == null) {
            // First time setup
            menuAdapter = new MenuItemAdapter(options);
            menuAdapter.setOnItemQuantityChangeListener(this);
            recyclerViewMenuForOrder.setAdapter(menuAdapter);
            recyclerViewMenuForOrder.setItemAnimator(null);
        } else {
            // Hot swap options for smooth filtering
            menuAdapter.updateOptions(options);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Listening for updates");

        // FIX: Start listening immediately
        if (menuAdapter != null) {
            menuAdapter.startListening();
        }

        // Listen for Table Updates (Total Price changes from other waiters)
        if (tableDocRef != null) {
            tableListenerRegistration = tableDocRef.addSnapshotListener(this, (snapshot, e) -> {
                if (e != null) {
                    Log.w(TAG, "Table listener failed", e);
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    Table table = snapshot.toObject(Table.class);
                    if (table != null) {
                        currentTableTotalPrice = table.getTotalPrice();
                        tableStatus = table.getStatus();
                        recalculateTemporaryTotal();
                        updateTableInfoDisplay();
                    }
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Stopping listeners");

        if (menuAdapter != null) {
            menuAdapter.stopListening();
        }
        if (tableListenerRegistration != null) {
            tableListenerRegistration.remove();
        }
    }

    // Recalculates total based on DB confirmed total + local pending items
    private void recalculateTemporaryTotal() {
        temporaryOrderTotal = currentTableTotalPrice;
        for (PendingOrderItemData item : pendingOrderItems.values()) {
            // Note: Ideally, you should subtract the old quantity if modifying an existing order item.
            // This logic assumes we are adding NEW items or purely local modifications.
            temporaryOrderTotal += (item.getQuantity() * item.getPrice());
        }
    }

    private void updateTableInfoDisplay() {
        textViewTableInfo.setText("Table: " + tableNumber + " - Total: €" + String.format("%.2f", temporaryOrderTotal));
    }

    // --- Interface Implementations ---

    @Override
    public void onCategoryClick(String categoryName, boolean isSelected) {
        if (isSelected) {
            currentSelectedCategories.add(categoryName);
        } else {
            currentSelectedCategories.remove(categoryName);
        }
        // Re-run setup to filter
        setUpMenuRecyclerView();
    }

    @Override
    public void onPlusClick(MenuItem menuItem, int position, int currentQuantity) {
        int newQuantity = currentQuantity + 1;
        updatePendingOrderItem(menuItem, newQuantity);
    }

    @Override
    public void onMinusClick(MenuItem menuItem, int position, int currentQuantity) {
        if (currentQuantity > 0) {
            int newQuantity = currentQuantity - 1;
            updatePendingOrderItem(menuItem, newQuantity);
        }
    }

    private void updatePendingOrderItem(MenuItem menuItem, int quantity) {
        // Get previous quantity for accurate total calculation
        int oldQuantity = 0;
        if (pendingOrderItems.containsKey(menuItem.getId())) {
            oldQuantity = pendingOrderItems.get(menuItem.getId()).getQuantity();
        }

        // Update Adapter UI
        menuAdapter.updateItemQuantity(menuItem.getId(), quantity);

        // Update Local State
        if (quantity > 0) {
            pendingOrderItems.put(menuItem.getId(), new PendingOrderItemData(
                    menuItem.getId(),
                    quantity,
                    menuItem.getPrice(),
                    menuItem.getName(),
                    menuItem.getCategory(),
                    menuItem.getType()
            ));
        } else {
            pendingOrderItems.remove(menuItem.getId());
        }

        // Update Total
        temporaryOrderTotal += (quantity - oldQuantity) * menuItem.getPrice();
        updateTableInfoDisplay();
    }

    private void sendOrderToFirestore() {
        showLoading(true);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Not logged in!", Toast.LENGTH_SHORT).show();
            showLoading(false);
            return;
        }

        if (pendingOrderItems.isEmpty()) {
            Toast.makeText(this, "No items to send.", Toast.LENGTH_SHORT).show();
            showLoading(false);
            return;
        }

        db.runTransaction(transaction -> {
            // 1. Read Table
            DocumentSnapshot tableSnapshot = transaction.get(tableDocRef);
            double currentConfirmedTableTotal = 0.0;
            if (tableSnapshot.exists()) {
                Table table = tableSnapshot.toObject(Table.class);
                if (table != null) currentConfirmedTableTotal = table.getTotalPrice();
            }

            // 2. Read Existing Items to merge quantities
            Map<String, Integer> existingQuantitiesMap = new HashMap<>();
            List<OrderItem> allOrderItemsList = new ArrayList<>();

            for (String menuItemId : pendingOrderItems.keySet()) {
                DocumentReference itemRef = currentOrderSubcollectionRef.document(menuItemId);
                DocumentSnapshot itemSnap = transaction.get(itemRef);
                if (itemSnap.exists()) {
                    OrderItem existingItem = itemSnap.toObject(OrderItem.class);
                    if (existingItem != null) existingQuantitiesMap.put(menuItemId, existingItem.getQuantity());
                }
            }

            // 3. Write Updates
            for (Map.Entry<String, PendingOrderItemData> entry : pendingOrderItems.entrySet()) {
                String menuItemId = entry.getKey();
                PendingOrderItemData itemData = entry.getValue();
                int quantityToAdd = itemData.getQuantity();

                DocumentReference itemRef = currentOrderSubcollectionRef.document(menuItemId);
                int existingQty = existingQuantitiesMap.getOrDefault(menuItemId, 0);
                int newTotalQty = existingQty + quantityToAdd;

                // Update Price
                double itemPrice = itemData.getPrice();
                currentConfirmedTableTotal += (quantityToAdd * itemPrice);

                if (newTotalQty > 0) {
                    OrderItem updatedItem = new OrderItem(
                            itemData.getMenuItemId(),
                            itemData.getName(),
                            itemData.getPrice(),
                            newTotalQty,
                            itemData.getCategory(),
                            itemData.getType(),
                            "Preparing"
                    );
                    transaction.set(itemRef, updatedItem);

                    // Add THIS BATCH to the preparer queue (kitchen)
                    // Note: We create a specific OrderItem for the kitchen that only shows the NEW quantity
                    OrderItem kitchenItem = new OrderItem(
                            itemData.getMenuItemId(),
                            itemData.getName(),
                            itemData.getPrice(),
                            quantityToAdd, // Only send new items to kitchen
                            itemData.getCategory(),
                            itemData.getType(),
                            "New"
                    );
                    allOrderItemsList.add(kitchenItem);
                } else {
                    transaction.delete(itemRef);
                }
            }

            // 4. Create Kitchen Ticket (Order Queue)
            double newQueueTotal = 0.0;
            for (OrderItem item : allOrderItemsList) newQueueTotal += (item.getQuantity() * item.getPrice());

            Order kitchenOrder = new Order();
            kitchenOrder.setTableNr(tableNumber);
            kitchenOrder.setOrderedItems(allOrderItemsList);
            kitchenOrder.setStatus("New");
            kitchenOrder.setTimestamp(new Date());
            kitchenOrder.setTotalPrice(newQueueTotal);

            DocumentReference newOrderQueueRef = db.collection("restaurants")
                    .document(restaurantId)
                    .collection("orderQueue")
                    .document();
            transaction.set(newOrderQueueRef, kitchenOrder);

            // 5. Update Table Status
            Map<String, Object> tableUpdates = new HashMap<>();
            tableUpdates.put("status", "Occupied");
            tableUpdates.put("totalPrice", currentConfirmedTableTotal);
            tableUpdates.put("activeOrderQueueId", newOrderQueueRef.getId());
            transaction.update(tableDocRef, tableUpdates);

            return null;

        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Order Sent!", Toast.LENGTH_SHORT).show();

            // Clear local UI state
            pendingOrderItems.clear();
            Map<String, Integer> selectedItems = menuAdapter.getSelectedItemsWithQuantities();
            for (String key : selectedItems.keySet()) {
                menuAdapter.updateItemQuantity(key, 0);
            }

            // Refresh totals from the listener
            showLoading(false);

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            showLoading(false);
        });
    }

    private void showLoading(boolean show) {
        progressBarLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        buttonSendOrder.setEnabled(!show);
        buttonSummaryOrder.setEnabled(!show);
    }
}