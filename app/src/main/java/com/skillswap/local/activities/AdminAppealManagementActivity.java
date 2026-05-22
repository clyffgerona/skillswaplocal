package com.skillswap.local.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.skillswap.local.R;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class AdminAppealManagementActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        db = FirebaseFirestore.getInstance();
        setupUI();
        loadAppeals();
    }

    private void setupUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F4F6F9"));

        // Header with Back Button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#0F2645"));
        header.setPadding(20, 20, 20, 20);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBack = new TextView(this);
        tvBack.setText("←"); // Using a simple arrow
        tvBack.setTextSize(24f);
        tvBack.setTextColor(Color.WHITE);
        tvBack.setPadding(20, 20, 40, 20);
        tvBack.setOnClickListener(v -> finish());
        header.addView(tvBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Appeal Management");
        tvTitle.setTextSize(20f);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTypeface(null, Typeface.BOLD);
        header.addView(tvTitle);

        mainLayout.addView(header);

        ScrollView scrollView = new ScrollView(this);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(30, 30, 30, 30);
        scrollView.addView(container);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);
    }

    private void loadAppeals() {
        container.removeAllViews();
        db.collection("appeals")
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    container.removeAllViews();
                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No pending appeals.");
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(0, 100, 0, 0);
                        container.addView(empty);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        addAppealCard(doc);
                    }
                });
    }

    private void addAppealCard(QueryDocumentSnapshot doc) {
        String appealId = doc.getId();
        String userId = doc.getString("suspendedUserId");
        String userName = doc.getString("suspendedUserName");
        String reason = doc.getString("reason");
        String evidence = doc.getString("evidence");

        if (userName == null || userName.isEmpty()) userName = "Unknown User";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_white);
        card.setPadding(40, 40, 40, 40);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 30);
        card.setLayoutParams(params);
        card.setElevation(4f);

        TextView tvName = new TextView(this);
        tvName.setText(userName);
        tvName.setTextColor(Color.parseColor("#0F2645"));
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextSize(18f);
        card.addView(tvName);

        TextView tvReasonLabel = new TextView(this);
        tvReasonLabel.setText("APPEAL REASON");
        tvReasonLabel.setTextSize(10f);
        tvReasonLabel.setTextColor(Color.parseColor("#A8B4C4"));
        tvReasonLabel.setTypeface(null, Typeface.BOLD);
        tvReasonLabel.setLetterSpacing(0.1f);
        tvReasonLabel.setPadding(0, 24, 0, 8);
        card.addView(tvReasonLabel);

        TextView tvReason = new TextView(this);
        tvReason.setText(reason);
        tvReason.setTextColor(Color.parseColor("#0F2645"));
        tvReason.setTextSize(14f);
        card.addView(tvReason);

        if (evidence != null && !evidence.isEmpty()) {
            TextView tvEvidenceLabel = new TextView(this);
            tvEvidenceLabel.setText("ADDITIONAL EVIDENCE");
            tvEvidenceLabel.setTextSize(10f);
            tvEvidenceLabel.setTextColor(Color.parseColor("#A8B4C4"));
            tvEvidenceLabel.setTypeface(null, Typeface.BOLD);
            tvEvidenceLabel.setLetterSpacing(0.1f);
            tvEvidenceLabel.setPadding(0, 20, 0, 8);
            card.addView(tvEvidenceLabel);

            TextView tvEvidence = new TextView(this);
            tvEvidence.setText(evidence);
            tvEvidence.setTextColor(Color.parseColor("#0F2645"));
            tvEvidence.setTextSize(14f);
            card.addView(tvEvidence);
        }

        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divParams.setMargins(0, 30, 0, 30);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(Color.parseColor("#EEF1F5"));
        card.addView(divider);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setWeightSum(2f);

        Button btnApprove = new Button(this);
        btnApprove.setText("Approve");
        btnApprove.setBackgroundColor(Color.parseColor("#4DB6AC"));
        btnApprove.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(0, 110, 1f);
        approveParams.setMargins(0, 0, 10, 0);
        btnApprove.setLayoutParams(approveParams);
        btnApprove.setOnClickListener(v -> resolveAppeal(appealId, userId, true));
        btnRow.addView(btnApprove);

        Button btnReject = new Button(this);
        btnReject.setText("Reject");
        btnReject.setBackgroundColor(Color.parseColor("#E53935"));
        btnReject.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(0, 110, 1f);
        rejectParams.setMargins(10, 0, 0, 0);
        btnReject.setLayoutParams(rejectParams);
        btnReject.setOnClickListener(v -> resolveAppeal(appealId, userId, false));
        btnRow.addView(btnReject);

        card.addView(btnRow);
        container.addView(card);
    }

    private void resolveAppeal(String appealId, String userId, boolean approve) {
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Error: User ID is missing for this appeal.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> appealUpdate = new HashMap<>();
        appealUpdate.put("status", approve ? "APPROVED" : "REJECTED");
        appealUpdate.put("resolvedAt", System.currentTimeMillis());

        db.collection("appeals").document(appealId).update(appealUpdate)
                .addOnSuccessListener(aVoid -> {
                    if (approve) {
                        Map<String, Object> userUpdate = new HashMap<>();
                        userUpdate.put("isSuspended", false);
                        userUpdate.put("suspensionReason", "");
                        db.collection("Users").document(userId).update(userUpdate);
                    }
                    Toast.makeText(this, "Appeal " + (approve ? "approved" : "rejected"), Toast.LENGTH_SHORT).show();
                    loadAppeals();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
