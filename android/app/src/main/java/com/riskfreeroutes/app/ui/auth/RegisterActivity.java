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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.model.User;
import com.riskfreeroutes.app.repository.UserRepository;
import com.riskfreeroutes.app.ui.home.HomeActivity;

/**
 * RegisterActivity — Handles user registration and profile creation with Firebase.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etFullName, etEmail, etPassword, etMobile;
    private Button btnSignUp;
    private TextView tvSignIn;
    private ImageView btnBack;

    private FirebaseAuth mAuth;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        userRepository = new UserRepository();

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
            etEmail.setEnabled(false);
            etEmail.setAlpha(0.7f);
        }
        if (passedName != null && !passedName.isEmpty()) {
            etFullName.setText(passedName);
        }

        // Setup click listeners
        btnSignUp.setOnClickListener(v -> attemptProfileCompletion());
        tvSignIn.setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * Gathers user input, validates it, and registers with Firebase Auth & Firestore.
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
            // Create user in Firebase Auth
            Log.d(TAG, "Creating new Firebase user: " + email);
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Firebase user created successfully");
                            saveUserProfile(name, phone);
                        } else {
                            Log.e(TAG, "Firebase Registration Failed", task.getException());
                            Toast.makeText(this, "Registration Failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"),
                                    Toast.LENGTH_LONG).show();
                            resetButton();
                        }
                    });
        } else {
            // Already logged in via Google Auth
            saveUserProfile(name, phone);
        }
    }

    /**
     * Creates and saves the user profile in Firestore.
     */
    private void saveUserProfile(String name, String phone) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show();
            resetButton();
            return;
        }

        User user = new User(
                currentUser.getUid(),
                name,
                currentUser.getEmail(),
                phone
        );

        userRepository.createUserProfile(
                user,
                this::navigateToHome,
                this::navigateToHome
        );
    }

    /**
     * Navigates to HomeActivity and clears the entire back stack.
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
