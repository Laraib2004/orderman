package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;

import android.content.Intent;
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
import java.util.List;

public class HistoryReceiptActivity extends AppCompatActivity {

    private static final String TAG = "HistoryReceiptActivity";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference receiptsRef;
    private ReceiptAdapter adapter;
    private ListenerRegistration firestoreListener;

    private RecyclerView recyclerView;
    private Button clearHistoryButton;

    private String restaurantId;
    private String tableId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_receipt);
        setTitle("Receipts");

        recyclerView = findViewById(R.id.recycler_view_receipts);
        clearHistoryButton = findViewById(R.id.btn_clear_history);

        if (getIntent().hasExtra(EXTRA_RESTAURANT_ID) && getIntent().hasExtra(EXTRA_TABLE_ID)) {
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            receiptsRef = db.collection("restaurants").document(restaurantId)
                    .collection("tables").document(tableId).collection("historyReceiptToday");
        } else {
            Log.e(TAG, "Restaurant ID or Table ID missing from intent.");
            Toast.makeText(this, "Error: Restaurant ID or Table ID not found.", Toast.LENGTH_LONG).show();
            finish();
        }

        setupRecyclerView();

        clearHistoryButton.setOnClickListener(v -> showConfirmationDialog());
    }

    // Method to show the confirmation dialog before deleting
    private void showConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("This operation should only be done after closing for the day, as it is irreversible. Are you sure you want to clear all receipts for today?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    clearAllReceipts();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    private void setupRecyclerView() {
        adapter = new ReceiptAdapter(this::onReceiptClick);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void clearAllReceipts() {
        // Query for all documents in the collection
        receiptsRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                Toast.makeText(this, "No receipts to delete.", Toast.LENGTH_SHORT).show();
                return;
            }

            WriteBatch batch = db.batch();
            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                batch.delete(doc.getReference());
            }

            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Batch delete successful.");
                        Toast.makeText(this, "All receipts cleared!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error performing batch delete.", e);
                        Toast.makeText(this, "Failed to clear receipts.", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void onReceiptClick(String receiptUrl) {
        Intent qrIntent = new Intent(this, InvoiceQRCodeActivity.class);
        qrIntent.putExtra(EXTRA_INVOICE_PDF_URL, receiptUrl);
        qrIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
        startActivity(qrIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupFirestoreListener();
    }

    private void setupFirestoreListener() {
        Query query = receiptsRef.orderBy("timestamp", Query.Direction.DESCENDING);

        firestoreListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (querySnapshot != null) {
                List<Receipt> newReceipts = new ArrayList<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    Receipt receipt = doc.toObject(Receipt.class);
                    newReceipts.add(receipt);
                }
                adapter.updateData(newReceipts);
            }
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