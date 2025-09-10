package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArchivedOrdersActivity extends AppCompatActivity {

    private static final String TAG = "ArchivedOrdersActivity";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference orderItemsRef;
    private OrderAdapter adapter;
    private ListenerRegistration firestoreListener;

    private RecyclerView recyclerViewArchive;
    private String restaurantId;
    private Button clearArchiveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_orders);

        recyclerViewArchive = findViewById(R.id.recycler_view_archive);
        clearArchiveButton = findViewById(R.id.btn_clear_archive);

        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
        } else {
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupRecyclerView();

        // New button click listener with confirmation dialog
        clearArchiveButton.setOnClickListener(v -> showConfirmationDialog());
    }

    // Method to show the confirmation dialog
    private void showConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Archive Deletion")
                .setMessage("This operation should only be done after closing for the day, as it is irreversible. Are you sure you want to clear the archived orders?")
                .setPositiveButton("Clear Archive", (dialog, which) -> {
                    // User confirmed, proceed with deletion
                    clearArchivedOrders();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // User canceled, do nothing
                    dialog.dismiss();
                })
                .show();
    }

    private void setupRecyclerView() {
        orderItemsRef = db.collection("restaurants").document(restaurantId).collection("orderQueue");

        adapter = new OrderAdapter(this, null);

        recyclerViewArchive.setHasFixedSize(true);
        recyclerViewArchive.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewArchive.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupFirestoreListener();
    }

    private void setupFirestoreListener() {
        Query query = orderItemsRef
                .whereIn("status", Arrays.asList("Served"))
                .orderBy("timestamp", Query.Direction.DESCENDING);

        firestoreListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (querySnapshot != null) {
                List<Order> newOrders = new ArrayList<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    Order order = doc.toObject(Order.class);
                    newOrders.add(order);
                }
                adapter.updateData(newOrders);
            }
        });
    }

    private void clearArchivedOrders() {
        orderItemsRef.whereEqualTo("status", "Served")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No archived orders to delete.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (int i = 0; i < queryDocumentSnapshots.size(); i++) {
                        batch.delete(queryDocumentSnapshots.getDocuments().get(i).getReference());
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Batch delete successful.");
                                Toast.makeText(this, "Archive cleared successfully!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error performing batch delete.", e);
                                Toast.makeText(this, "Failed to clear archive.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting documents for deletion.", e);
                    Toast.makeText(this, "Failed to retrieve orders.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}