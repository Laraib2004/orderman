package com.example.orderman;

import android.app.Application;
// import com.google.firebase.FirebaseApp; // Uncomment if you want to initialize Firebase here explicitly

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // If you need global initialization, put it here.
        // Firebase is usually auto-initialized if google-services.json is set up correctly.
        // FirebaseApp.initializeApp(this); // Usually not needed with standard setup
    }
}