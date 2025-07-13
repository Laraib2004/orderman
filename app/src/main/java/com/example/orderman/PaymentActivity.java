package com.example.orderman;

import static com.example.orderman.OrderTakingActivity.EXTRA_TABLE_TOTAL_PRICE;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback;
import com.stripe.stripeterminal.external.models.CollectConfiguration;
import com.stripe.stripeterminal.external.models.PaymentIntent;
import com.stripe.stripeterminal.external.models.PaymentIntentParameters;
import com.stripe.stripeterminal.external.models.TerminalException;

import org.jetbrains.annotations.NotNull;

public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    private Cancelable collectCancelable;
    private double currentTableTotalPrice;


    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Starting PaymentActivity");

        if (getIntent().hasExtra(EXTRA_TABLE_TOTAL_PRICE)) {
            currentTableTotalPrice = getIntent().getDoubleExtra(EXTRA_TABLE_TOTAL_PRICE, 0.0);
        }
        else {
            Toast.makeText(this, "Error: Order information missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Required Intent extras missing for OrderSummaryActivity.");
            finish();
        }

        // STEP 1: Create PaymentIntent via backend
        CustomConnectionTokenProvider tokenProvider = new CustomConnectionTokenProvider();
        tokenProvider.createPaymentIntent((int) (currentTableTotalPrice*100), new CustomConnectionTokenProvider.CreateIntentCallback() {
            @Override
            public void onSuccess(String clientSecret) {
                Log.d(TAG, "Client secret received: " + clientSecret);

                // STEP 2: Retrieve the PaymentIntent from Stripe
                Terminal.getInstance().retrievePaymentIntent(
                        clientSecret,
                        new PaymentIntentCallback() {
                            @Override
                            public void onSuccess(@NotNull PaymentIntent paymentIntent) {
                                Log.d(TAG, "PaymentIntent retrieved: " + paymentIntent);

                                // STEP 3: Collect Payment Method
                                collectCancelable = Terminal.getInstance().collectPaymentMethod(
                                        paymentIntent,
                                        new PaymentIntentCallback() {
                                            @Override
                                            public void onSuccess(@NotNull PaymentIntent collectedIntent) {
                                                Log.d(TAG, "Payment method collected. Confirming...");

                                                // STEP 4: Confirm the payment
                                                Terminal.getInstance().confirmPaymentIntent(
                                                        collectedIntent,
                                                        new PaymentIntentCallback() {
                                                            @Override
                                                            public void onSuccess(@NotNull PaymentIntent confirmedIntent) {
                                                                Log.d(TAG, "Payment confirmed: " + confirmedIntent.getId());

                                                                // STEP 5: Capture it via backend
                                                                tokenProvider.capturePaymentIntent(
                                                                        confirmedIntent.getId(),
                                                                        new CustomConnectionTokenProvider.CaptureIntentCallback() {
                                                                            @Override
                                                                            public void onSuccess(String status) {
                                                                                Log.d(TAG, "Payment captured! Status: " + status);
                                                                                Toast.makeText(PaymentActivity.this, "Payment completed!", Toast.LENGTH_LONG).show();
                                                                                setResult(RESULT_OK);
                                                                                finish();
                                                                            }

                                                                            @Override
                                                                            public void onFailure(Exception e) {
                                                                                Log.e(TAG, "Capture failed: ", e);
                                                                                Toast.makeText(PaymentActivity.this, "Capture failed!", Toast.LENGTH_SHORT).show();
                                                                            }
                                                                        });
                                                            }

                                                            @Override
                                                            public void onFailure(@NotNull TerminalException exception) {
                                                                Log.e(TAG, "Failed to confirm PaymentIntent: " + exception.getErrorMessage());
                                                            }
                                                        }
                                                );
                                            }

                                            @Override
                                            public void onFailure(@NotNull TerminalException exception) {
                                                Log.e(TAG, "Failed to collect payment method: " + exception.getErrorMessage());
                                            }
                                        },
                                        new CollectConfiguration.Builder()
                                                .build()
                                );
                            }

                            @Override
                            public void onFailure(@NotNull TerminalException exception) {
                                Log.e(TAG, "Failed to retrieve PaymentIntent: " + exception.getErrorMessage());
                            }
                        }
                );
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to create PaymentIntent via backend", e);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (collectCancelable != null) {
            collectCancelable.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "collectCancelable canceled successfully");
                }

                @Override
                public void onFailure(@NotNull TerminalException e) {
                    Log.e(TAG, "Failed to cancel collectCancelable", e);
                }
            });
        }
    }
}