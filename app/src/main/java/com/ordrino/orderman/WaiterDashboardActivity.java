package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

public class WaiterDashboardActivity extends AppCompatActivity {

    private TextView waiterTitleTextView;
    private Button btnViewTables; // To see table status and create orders
    private Button btnLogout;
    private String restaurantId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiter_dashboard);

        waiterTitleTextView = findViewById(R.id.tv_waiter_dashboard_title);
        btnViewTables = findViewById(R.id.btn_view_tables); // Assuming this ID
        btnLogout = findViewById(R.id.btn_logout);

        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            new AppUpdater(this).checkForUpdate();
            // Optional: Toast.makeText(this, "Waiter for Restaurant ID: " + restaurantId, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_LONG).show();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        btnViewTables.setOnClickListener(v -> {
            // Waiters would typically go to a screen to see tables and take orders
            Intent intent = new Intent(WaiterDashboardActivity.this, TableManagementActivity.class); // Or a specific WaiterTablesActivity
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(WaiterDashboardActivity.this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}