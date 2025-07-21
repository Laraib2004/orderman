package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ordrino.orderman.models.CustomConnectionTokenProvider;
import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.TerminalListener;
import com.stripe.stripeterminal.external.models.ConnectionStatus;
import com.stripe.stripeterminal.external.models.PaymentStatus;
import com.stripe.stripeterminal.external.models.TerminalException;
import com.stripe.stripeterminal.log.LogLevel;

import org.jetbrains.annotations.NotNull;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int REQUEST_CODE_LOCATION = 100;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;

    // Key for passing restaurant ID through intents
    public static final String EXTRA_RESTAURANT_ID = "EXTRA_RESTAURANT_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_LOCATION);
        }
        // Create your listener object. Override any methods that you want to be notified about
        TerminalListener listener = new TerminalListener() {
            @Override
            public void onConnectionStatusChange(ConnectionStatus status) {
                Log.d(TAG, "onConnectionStatusChange: "+status);
            }

            @Override
            public void onPaymentStatusChange(PaymentStatus status) {
                Log.d(TAG, "onPaymentStatusChange: "+status);
            }
        };

// Choose the level of messages that should be logged to your console
        LogLevel logLevel = LogLevel.VERBOSE;

// Create your token provider.
        CustomConnectionTokenProvider tokenProvider = new CustomConnectionTokenProvider();

// Pass in the current application context, your desired logging level, your token provider, and the listener you created
        if (!Terminal.isInitialized()) {
            try {
                Terminal.initTerminal(getApplicationContext(), logLevel, tokenProvider, listener);
            } catch (TerminalException e) {
                throw new RuntimeException(e);
            }
        }


        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailEditText = findViewById(R.id.et_email);
        passwordEditText = findViewById(R.id.et_password);
        loginButton = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progressBar);

        loginButton.setOnClickListener(v -> loginUser());
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already logged in, fetch their restaurantId and role, then redirect
            showProgressBar();
            emailEditText.setText(currentUser.getEmail());
            passwordEditText.setText("secret");
            fetchUserRestaurantAndRole(currentUser.getUid());
        }
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Please enter email and password.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressBar();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            fetchUserRestaurantAndRole(user.getUid());
                        } else {
                            hideProgressBar();
                            Toast.makeText(LoginActivity.this, "Authentication failed: User is null.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        hideProgressBar();
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchUserRestaurantAndRole(String userId) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    hideProgressBar();
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String role = document.getString("role");
                            String restaurantId = document.getString("restaurantId"); // Fetch restaurantId

                            if (role != null && restaurantId != null) {
                                redirectBasedOnRole(role, restaurantId); // Pass restaurantId
                            } else {
                                Toast.makeText(LoginActivity.this, "User role or restaurant ID not found.", Toast.LENGTH_LONG).show();
                                Log.e(TAG, "User role (" + role + ") or restaurantId (" + restaurantId + ") is null for UID: " + userId);
                                mAuth.signOut();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, "User data not found. Please contact admin.", Toast.LENGTH_LONG).show();
                            Log.e(TAG, "User document does not exist in Firestore for UID: " + userId);
                            mAuth.signOut();
                        }
                    } else {
                        Log.e(TAG, "Error getting user document: ", task.getException());
                        Toast.makeText(LoginActivity.this, "Failed to retrieve user data. Please try again.", Toast.LENGTH_SHORT).show();
                        mAuth.signOut();
                    }
                });
    }

    private void redirectBasedOnRole(String role, String restaurantId) {
        Intent intent;
        switch (role) {
            case "admin":
                intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                break;
            case "waiter":
                intent = new Intent(LoginActivity.this, WaiterDashboardActivity.class);
                break;
            case "kitchen_preparer":
            case "barkeeper_preparer":
                intent = new Intent(LoginActivity.this, PreparerDashboardActivity.class);
                break;
            default:
                Toast.makeText(LoginActivity.this, "Unknown role: " + role, Toast.LENGTH_SHORT).show();
                mAuth.signOut();
                return;
        }
        intent.putExtra(EXTRA_RESTAURANT_ID, restaurantId); // Pass the restaurantId
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NotNull String[] permissions,
            @NotNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_LOCATION && grantResults.length > 0 &&
                grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            throw new RuntimeException("Location services are required to connect to a reader.");
        }
    }

    private void showProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
        }
    }

    private void hideProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
        }
    }
}