package com.riskfreeroutes.app.ui.home;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.riskfreeroutes.app.R;

/**
 * HomeActivity — Placeholder stub (full Map UI built in Module 3)
 * Fixed: now calls setContentView so it doesn't crash on launch.
 */
public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
    }
}
