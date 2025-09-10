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

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OrderQueueActivity extends AppCompatActivity {

    private static final String TAG = "OrderQueueActivity";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference orderItemsRef;
    private OrderAdapter adapter;
    private ListenerRegistration firestoreListener;

    private TextView titleTextView;
    private RecyclerView recyclerViewOrderQueue;
    private String restaurantId;
    private ImageButton archiveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_queue);

        titleTextView = findViewById(R.id.tv_order_queue_title);
        recyclerViewOrderQueue = findViewById(R.id.recycler_view_order_queue);
        archiveButton = findViewById(R.id.btn_view_archive);

        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
        } else {
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        archiveButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ArchivedOrdersActivity.class);
            intent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
            startActivity(intent);
        });

        setupRecyclerView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupFirestoreListener();
    }

    private void setupRecyclerView() {
        orderItemsRef = db.collection("restaurants").document(restaurantId).collection("orderQueue");

        adapter = new OrderAdapter(this, (documentReference, currentStatus) -> {
            updateOrderStatus(documentReference, currentStatus);
        });

        recyclerViewOrderQueue.setHasFixedSize(true);
        recyclerViewOrderQueue.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrderQueue.setAdapter(adapter);
    }

    private void setupFirestoreListener() {
        // Keep the query simple and stable, sorted only by timestamp
        Query query = orderItemsRef
                .whereNotIn("status", Arrays.asList(new String[]{"Served"}))
                .orderBy("timestamp", Query.Direction.ASCENDING);

        firestoreListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (querySnapshot != null) {
                List<Order> newOrders = new ArrayList<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    Order order = doc.toObject(Order.class);
                    order.setDocumentReference(doc.getReference());
                    newOrders.add(order);
                }

                // --- NEW CODE: MANUALLY SORT THE LIST BY STATUS ---
                Collections.sort(newOrders, new Comparator<Order>() {
                    @Override
                    public int compare(Order o1, Order o2) {
                        return getStatusPriority(o1.getStatus()) - getStatusPriority(o2.getStatus());
                    }
                });
                // --- END NEW CODE ---

                adapter.updateData(newOrders);
            }
        });
    }

    private int getStatusPriority(String status) {
        switch (status) {
            case "New":
                return 0; // Highest priority
            case "Preparing":
                return 1;
            case "Ready":
                return 2; // Lowest priority
            default:
                return 99; // Fallback for any other status
        }
    }

    private void updateOrderStatus(DocumentReference documentReference, String currentStatus) {
        String newStatus;
        switch (currentStatus) {
            case "New":
                newStatus = "Preparing";
                break;
            case "Preparing":
                newStatus = "Ready";
                break;
            case "Ready":
                newStatus = "Served";
                break;
            default:
                Toast.makeText(this, "Order is already served!", Toast.LENGTH_SHORT).show();
                return;
        }

        documentReference.update("status", newStatus)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "DocumentSnapshot successfully updated!"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating document", e));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}