package com.ordrino.orderman;

import static com.ordrino.orderman.LoginActivity.EXTRA_RESTAURANT_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_ID;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_NUMBER;
import static com.ordrino.orderman.OrderTakingActivity.EXTRA_TABLE_TOTAL_PRICE;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.ReaderCallback;
import com.stripe.stripeterminal.external.callable.TerminalListener;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration;
import com.stripe.stripeterminal.external.models.ConnectionStatus;
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.TapToPayDiscoveryConfiguration;
import com.stripe.stripeterminal.external.models.PaymentStatus;
import com.stripe.stripeterminal.external.models.Reader;
import com.stripe.stripeterminal.external.models.TerminalException;
import com.stripe.stripeterminal.log.LogLevel;

import org.jetbrains.annotations.NotNull;

public class DiscoverReadersActivity extends AppCompatActivity {
    public static final String TAG = "DISCOVEREADER";
    private Cancelable discoverCancelable;
    private boolean isDiscovering = false;
    private double currentTableTotalPrice;
    private String restaurantId;
    private static final int REQUEST_CODE_LOCATION = 100;

    private String tableId;
    private  int tableNumber;
    ActivityResultLauncher<Intent> launcher;



    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover_readers);

        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // You came back from ActivityB
                        finish();
                    }
                });
        if (getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE) &&
                getIntent().hasExtra(EXTRA_RESTAURANT_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_ID) &&
                getIntent().hasExtra(EXTRA_TABLE_NUMBER)) {
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);
            restaurantId = getIntent().getStringExtra(EXTRA_RESTAURANT_ID);
            tableId = getIntent().getStringExtra(EXTRA_TABLE_ID);
            tableNumber = getIntent().getIntExtra(EXTRA_TABLE_NUMBER, 0);
        }
        else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
        }

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

        if (Terminal.getInstance().getConnectedReader() == null) {
            onDiscoverReaders();
        } else {
            Log.d(TAG, "Reader already connected. Skipping discovery.");
            // Directly go to PaymentActivity or handle as needed
            Intent paymentIntent = new Intent(this, PaymentActivity.class);
            paymentIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, currentTableTotalPrice);
            paymentIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
            paymentIntent.putExtra(EXTRA_TABLE_ID, tableId);
            paymentIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);
            startActivity(paymentIntent);
            finish();
        }
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

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    public void onDiscoverReaders() {
        if (isDiscovering) return;
        isDiscovering = true;
        // TODO bei production flag auf false
        TapToPayDiscoveryConfiguration configDiscover = new TapToPayDiscoveryConfiguration(true);


        discoverCancelable = Terminal.getInstance().discoverReaders(
                configDiscover,
                readers -> {
                    isDiscovering = false;
                    Reader reader = readers.get(0); // Already discovered
                    Log.d(TAG, reader.toString());
                    String connectLocationId = null;

                    CustomTapToPayReaderListener tapToPayReaderListener = new CustomTapToPayReaderListener();


                    if (reader.getLocation() != null) {
                        connectLocationId = reader.getLocation().getId();
                    }


                    Terminal.getInstance().connectReader(reader,
                            new ConnectionConfiguration.TapToPayConnectionConfiguration(
                                    connectLocationId,
                                    true,
                                    tapToPayReaderListener
                            ),
                            new ReaderCallback() {
                                @Override
                                public void onSuccess(@NonNull Reader reader) {
                                    Log.d(TAG, "Connected to reader: " + reader.getSerialNumber());
                                    discoverCancelable = null;

                                    Intent paymentIntent = new Intent(DiscoverReadersActivity.this, PaymentActivity.class);
                                    paymentIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, currentTableTotalPrice);
                                    paymentIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
                                    paymentIntent.putExtra(EXTRA_TABLE_ID, tableId);
                                    paymentIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);
                                    launcher.launch(paymentIntent);
                                    Log.d(TAG, "Reader disconnected on stop");

                                }

                                @Override
                                public void onFailure(TerminalException e) {
                                    // Placeholder for handling exception
                                    Log.e(TAG, "Failed to connect to reader", e);
                                }
                            }
                    );
                },
                new Callback() {
                    @Override
                    public void onSuccess() {
                        // Placeholder for handling successful operation
                        Log.d(TAG, "Reader discovery started successfully");
                    }

                    @Override
                    public void onFailure(TerminalException e) {
                        // Placeholder for handling exception
                        Log.e(TAG, "Reader discovery failed", e);
                    }
                }

        );
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "Discovery stop");

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (discoverCancelable != null) {
            discoverCancelable.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Discovery cancelled successfully");
                    finish();
                }

                @Override
                public void onFailure(TerminalException e) {
                    Log.e(TAG, "Failed to cancel discovery", e);
                }
            });
            discoverCancelable = null;
        }

        if (Terminal.getInstance().getConnectedReader() != null) {
            Terminal.getInstance().disconnectReader(new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Reader disconnected on stop");
                    isDiscovering = false;
                    finish();

                }

                @Override
                public void onFailure(@NonNull TerminalException e) {
                    Log.e(TAG, "Failed to disconnect reader", e);
                    isDiscovering = false;
                }
            });
        }
    }
}