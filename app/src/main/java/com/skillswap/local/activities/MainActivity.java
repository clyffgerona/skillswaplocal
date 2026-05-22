package com.skillswap.local.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.skillswap.local.R; // <-- Add this line right here!

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}