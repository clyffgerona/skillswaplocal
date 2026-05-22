// SplashActivity.java - Add this as your launcher activity
package com.skillswap.local.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.skillswap.local.R;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "skillswap_legal_prefs";
    private static final String KEY_ACCEPTED = "legal_terms_accepted";
    private static final String KEY_ACCEPTED_VERSION = "legal_terms_version";
    private static final String CURRENT_VERSION = "2.0.0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            checkLegalAcceptance();
        }, 1500);
    }

    private void checkLegalAcceptance() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hasAccepted = prefs.getBoolean(KEY_ACCEPTED, false);
        String savedVersion = prefs.getString(KEY_ACCEPTED_VERSION, "");

        if (!hasAccepted || !savedVersion.equals(CURRENT_VERSION)) {
            // Need to accept legal terms first
            startActivity(new Intent(this, LegalAgreementActivity.class));
            finish();
        } else {
            // Already accepted, proceed to normal flow
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                // Check if user is suspended
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("Users").document(currentUser.getUid()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                Boolean isSuspended = documentSnapshot.getBoolean("isSuspended");
                                if (isSuspended != null && isSuspended) {
                                    String reason = documentSnapshot.getString("suspensionReason");
                                    new androidx.appcompat.app.AlertDialog.Builder(this)
                                            .setTitle("Account Suspended")
                                            .setMessage("Your account has been suspended: " + (reason != null ? reason : "No reason provided") + "\n\nYou can submit an appeal to request reinstatement.")
                                            .setPositiveButton("Submit Appeal", (dialog, which) -> {
                                                startActivity(new Intent(this, AppealActivity.class));
                                                finish();
                                            })
                                            .setNegativeButton("Close", (dialog, which) -> {
                                                FirebaseAuth.getInstance().signOut();
                                                startActivity(new Intent(this, LoginActivity.class));
                                                finish();
                                            })
                                            .setCancelable(false)
                                            .show();
                                } else {
                                    startActivity(new Intent(this, HomeActivity.class));
                                    finish();
                                }
                            } else {
                                startActivity(new Intent(this, HomeActivity.class));
                                finish();
                            }
                        })
                        .addOnFailureListener(e -> {
                            startActivity(new Intent(this, HomeActivity.class));
                            finish();
                        });
            } else {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        }
    }
}