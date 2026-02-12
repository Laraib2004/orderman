package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;

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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class InvoiceQRCodeActivity extends AppCompatActivity {

    private static final String TAG = "InvoiceQRCodeActivity";

    private String invoiceUrl;
    private String receiptUuid;
    private String restaurantId;
    private String backendUrl; // Will be fetched from Firebase

    private Button buttonStorno;
    private ImageView qrImageView;
    private CustomConnectionTokenProvider tokenProvider;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_qrcode);

        db = FirebaseFirestore.getInstance();
        qrImageView = findViewById(R.id.qrImageView);
        buttonStorno = findViewById(R.id.button_storno);

        // Initially disable the button until we fetch the configuration
        buttonStorno.setEnabled(false);

        if (getIntent().hasExtra(EXTRA_INVOICE_PDF_URL) && getIntent().hasExtra(EXTRA_RESTAURANT_ID)) {
            invoiceUrl = getIntent().getStringExtra(EXTRA_INVOICE_PDF_URL);
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);

            // 1. Generate QR Code immediately
            generateQrCode(invoiceUrl);

            // 2. Extract UUID from the URL (e.g., .../public/receipt/uuid-1234)
            try {
                Uri uri = Uri.parse(invoiceUrl);
                receiptUuid = uri.getLastPathSegment(); // Gets the part after the last '/'
                Log.d(TAG, "Extracted UUID: " + receiptUuid);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse UUID from URL", e);
                Toast.makeText(this, "Error: Invalid Receipt URL", Toast.LENGTH_LONG).show();
                return;
            }

            // 3. Fetch Backend URL from Firebase
            fetchBackendUrlAndSetupStorno();

        } else {
            Toast.makeText(this, "Error: Missing invoice info.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void fetchBackendUrlAndSetupStorno() {
        if (restaurantId == null) return;

        db.collection("restaurants").document(restaurantId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Fetch the 'api_domain' field
                        backendUrl = documentSnapshot.getString("api_domain");

                        if (backendUrl != null && !backendUrl.isEmpty()) {
                            // Initialize Token Provider with the fetched URL
                            tokenProvider = new CustomConnectionTokenProvider(restaurantId, backendUrl);

                            // Enable button and setup listener
                            buttonStorno.setEnabled(true);
                            buttonStorno.setOnClickListener(v -> showStornoConfirmationDialog());
                        } else {
                            Log.e(TAG, "api_domain field is missing in Firestore for restaurant: " + restaurantId);
                            Toast.makeText(this, "Config Error: Missing API Domain", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e(TAG, "Restaurant document not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch restaurant config", e);
                    Toast.makeText(this, "Network Error: Could not fetch config", Toast.LENGTH_SHORT).show();
                });
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
        if (receiptUuid == null || receiptUuid.isEmpty()) {
            Toast.makeText(this, "Error: No Receipt ID found.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Void Receipt")
                .setMessage("Are you sure you want to void (STORNO) this receipt?\n\nThis will send a legal cancellation request to Agenzia delle Entrate.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Void It", (dialog, whichButton) -> {
                    performStorno();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void performStorno() {
        buttonStorno.setEnabled(false);
        buttonStorno.setText("Voiding...");

        if (tokenProvider == null) {
            Toast.makeText(this, "Error: Backend not configured", Toast.LENGTH_SHORT).show();
            return;
        }

        tokenProvider.voidReceipt(receiptUuid, restaurantId, new CustomConnectionTokenProvider.VoidReceiptCallback() {
            @Override
            public void onSuccess(String voidUuid, String status) {
                Toast.makeText(InvoiceQRCodeActivity.this, "Receipt Voided Successfully!", Toast.LENGTH_LONG).show();
                buttonStorno.setText("RECEIPT VOIDED");
                buttonStorno.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(InvoiceQRCodeActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                buttonStorno.setEnabled(true);
                buttonStorno.setText("STORNO RECEIPT");
            }
        });
    }
}