package com.example.orderman;

import android.app.Application;
import com.stripe.stripeterminal.TerminalApplicationDelegate;
import com.stripe.stripeterminal.taptopay.TapToPay;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate(); // Always call super.onCreate() first

        // Skip initialization if running in the TTPA process.
        if (TapToPay.isInTapToPayProcess()) return;

        // For example, this will be skipped.
        TerminalApplicationDelegate.onCreate(this);
    }
}