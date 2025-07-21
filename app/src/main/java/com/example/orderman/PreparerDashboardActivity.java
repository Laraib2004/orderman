package com.example.orderman;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

public class PreparerDashboardActivity extends AppCompatActivity {

    private TextView preparerTitleTextView;
    private Button btnViewOrders; // To see pending orders
    private Button btnLogout;
    private String restaurantId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preparer_dashboard);

        preparerTitleTextView = findViewById(R.id.tv_preparer_dashboard_title);
        btnViewOrders = findViewById(R.id.btn_view_orders); // Assuming this ID
        btnLogout = findViewById(R.id.btn_logout);

        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            // Optional: Toast.makeText(this, "Waiter for Restaurant ID: " + restaurantId, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_LONG).show();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        btnViewOrders.setOnClickListener(v -> {
            // Preparers would go to a screen to view orders assigned to them (food/drinks)
            // You'd need a dedicated activity for this, e.g., OrderQueueActivity
            Toast.makeText(PreparerDashboardActivity.this, "View Orders functionality coming soon!", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(PreparerDashboardActivity.this, LoginActivity.class); // Or a specific WaiterTablesActivity
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}