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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_qrcode);

        ImageView qrImageView = findViewById(R.id.qrImageView);

        if (getIntent().hasExtra(EXTRA_INVOICE_PDF_URL)) {
            invoiceUrl = getIntent().getStringExtra(EXTRA_INVOICE_PDF_URL);

            // Then, generate the QR code
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(invoiceUrl, BarcodeFormat.QR_CODE, 600, 600);
                qrImageView.setImageBitmap(bitmap);
            } catch (WriterException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error generating QR Code", Toast.LENGTH_SHORT).show();
            }
        }
        else {
            Toast.makeText(this, "Error: Missing invoice or table information.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Intent missing EXTRA_INVOICE_PDF_URL or EXTRA_TABLE_ID.");
            finish();
        }
    }

}