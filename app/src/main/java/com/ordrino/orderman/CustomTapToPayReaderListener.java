package com.ordrino.orderman;

import android.util.Log;

import androidx.annotation.NonNull;

import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener;
import com.stripe.stripeterminal.external.models.DisconnectReason;
import com.stripe.stripeterminal.external.models.Reader;

public class CustomTapToPayReaderListener implements TapToPayReaderListener {
    @Override
    public void onReaderReconnectStarted(@NonNull Reader reader, @NonNull Cancelable cancelReconnect, @NonNull DisconnectReason reason) {
        Log.d("MainActivity", "Reconnection to reader " + reader.getId() + " started!");
    }
    @Override
    public void onReaderReconnectSucceeded(@NonNull Reader reader) {
        Log.d("MainActivity", "Reader " + reader.getId() + " reconnected successfully!");
    }

    @Override
    public void onReaderReconnectFailed(@NonNull Reader reader) {
        Log.d("MainActivity", "Reconnection to reader " + reader.getId() + " failed!");
    }
};