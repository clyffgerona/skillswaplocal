package com.skillswap.local.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.skillswap.local.R;

public class RegisterActivity extends AppCompatActivity {

    private EditText etRegEmail, etRegPassword, etRegConfirmPassword;
    private ImageView ivRegPasswordToggle, ivRegConfirmToggle;
    private Button btnRegisterSubmit, btnVerifyEmail;
    private TextView tvGoToLogin, tvRegError;

    private FirebaseAuth mAuth;
    private Handler verificationHandler = new Handler();
    private Runnable verificationRunnable;

    private boolean isPasswordVisible = false;
    private boolean isConfirmVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        etRegEmail = findViewById(R.id.etRegEmail);
        etRegPassword = findViewById(R.id.etRegPassword);
        etRegConfirmPassword = findViewById(R.id.etRegConfirmPassword);
        ivRegPasswordToggle = findViewById(R.id.ivRegPasswordToggle);
        ivRegConfirmToggle = findViewById(R.id.ivRegConfirmToggle);
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
        tvRegError = findViewById(R.id.tvRegError);

        // Email domain watcher
        etRegEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String email = s.toString().trim();
                if (email.endsWith("@gmail.com")) {
                    btnVerifyEmail.setVisibility(View.VISIBLE);
                } else {
                    btnVerifyEmail.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnVerifyEmail.setOnClickListener(v -> sendVerification());

        // Setup visibility toggles
        setupPasswordToggle(ivRegPasswordToggle, etRegPassword, true);
        setupPasswordToggle(ivRegConfirmToggle, etRegConfirmPassword, false);

        btnRegisterSubmit.setOnClickListener(v -> createAccount());

        tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void setupPasswordToggle(ImageView toggleIcon, EditText editText, boolean isMainPassword) {
        toggleIcon.setOnClickListener(v -> {
            boolean currentVisibleState = isMainPassword ? isPasswordVisible : isConfirmVisible;
            boolean newState = !currentVisibleState;

            if (isMainPassword) isPasswordVisible = newState;
            else isConfirmVisible = newState;

            if (newState) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                toggleIcon.setImageResource(R.drawable.ic_visibility_on);
            } else {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                toggleIcon.setImageResource(R.drawable.ic_visibility_off);
            }
            // Keep cursor at the end
            editText.setSelection(editText.getText().length());
        });
    }

    private void sendVerification() {
        String email = etRegEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) return;

        btnVerifyEmail.setEnabled(false);
        btnVerifyEmail.setText("Sending Link...");

        // We create a temporary account to send verification
        mAuth.createUserWithEmailAndPassword(email, "Temp123!@#")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(verifyTask -> {
                                        if (verifyTask.isSuccessful()) {
                                            Toast.makeText(this, "Verification email sent! Please check your Gmail (including Spam folder).", Toast.LENGTH_LONG).show();
                                            tvRegError.setText("Check your email and spam folder for the verification link.");
                                            tvRegError.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                                            tvRegError.setVisibility(View.VISIBLE);
                                            startVerificationCheck(user);
                                        } else {
                                            showError("Failed to send verification email.");
                                            btnVerifyEmail.setEnabled(true);
                                            btnVerifyEmail.setText("Verify Email");
                                        }
                                    });
                        }
                    } else {
                        // If user already exists but not verified, we might need to handle that
                        mAuth.signInWithEmailAndPassword(email, "Temp123!@#")
                                .addOnCompleteListener(loginTask -> {
                                    if (loginTask.isSuccessful()) {
                                        FirebaseUser user = mAuth.getCurrentUser();
                                        if (user != null && !user.isEmailVerified()) {
                                            user.sendEmailVerification().addOnCompleteListener(vTask -> {
                                                if (vTask.isSuccessful()) {
                                                    Toast.makeText(this, "Verification link resent! Check your Gmail and Spam folder.", Toast.LENGTH_LONG).show();
                                                    tvRegError.setText("Check your Gmail and Spam folder for the link.");
                                                    tvRegError.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                                                    tvRegError.setVisibility(View.VISIBLE);
                                                    startVerificationCheck(user);
                                                } else {
                                                    showError("Failed to resend verification.");
                                                }
                                            });
                                        } else if (user != null && user.isEmailVerified()) {
                                            unlockPasswordFields();
                                        }
                                    } else {
                                        showError("Email already in use or error occurred.");
                                        btnVerifyEmail.setEnabled(true);
                                        btnVerifyEmail.setText("Verify Email");
                                    }
                                });
                    }
                });
    }

    private void startVerificationCheck(FirebaseUser user) {
        btnVerifyEmail.setText("Waiting for verification...");
        verificationRunnable = new Runnable() {
            @Override
            public void run() {
                user.reload().addOnCompleteListener(task -> {
                    if (user.isEmailVerified()) {
                        unlockPasswordFields();
                        verificationHandler.removeCallbacks(this);
                    } else {
                        verificationHandler.postDelayed(this, 3000); // Check every 3 seconds
                    }
                });
            }
        };
        verificationHandler.post(verificationRunnable);
    }

    private void unlockPasswordFields() {
        runOnUiThread(() -> {
            etRegPassword.setEnabled(true);
            etRegConfirmPassword.setEnabled(true);
            btnRegisterSubmit.setEnabled(true);
            btnRegisterSubmit.setAlpha(1.0f);
            btnRegisterSubmit.setText("Complete Registration");
            btnVerifyEmail.setVisibility(View.GONE);
            tvRegError.setVisibility(View.GONE);
            etRegEmail.setEnabled(false); // Lock email once verified
            Toast.makeText(this, "Email Verified! You can now set your password.", Toast.LENGTH_SHORT).show();
        });
    }

    private void createAccount() {
        String password = etRegPassword.getText().toString().trim();
        String confirmPassword = etRegConfirmPassword.getText().toString().trim();

        tvRegError.setVisibility(View.GONE);

        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        btnRegisterSubmit.setEnabled(false);
        btnRegisterSubmit.setText("Completing Registration...");

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.updatePassword(password).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Intent intent = new Intent(RegisterActivity.this, VerificationActivity.class);
                    intent.putExtra("user_email", user.getEmail());
                    intent.putExtra("user_password", password);
                    startActivity(intent);
                    finish();
                } else {
                    showError("Failed to update password.");
                    btnRegisterSubmit.setEnabled(true);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (verificationHandler != null && verificationRunnable != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
    }

    private void showError(String message) {
        tvRegError.setText(message);
        tvRegError.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        tvRegError.setVisibility(View.VISIBLE);
        btnRegisterSubmit.setEnabled(true);
        btnRegisterSubmit.setText("Next Step: Verification");
    }
}