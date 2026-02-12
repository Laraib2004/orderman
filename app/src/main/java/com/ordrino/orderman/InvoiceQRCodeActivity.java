package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;
// Assuming you have this constant defined somewhere, otherwise define string "extraTableId"
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class InvoiceQRCodeActivity extends AppCompatActivity {

    private static final String TAG = "InvoiceQRCodeActivity";

    private String invoiceUrl;
    private String receiptUuid;
    private String restaurantId;
    private String tableId; // Needed to find the doc in Firestore
    private String backendUrl;

    private Button buttonStorno;
    private ImageView qrImageView;
    private CustomConnectionTokenProvider tokenProvider;
    private FirebaseFirestore db;

    // Reference to the specific receipt document in Firestore
    private DocumentReference currentReceiptDocRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_qrcode);

        db = FirebaseFirestore.getInstance();
        qrImageView = findViewById(R.id.qrImageView);
        buttonStorno = findViewById(R.id.button_storno);

        // Initially disable button while we load data
        buttonStorno.setEnabled(false);

        // 1. Check Extras
        if (getIntent().hasExtra(EXTRA_INVOICE_PDF_URL) && getIntent().hasExtra(EXTRA_RESTAURANT_ID)) {
            invoiceUrl = getIntent().getStringExtra(EXTRA_INVOICE_PDF_URL);
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);

            // We need tableID to find the document in historyReceiptToday
            if (getIntent().hasExtra(EXTRA_TABLE_ID)) {
                tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            }

            // 2. Generate QR Code
            generateQrCode(invoiceUrl);

            // 3. Extract UUID from URL
            try {
                Uri uri = Uri.parse(invoiceUrl);
                receiptUuid = uri.getLastPathSegment();
                Log.d(TAG, "Extracted UUID: " + receiptUuid);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse UUID", e);
                Toast.makeText(this, "Error: Invalid Receipt URL", Toast.LENGTH_LONG).show();
                return;
            }

            // 4. Fetch Config & Check Receipt Status
            fetchBackendUrl();
            if (tableId != null) {
                checkReceiptStatus();
            } else {
                Log.w(TAG, "No Table ID provided, cannot check/update void status in Firestore.");
            }

        } else {
            Toast.makeText(this, "Error: Missing invoice info.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void fetchBackendUrl() {
        if (restaurantId == null) return;

        db.collection("restaurants").document(restaurantId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        backendUrl = documentSnapshot.getString("api_domain");
                        if (backendUrl != null && !backendUrl.isEmpty()) {
                            tokenProvider = new CustomConnectionTokenProvider(restaurantId, backendUrl);
                            // Only enable if we haven't already determined it's voided
                            // (We'll handle the final enable state in checkReceiptStatus)
                        }
                    }
                });
    }

    // 🔹 NEW: Check Firestore for 'voided' status
    private void checkReceiptStatus() {
        // Query the collection to find the document matching this URL
        db.collection("restaurants").document(restaurantId)
                .collection("tables").document(tableId)
                .collection("historyReceiptToday")
                .whereEqualTo("url", invoiceUrl) // Match by URL since we don't have the Doc ID
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        currentReceiptDocRef = doc.getReference();

                        Boolean isVoided = doc.getBoolean("voided");

                        if (isVoided == null) {
                            // Field doesn't exist -> Create it (default: false)
                            currentReceiptDocRef.update("voided", false);
                            updateStornoButtonState(false);
                            // 2. Setup Storno Button Logic
                            buttonStorno.setOnClickListener(v -> showStornoConfirmationDialog());
                        } else if (isVoided) {
                            // Already voided -> Lock UI
                            updateStornoButtonState(true);
                        } else {
                            // Not voided -> Enable UI
                            updateStornoButtonState(false);
                            // 2. Setup Storno Button Logic
                            buttonStorno.setOnClickListener(v -> showStornoConfirmationDialog());
                        }
                    } else {
                        Log.w(TAG, "Receipt document not found in Firestore.");
                        // If we can't find the doc, we default to allowing storno (risky?)
                        // or maybe just keep it enabled but warn logic might fail to update DB.
                        updateStornoButtonState(false);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error checking receipt status", e));
    }

    private void updateStornoButtonState(boolean isVoided) {
        if (isVoided) {
            buttonStorno.setText("RECEIPT ALREADY VOIDED");
            buttonStorno.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            buttonStorno.setEnabled(false);
        } else {
            buttonStorno.setText("STORNO RECEIPT");
            buttonStorno.setEnabled(true); // Now safe to enable
        }
    }

    private void generateQrCode(String url) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 600, 600);
            qrImageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void showStornoConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Void Receipt")
                .setMessage("Are you sure? This will send a cancellation to Agenzia delle Entrate.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Void It", (dialog, whichButton) -> performStorno())
                .setNegativeButton("No", null)
                .show();
    }

    private void performStorno() {
        if (tokenProvider == null) {
            Toast.makeText(this, "Backend not configured yet", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonStorno.setEnabled(false);
        buttonStorno.setText("Voiding...");

        tokenProvider.voidReceipt(receiptUuid, restaurantId, new CustomConnectionTokenProvider.VoidReceiptCallback() {
            @Override
            public void onSuccess(String voidUuid, String status) {
                Toast.makeText(InvoiceQRCodeActivity.this, "Void Success!", Toast.LENGTH_LONG).show();

                // 🔹 NEW: Update Firestore with BOTH status and the new UUID
                if (currentReceiptDocRef != null) {

                    // NEW: Update QR Code to show the VOID receipt
                    if (backendUrl != null && !backendUrl.isEmpty()) {
                        // Construct the URL for the new void receipt
                        // Pattern: https://your-backend.com/public/receipt/NEW_UUID
                        String newVoidUrl = backendUrl + "/public/receipt/" + voidUuid;

                        // Create a map of updates
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("voided", true);
                        updates.put("url", newVoidUrl); // Save the new UUID for reference
                        updates.put("timestamp", new Date()); // Optional: Save when it happened

                        currentReceiptDocRef.update(updates)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore updated with void info"))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update local DB status", e);
                                    Toast.makeText(InvoiceQRCodeActivity.this, "Warning: Local DB not updated", Toast.LENGTH_SHORT).show();
                                });

                        Log.d(TAG, "Generating QR for Void Receipt: " + newVoidUrl);
                        generateQrCode(newVoidUrl);
                    }
                }

                updateStornoButtonState(true);
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(InvoiceQRCodeActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                // Re-enable button on failure
                updateStornoButtonState(false);
            }
        });
    }
}