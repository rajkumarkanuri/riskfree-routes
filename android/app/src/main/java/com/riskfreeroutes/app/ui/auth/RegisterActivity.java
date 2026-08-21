package com.riskfreeroutes.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.network.RetrofitClient;
import com.riskfreeroutes.app.network.dto.CompleteProfileRequest;
import com.riskfreeroutes.app.ui.home.HomeActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * RegisterActivity — Handles user registration / Profile Completion
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etFullName, etEmail, etPassword, etMobile;
    private Button btnSignUp;
    private TextView tvSignIn;
    private ImageView btnBack;

    private com.google.firebase.auth.FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase Auth
        mAuth = com.google.firebase.auth.FirebaseAuth.getInstance();

        // Bind views
        etFullName = findViewById(R.id.et_full_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etMobile = findViewById(R.id.et_mobile);
        
        btnSignUp = findViewById(R.id.btn_sign_up);
        tvSignIn = findViewById(R.id.tv_sign_in);
        btnBack = findViewById(R.id.btn_back);

        // Pre-fill fields if passed from LoginActivity (Google Sign-In)
        String passedEmail = getIntent().getStringExtra("EXTRA_EMAIL");
        String passedName = getIntent().getStringExtra("EXTRA_NAME");

        if (passedEmail != null && !passedEmail.isEmpty()) {
            etEmail.setText(passedEmail);
            etEmail.setEnabled(false); // Lock the email field
            etEmail.setAlpha(0.7f); // Make it look slightly disabled
        }
        if (passedName != null && !passedName.isEmpty()) {
            etFullName.setText(passedName);
        }

        // Setup click listeners
        btnSignUp.setOnClickListener(v -> attemptProfileCompletion());

        tvSignIn.setOnClickListener(v -> finish()); // Go back to login
        btnBack.setOnClickListener(v -> finish()); // Go back to login
    }

    /**
     * Gathers user input, validates it, and sends the profile data to our Spring Boot backend.
     * If the backend is not reachable, navigates to Home anyway (useful for UI testing).
     */
    private void attemptProfileCompletion() {
        String name = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String phone = etMobile.getText().toString().trim();
        
        // 1. Basic Validation
        if (name.isEmpty()) {
            etFullName.setError("Name is required");
            etFullName.requestFocus();
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (phone.isEmpty()) {
            etMobile.setError("Phone is required");
            etMobile.requestFocus();
            return;
        }

        // If it's a new email registration (not Google), validate password
        if (mAuth.getCurrentUser() == null) {
            if (password.isEmpty() || password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                etPassword.requestFocus();
                return;
            }
        }

        // 2. Disable button to prevent double-clicks while loading
        btnSignUp.setEnabled(false);
        btnSignUp.setText(getString(R.string.loading));

        if (mAuth.getCurrentUser() == null) {
            // STEP A: Create the user in Firebase first
            Log.d(TAG, "Creating new Firebase user: " + email);
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // User created! Now proceed to Step B (Backend Sync)
                            Log.d(TAG, "Firebase user created successfully");
                            completeProfileOnBackend(name, phone);
                        } else {
                            Log.e(TAG, "Firebase Registration Failed", task.getException());
                            Toast.makeText(this, "Registration Failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                            resetButton();
                        }
                    });
        } else {
            // Already logged in (likely via Google) — skip Step A and go directly to Step B
            completeProfileOnBackend(name, phone);
        }
    }

    /**
     * Sends the profile data to our Spring Boot backend.
     * The AuthInterceptor will automatically attach the fresh Firebase token.
     */
    private void completeProfileOnBackend(String name, String phone) {
        // Create the DTO containing the data we want to send to the server
        CompleteProfileRequest request = new CompleteProfileRequest(name, phone, "Standard");

        // DEBUG: Check if Firebase user is null right before API call
        Log.d(TAG, "Calling complete-profile API. Current FirebaseUser: " + mAuth.getCurrentUser());

        // 3. Make the API Call to our backend
        RetrofitClient.getInstance().getApi().completeProfile(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Profile completed successfully on backend — navigate to Home
                    navigateToHome();
                } else {
                    Log.e(TAG, "Backend returned error: " + response.code());
                    Toast.makeText(RegisterActivity.this,
                            "Server error (" + response.code() + "). Please try again.", Toast.LENGTH_LONG).show();
                    resetButton();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Network error reaching backend", t);
                // Backend is not running yet — navigate to Home anyway for UI testing
                Toast.makeText(RegisterActivity.this,
                        "Backend unreachable — continuing in demo mode.", Toast.LENGTH_SHORT).show();
                navigateToHome();
            }
        });
    }

    /**
     * Navigates to HomeActivity and clears the entire back stack.
     * 'finishAffinity()' closes all previous screens (Login, Register) so the user
     * cannot press Back to return to the auth flow.
     */
    private void navigateToHome() {
        startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
        finishAffinity();
    }

    private void resetButton() {
        btnSignUp.setEnabled(true);
        btnSignUp.setText(getString(R.string.btn_register));
    }
}
