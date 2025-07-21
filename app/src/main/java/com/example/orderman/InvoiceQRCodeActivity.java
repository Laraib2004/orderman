package com.example.orderman;

import static com.example.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.example.orderman.OrderSummaryActivity.EXTRA_INVOICE_PDF_URL;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class InvoiceQRCodeActivity extends AppCompatActivity {

    private String invoiceUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_qrcode);

        ImageView qrImageView = findViewById(R.id.qrImageView);

        if (getIntent().hasExtra(EXTRA_INVOICE_PDF_URL)){
            invoiceUrl = getIntent().getStringExtra(EXTRA_INVOICE_PDF_URL);
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(invoiceUrl, BarcodeFormat.QR_CODE, 600, 600);
                qrImageView.setImageBitmap(bitmap);
            } catch (WriterException e) {
                e.printStackTrace();
            }
        }

    }
}