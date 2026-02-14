package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast; // Added for testing

import com.google.firebase.auth.FirebaseAuth;

public class AdminDashboardActivity extends AppCompatActivity {

    private String restaurantId; // Store the restaurant ID

    private TextView adminTitleTextView;
    private Button btnManageMenu;
    private Button btnManageTables;
    private Button btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        new AppUpdater(this).checkForUpdate();

        // Retrieve the restaurantId from the Intent
        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            Toast.makeText(this, "Admin for Restaurant ID: " + restaurantId, Toast.LENGTH_LONG).show(); // For debugging
        } else {
            // Handle case where restaurantId is missing (e.g., redirect to login or show error)
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_LONG).show();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return; // Prevent further execution if ID is missing
        }

        adminTitleTextView = findViewById(R.id.tv_admin_dashboard_title);
        btnManageMenu = findViewById(R.id.btn_manage_menu);
        btnManageTables = findViewById(R.id.btn_manage_tables);
        btnLogout = findViewById(R.id.btn_logout);

        btnManageMenu.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, MenuManagementActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId); // Pass restaurantId
            startActivity(intent);
        });

        btnManageTables.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, TableManagementActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RESTAURANT_ID, restaurantId); // Pass restaurantId
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}