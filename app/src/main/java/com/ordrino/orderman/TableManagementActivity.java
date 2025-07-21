package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.ordrino.orderman.models.Table;

// ... (imports)
// In TableManagementActivity.java

// ... (existing imports)

public class TableManagementActivity extends AppCompatActivity {

    private static final String TAG = "TableManagementActivity"; // Add TAG for logging

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private TableAdapter adapter;
    private CollectionReference tablesRef;
    private String restaurantId;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table_management);

        Log.d(TAG, "onCreate: TableManagementActivity started.");

        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            tablesRef = db.collection("restaurants").document(restaurantId).collection("tables");
            Log.d(TAG, "Tables collection ref: " + tablesRef.getPath());
        } else {
            Toast.makeText(this, "Error: Restaurant ID not passed to Table Management.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Restaurant ID missing from Intent.");
            finish();
            return;
        }

        FloatingActionButton buttonAddTable = findViewById(R.id.button_add_table);
        buttonAddTable.setOnClickListener(v -> {
            Log.d(TAG, "Add Table button clicked.");
            Intent intent = new Intent(TableManagementActivity.this, AddEditTableActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            startActivity(intent);
        });

        recyclerView = findViewById(R.id.recycler_view_tables);
        if (recyclerView == null) {
            Log.e(TAG, "onCreate: RecyclerView with ID recycler_view_tables not found.");
            Toast.makeText(this, "Layout error: RecyclerView not found.", Toast.LENGTH_LONG).show();
            return;
        }
        recyclerView.setHasFixedSize(true);
        // LayoutManager will be set in onStart for robustness
    }

    private void setUpRecyclerViewAndAdapter() {
        Log.d(TAG, "setUpRecyclerViewAndAdapter: Initializing or re-initializing adapter.");
        if (tablesRef == null) {
            Log.e(TAG, "setUpRecyclerViewAndAdapter: tablesRef is null, cannot create query.");
            Toast.makeText(this, "Error: Tables reference not initialized for RecyclerView.", Toast.LENGTH_SHORT).show();
            return;
        }

        Query query = tablesRef.orderBy("number", Query.Direction.ASCENDING);
        FirestoreRecyclerOptions<Table> options = new FirestoreRecyclerOptions.Builder<Table>()
                .setQuery(query, Table.class)
                .build();

        if (adapter != null) {
            adapter.stopListening();
            Log.d(TAG, "setUpRecyclerViewAndAdapter: Stopped previous adapter before re-creation.");
        }

        adapter = new TableAdapter(options);
        Log.d(TAG, "setUpRecyclerViewAndAdapter: TableAdapter initialized.");

        // Long click for taking orders
        adapter.setOnItemLongClickListener((table, position) -> {
            Log.d(TAG, "Table item clicked for EDIT: " + table.getNumber());
            Intent intent = new Intent(TableManagementActivity.this, AddEditTableActivity.class);
            intent.putExtra("table", table);
            intent.putExtra("tableId", table.getId()); // Pass the document ID
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            startActivity(intent);

            return true; // Consume the long click event
        });

        // Single click for editing table details
        adapter.setOnItemClickListener((table, position) -> {
            Log.d(TAG, "Table item long-clicked for ORDER: " + table.getNumber());
            Intent intent = new Intent(TableManagementActivity.this, OrderTakingActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            intent.putExtra(OrderTakingActivity.EXTRA_TABLE_ID, table.getId());
            intent.putExtra(OrderTakingActivity.EXTRA_TABLE_NUMBER, table.getNumber());
            intent.putExtra(OrderTakingActivity.EXTRA_TABLE_STATUS, table.getStatus());
            intent.putExtra(OrderTakingActivity.EXTRA_TABLE_TOTAL_PRICE, table.getTotalPrice()); // <--- Pass the table's total price

            startActivity(intent);
        });

        Log.d(TAG, "setUpRecyclerViewAndAdapter: Adapter and click listeners prepared.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: TableManagementActivity entered. Starting fresh RecyclerView setup.");

        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // Always re-set LayoutManager
        setUpRecyclerViewAndAdapter(); // This will create and prepare the 'adapter' instance

        if (adapter != null) {
            recyclerView.setAdapter(adapter); // Re-set the adapter
            recyclerView.post(() -> {
                if (adapter != null) {
                    adapter.startListening();
                    Log.d(TAG, "onStart: Adapter started listening (delayed via post()).");
                } else {
                    Log.w(TAG, "onStart: Adapter is null after post() delay (unexpected).");
                }
            });
        } else {
            Log.e(TAG, "onStart: Adapter is null immediately after setUpRecyclerViewAndAdapter(). Cannot proceed.");
            Toast.makeText(this, "Failed to initialize table list.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: TableManagementActivity entered.");
        if (adapter != null) {
            adapter.stopListening();
            Log.d(TAG, "onStop: Adapter stopped listening.");
        } else {
            Log.w(TAG, "onStop: Adapter is null, no need to stop listening.");
        }
    }
}