package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.Arrays;

public class OrderQueueActivity extends AppCompatActivity {

    private static final String TAG = "OrderQueueActivity";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference orderItemsRef;
    private PreparerOrderAdapter adapter;

    private TextView titleTextView;
    private RecyclerView recyclerViewOrderQueue;

    private String restaurantId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_queue);

        titleTextView = findViewById(R.id.tv_order_queue_title);
        recyclerViewOrderQueue = findViewById(R.id.recycler_view_order_queue);

        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
        } else {
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        // The query should fetch all order items that are not yet 'Completed' or 'Served'
        // You'll need to make sure your OrderItem model has a "status" field.
        orderItemsRef = db.collection("restaurants").document(restaurantId).collection("orderQueue");

        // We use orderBy("status") to group orders by their status, for a clean view.
        Query query = orderItemsRef
                .whereNotIn("status", Arrays.asList(new String[]{"Ready", "Served"}))
                .orderBy("status")
                .orderBy("timestamp", Query.Direction.ASCENDING);

        // In your OrderQueueActivity.java
        FirestoreRecyclerOptions<Order> options = new FirestoreRecyclerOptions.Builder<Order>()
                .setQuery(query, Order.class)
                .build();

        adapter = new PreparerOrderAdapter(options, this);

        recyclerViewOrderQueue.setHasFixedSize(true);
        recyclerViewOrderQueue.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrderQueue.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) {
            adapter.startListening();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening();
        }
    }
}