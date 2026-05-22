package com.skillswap.local.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.skillswap.local.R;
import com.skillswap.local.models.Appeal;

public class AppealActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId, userName, suspensionReason;

    private TextView tvSuspensionReason;
    private EditText etAppealReason, etEvidence;
    private Button btnSubmit;
    private TextView btnBack, btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appeal);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        userId = user.getUid();

        tvSuspensionReason = findViewById(R.id.tvSuspensionReason);
        etAppealReason = findViewById(R.id.etAppealReason);
        etEvidence = findViewById(R.id.etEvidence);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnBack = findViewById(R.id.btnBack);
        btnCancel = findViewById(R.id.btnCancel);

        loadSuspensionData();

        btnSubmit.setOnClickListener(v -> submitAppeal());
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadSuspensionData() {
        db.collection("Users").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                userName = doc.getString("fullName");
                suspensionReason = doc.getString("suspensionReason");
                tvSuspensionReason.setText(suspensionReason != null && !suspensionReason.isEmpty() 
                        ? suspensionReason : "No specific reason provided by administration.");
            }
        });
    }

    private void submitAppeal() {
        String reason = etAppealReason.getText().toString().trim();
        String evidence = etEvidence.getText().toString().trim();

        if (reason.isEmpty()) {
            Toast.makeText(this, "Please explain why you are appealing.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");
        
        Appeal appeal = new Appeal(userId, userName, reason, evidence);

        db.collection("appeals").add(appeal)
                .addOnSuccessListener(docRef -> {
                    docRef.update("appealId", docRef.getId());
                    Toast.makeText(this, "Appeal submitted successfully. Our team will review it.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit Appeal");
                });
    }
}
