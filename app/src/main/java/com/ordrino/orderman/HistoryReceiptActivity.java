package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class HistoryReceiptActivity extends AppCompatActivity {

    private static final String TAG = "HistoryReceiptActivity";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ReceiptAdapter adapter;
    private RecyclerView recyclerView;

    private String restaurantId;
    private String tableId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_receipt);
        setTitle("Receipts");

        recyclerView = findViewById(R.id.recycler_view_receipts);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
        tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);

        if (restaurantId == null || tableId == null) {
            Log.e(TAG, "Restaurant ID or Table ID missing from intent.");
            finish();
        }
    }

    private void setUpRecyclerView() {
        // Reference to the subcollection containing receipt URLs
        CollectionReference receiptsRef = db.collection("restaurants").document(restaurantId)
                .collection("tables").document(tableId).collection("historyReceiptToday");

        // Order the receipts by timestamp, newest first
        Query query = receiptsRef.orderBy("timestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<Receipt> options = new FirestoreRecyclerOptions.Builder<Receipt>()
                .setQuery(query, Receipt.class)
                .build();

        adapter = new ReceiptAdapter(options, this::onReceiptClick);
        recyclerView.setAdapter(adapter);
    }

    private void onReceiptClick(String receiptUrl) {
        // When a receipt item is clicked, open the InvoiceQRCodeActivity with the URL
        Intent qrIntent = new Intent(this, InvoiceQRCodeActivity.class);
        qrIntent.putExtra(EXTRA_INVOICE_PDF_URL, receiptUrl);
        // You may need to pass the table ID too, if InvoiceQRCodeActivity requires it
        startActivity(qrIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-set up the adapter and start listening every time the activity is visible
        if (restaurantId != null && tableId != null) {
            setUpRecyclerView();
            if (adapter != null) {
                adapter.startListening();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening();
            // Explicitly detach the adapter to prevent stale views
            recyclerView.setAdapter(null);
            adapter = null;
        }
    }
}