package com.example.orderman;

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

import java.util.ArrayList; // Added for new ArrayList
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

// Implement the new category click listener interface
public class MenuManagementActivity extends AppCompatActivity implements CategoryFilterAdapter.OnCategoryClickListener {

    private static final String TAG = "MenuManagementActivity";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference menuItemsRef;
    private String restaurantId;

    private MenuManagementItemAdapter adapter; // Adapter for menu items
    private RecyclerView recyclerViewMenu; // Renamed for clarity (was recyclerView)

    private CategoryFilterAdapter categoryAdapter; // New adapter for categories
    private RecyclerView recyclerViewCategories; // New RecyclerView for categories

    private Set<String> currentSelectedCategories = new HashSet<>(); // Tracks selected categories for filtering

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_management);

        Log.d(TAG, "onCreate: MenuManagementActivity started.");

        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            Log.d(TAG, "onCreate: Received restaurantId: " + restaurantId);
            menuItemsRef = db.collection("restaurants").document(restaurantId).collection("menuItems");
            Log.d(TAG, "onCreate: menuItemsRef path: " + menuItemsRef.getPath());
        } else {
            Toast.makeText(this, "Error: Restaurant ID not passed to Menu Management.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "onCreate: Restaurant ID missing from Intent.");
            finish();
            return;
        }

        FloatingActionButton buttonAddMenuItem = findViewById(R.id.button_add_menu_item);
        buttonAddMenuItem.setOnClickListener(v -> {
            Log.d(TAG, "Add Menu Item button clicked.");
            Intent intent = new Intent(MenuManagementActivity.this, AddEditMenuItemActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            startActivity(intent);
        });

        recyclerViewMenu = findViewById(R.id.recycler_view_menu); // Initialize menu item RecyclerView
        if (recyclerViewMenu == null) {
            Log.e(TAG, "onCreate: RecyclerView with ID recycler_view_menu not found in layout.");
            Toast.makeText(this, "Layout error: Menu RecyclerView not found.", Toast.LENGTH_LONG).show();
            return;
        }
        recyclerViewMenu.setHasFixedSize(true);

        // Initialize Category RecyclerView
        recyclerViewCategories = findViewById(R.id.recycler_view_categories);
        if (recyclerViewCategories == null) {
            Log.e(TAG, "onCreate: RecyclerView with ID recycler_view_categories not found in layout.");
            Toast.makeText(this, "Layout error: Category RecyclerView not found.", Toast.LENGTH_LONG).show();
            return;
        }

        // Get categories from string-array
        String[] categoryArray = getResources().getStringArray(R.array.category_item);
        List<String> allCategories = Arrays.asList(categoryArray);

        // Initialize CategoryFilterAdapter
        categoryAdapter = new CategoryFilterAdapter(allCategories, this); // 'this' implements OnCategoryClickListener
        recyclerViewCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerViewCategories.setAdapter(categoryAdapter);

        // setUpRecyclerViewAndAdapter will be called in onStart, which is called after onCreate.
        // It's important that setUpRecyclerViewAndAdapter also sets the adapter for recyclerViewMenu
        // and starts listening.
    }

    private void setUpRecyclerViewAndAdapter() {
        Log.d(TAG, "setUpRecyclerViewAndAdapter: Initializing or re-initializing adapter.");
        if (menuItemsRef == null) {
            Log.e(TAG, "setUpRecyclerViewAndAdapter: menuItemsRef is null, cannot create query.");
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

        // IMPORTANT: Stop listening to the OLD adapter before creating a new one
        if (adapter != null) {
            adapter.stopListening();
            Log.d(TAG, "setUpRecyclerViewAndAdapter: Stopped previous adapter before re-creation.");
        }

        adapter = new MenuManagementItemAdapter(options); // Create NEW adapter with the (potentially filtered) query

        adapter.setOnItemClickListener((menuItem, position) -> {
            Log.d(TAG, "Item clicked: " + menuItem.getName() + " at position: " + position);
            Intent intent = new Intent(MenuManagementActivity.this, AddEditMenuItemActivity.class);
            if (menuItem.getId() == null) {
                Log.w(TAG, "Item ID is null when clicked. Fetching from snapshot.");
                String itemId = adapter.getSnapshots().getSnapshot(position).getId();
                menuItem.setId(itemId);
            }
            intent.putExtra("menuItem", menuItem);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            startActivity(intent);
        });
        Log.d(TAG, "setUpRecyclerViewAndAdapter: Adapter and click listener prepared.");

        // IMPORTANT: Set the NEW adapter to the RecyclerView
        recyclerViewMenu.setAdapter(adapter);
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
        setUpRecyclerViewAndAdapter();

        // IMPORTANT: Start the NEW adapter listening for data
        if (adapter != null) {
            adapter.startListening();
            Log.d(TAG, "onCategoryClick: Menu adapter restarted listening with new filter.");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: MenuManagementActivity entered. Setting up RecyclerViews.");

        // Set LayoutManager for the main menu items RecyclerView
        recyclerViewMenu.setLayoutManager(new LinearLayoutManager(this));

        // Initial setup of the menu items adapter with the initial query (no filter initially)
        setUpRecyclerViewAndAdapter();

        // Start listening for the menu items adapter
        if (adapter != null) {
            adapter.startListening();
            Log.d(TAG, "onStart: Menu adapter started listening.");
        } else {
            Log.e(TAG, "onStart: Menu adapter is null. Cannot start listening.");
            Toast.makeText(this, "Failed to initialize menu list.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: MenuManagementActivity entered.");
        // Stop listening for the menu items adapter to prevent memory leaks
        if (adapter != null) {
            adapter.stopListening();
            Log.d(TAG, "onStop: Menu adapter stopped listening.");
        } else {
            Log.w(TAG, "onStop: Adapter is null, no need to stop listening.");
        }
    }
}