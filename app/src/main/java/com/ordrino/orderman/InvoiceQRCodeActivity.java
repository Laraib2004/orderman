package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import android.util.Log;

public class InvoiceQRCodeActivity extends AppCompatActivity {

    private static final String TAG = "InvoiceQRCodeActivity";
    private String invoiceUrl;
    private String tableId;
    private String restaurantId;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_qrcode);

        ImageView qrImageView = findViewById(R.id.qrImageView);

        if (getIntent().hasExtra(EXTRA_INVOICE_PDF_URL)
                && getIntent().hasExtra(EXTRA_TABLE_ID)
                && getIntent().hasExtra(EXTRA_RESTAURANT_ID)) {
            invoiceUrl = getIntent().getStringExtra(EXTRA_INVOICE_PDF_URL);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);

            // First, add the receipt URL to Firestore
            addReceiptToHistory(invoiceUrl, tableId, restaurantId);

            // Then, generate the QR code
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(invoiceUrl, BarcodeFormat.QR_CODE, 600, 600);
                qrImageView.setImageBitmap(bitmap);
            } catch (WriterException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error generating QR Code", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Error: Missing invoice or table information.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Intent missing EXTRA_INVOICE_PDF_URL or EXTRA_TABLE_ID.");
            finish();
        }
    }

    private void addReceiptToHistory(String url, String tableId, String restaurantId) {
        if (tableId == null || tableId.isEmpty()) {
            Log.e(TAG, "Table ID is missing, cannot add receipt to history.");
            return;
        }

        // Create the document reference to the specific table
        // This implicitly creates the 'historyReceiptToday' subcollection if it doesn't exist.
        CollectionReference historyRef = db.collection("restaurants")
                .document(restaurantId)
                .collection("tables")
                .document(tableId)
                .collection("historyReceiptToday");

        // Prepare the data to be stored
        Map<String, Object> receiptData = new HashMap<>();
        receiptData.put("url", url);
        receiptData.put("timestamp", new Date()); // Use a Java Date object for server-side timestamp
        // You could also add other information like total amount, payment method, etc.

        // Add the new document to the subcollection. Firestore automatically generates a document ID.
        historyRef.add(receiptData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Receipt URL added to history for table " + tableId + " with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding receipt to history for table " + tableId, e);
                });
    }
}