package com.example.orderman;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.ReaderCallback;
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.TapToPayConnectionConfiguration;
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.TapToPayDiscoveryConfiguration;
import com.stripe.stripeterminal.external.models.Reader;
import com.stripe.stripeterminal.external.models.TerminalException;

import org.jetbrains.annotations.NotNull;

public class DiscoverReadersActivity extends AppCompatActivity {
    public static final String TAG = "DISCOVEREADER";
    private Cancelable discoverCancelable;
    private boolean isDiscovering = false;


    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Terminal.getInstance().getConnectedReader() == null) {
            onDiscoverReaders();
        } else {
            Log.d(TAG, "Reader already connected. Skipping discovery.");
            // Directly go to PaymentActivity or handle as needed
            startActivity(new Intent(this, PaymentActivity.class));
            finish();
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
                                    /*paymentIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
                                    paymentIntent.putExtra(EXTRA_TABLE_ID, tableId);
                                    paymentIntent.putExtra(EXTRA_TABLE_NUMBER, tableNumber);
                                    paymentIntent.putExtra(EXTRA_TABLE_TOTAL_PRICE, currentTableTotalPrice);*/
                                    startActivity(paymentIntent);
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


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (discoverCancelable != null) {
            discoverCancelable.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Discovery cancelled successfully");
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