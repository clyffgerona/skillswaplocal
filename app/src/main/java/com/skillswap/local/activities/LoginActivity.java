package com.skillswap.local.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.skillswap.local.R;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView tvLoginError;
    private ImageView ivPasswordToggle;
    private Button btnLogin;
    private View loginCard;
    private LinearLayout loadingOverlay;
    private boolean passwordVisible = false;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvLoginError = findViewById(R.id.tvLoginError);
        ivPasswordToggle = findViewById(R.id.ivPasswordToggle);
        btnLogin = findViewById(R.id.btnLogin);
        loginCard = findViewById(R.id.login_card);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        ivPasswordToggle.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivPasswordToggle.setImageResource(R.drawable.ic_visibility_on);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> attemptLogin());

        // ---------------------------------------------------------
        // NEW: Forgot Password Dialog Setup
        // ---------------------------------------------------------
        TextView tvForgot = findViewById(R.id.tvForgotPassword);
        if (tvForgot != null) {
            tvForgot.setOnClickListener(v -> showForgotPasswordDialog());
        }

        TextView tvRegister = findViewById(R.id.tvGoToRegister);
        if (tvRegister != null) {
            tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        }

        // Auto-login check
        if (mAuth.getCurrentUser() != null) {
            showLoading(true);
            checkUserStatus(mAuth.getCurrentUser().getUid());
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Please enter email and password.");
            return;
        }

        if (email.equals("admin") && password.equals("admin123")) {
            Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AdminDashboardActivity.class));
            finish();
            return;
        }

        hideError();
        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        checkUserStatus(mAuth.getCurrentUser().getUid());
                    } else {
                        showLoading(false);
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Authentication failed.";
                        showError(errorMsg);
                    }
                });
    }

    private void checkUserStatus(String userId) {
        db.collection("Users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        String fetchedName = documentSnapshot.getString("fullName");

                        String fullName = fetchedName != null ? fetchedName : "Maria Santos";
                        String firstName = fullName.split(" ")[0];

                        if ("approved".equalsIgnoreCase(status)) {
                            Boolean isSuspended = documentSnapshot.getBoolean("isSuspended");
                            if (isSuspended != null && isSuspended) {
                                showLoading(false);
                                String reason = documentSnapshot.getString("suspensionReason");
                                new androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("Account Suspended")
                                        .setMessage("Your account has been suspended: " + (reason != null ? reason : "No reason provided") + "\n\nYou can submit an appeal to request reinstatement.")
                                        .setPositiveButton("Submit Appeal", (dialog, which) -> {
                                            startActivity(new Intent(this, AppealActivity.class));
                                        })
                                        .setNegativeButton("Close", (dialog, which) -> {
                                            mAuth.signOut();
                                            hideError();
                                            dialog.dismiss();
                                        })
                                        .setCancelable(false)
                                        .show();
                                return;
                            }
                            onLoginSuccess(fullName, firstName);
                            return;
                        } else if ("pending".equalsIgnoreCase(status)) {
                            startActivity(new Intent(LoginActivity.this, PendingApprovalActivity.class));
                            finish();
                            return;
                        } else if ("rejected".equalsIgnoreCase(status) || status == null || "none".equalsIgnoreCase(status)) {
                            startActivity(new Intent(LoginActivity.this, VerificationActivity.class));
                            finish();
                            return;
                        } else {
                            mAuth.signOut();
                            showError("Account status unknown. Please contact support.");
                        }
                    } else {
                        mAuth.signOut();
                        showError("User record not found. Please register again.");
                    }

                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    mAuth.signOut();
                    showError("Failed to check account status. Check connection.");
                    showLoading(false);
                });
    }

    public void onLoginSuccess(String fullName, String firstName) {
        SharedPreferences prefs = getSharedPreferences("skillswap_prefs", MODE_PRIVATE);
        prefs.edit()
                .putString("user_full_name", fullName)
                .putString("user_first_name", firstName)
                .apply();

        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------------
    // NEW: Forgot Password Dialog Execution
    // ---------------------------------------------------------
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setMessage("Enter your registered email address to receive a password reset link.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint(" Email Address");

        // Add some padding to make it look nicer
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 0, 50, 0);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Send Link", (dialog, which) -> {
            String emailAddress = input.getText().toString().trim();
            if (TextUtils.isEmpty(emailAddress)) {
                Toast.makeText(LoginActivity.this, "Email is required.", Toast.LENGTH_SHORT).show();
                return;
            }
            mAuth.sendPasswordResetEmail(emailAddress).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Reset link sent! Check your email and spam folder.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // ---------------------------------------------------------
    // NEW: Clean Loading State Manager
    // ---------------------------------------------------------
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            loginCard.setVisibility(View.GONE);
            loadingOverlay.setVisibility(View.VISIBLE);
        } else {
            loginCard.setVisibility(View.VISIBLE);
            loadingOverlay.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            btnLogin.setText("Log In");
        }
    }

    private void showError(String message) {
        if (tvLoginError != null) {
            tvLoginError.setText(message);
            tvLoginError.setVisibility(View.VISIBLE);
        }
    }

    private void hideError() {
        if (tvLoginError != null) {
            tvLoginError.setVisibility(View.GONE);
        }
    }
}