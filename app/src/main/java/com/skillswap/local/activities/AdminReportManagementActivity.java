package com.skillswap.local.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import com.skillswap.local.R;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class AdminReportManagementActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        setupUI();
        loadReports();
    }

    private void setupUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F4F6F9"));

        // Header with Back Button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#E53935"));
        header.setPadding(20, 20, 20, 20);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBack = new TextView(this);
        tvBack.setText("←");
        tvBack.setTextSize(24f);
        tvBack.setTextColor(Color.WHITE);
        tvBack.setPadding(20, 20, 40, 20);
        tvBack.setOnClickListener(v -> finish());
        header.addView(tvBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Report Management");
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

    private void loadReports() {
        db.collection("Reports")
                .whereEqualTo("status", "OPEN")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    container.removeAllViews();
                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No open reports.");
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(0, 100, 0, 0);
                        container.addView(empty);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        addReportCard(doc);
                    }
                });
    }

    private void addReportCard(QueryDocumentSnapshot doc) {
        String reportId = doc.getId();
        String targetUserId = doc.getString("targetUserId");
        String targetUserName = doc.getString("targetUserName");
        String reporterName = doc.getString("reporterName");
        String reporterId = doc.getString("reporterId");
        if (reporterId == null) reporterId = doc.getString("reporterUid");
        String reason = doc.getString("reason");
        String details = doc.getString("details");

        if (targetUserName == null || targetUserName.isEmpty()) targetUserName = "Unknown User";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_white);
        card.setPadding(40, 40, 40, 40);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 30);
        card.setLayoutParams(params);
        card.setElevation(4f);

        TextView tvTarget = new TextView(this);
        tvTarget.setText("Reported: " + targetUserName);
        tvTarget.setTextColor(Color.parseColor("#0F2645"));
        tvTarget.setTypeface(null, Typeface.BOLD);
        tvTarget.setTextSize(18f);
        card.addView(tvTarget);

        TextView tvReporter = new TextView(this);
        if (reporterName == null || reporterName.isEmpty() || reporterName.equalsIgnoreCase("User")) {
            tvReporter.setText("By: Loading...");
            if (reporterId != null) {
                final TextView finalTvReporter = tvReporter;
                db.collection("Users").document(reporterId).get().addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String name = userDoc.getString("fullName");
                        finalTvReporter.setText("By: " + (name != null ? name : "Unknown"));
                    } else {
                        finalTvReporter.setText("By: Unknown User");
                    }
                });
            } else {
                tvReporter.setText("By: Unknown User");
            }
        } else {
            tvReporter.setText("By: " + reporterName);
        }
        tvReporter.setTextColor(Color.parseColor("#7A8B9A"));
        tvReporter.setTextSize(13f);
        card.addView(tvReporter);

        TextView tvReasonLabel = new TextView(this);
        tvReasonLabel.setText("REPORT REASON");
        tvReasonLabel.setTextSize(10f);
        tvReasonLabel.setTextColor(Color.parseColor("#A8B4C4"));
        tvReasonLabel.setTypeface(null, Typeface.BOLD);
        tvReasonLabel.setLetterSpacing(0.1f);
        tvReasonLabel.setPadding(0, 24, 0, 8);
        card.addView(tvReasonLabel);

        TextView tvReason = new TextView(this);
        tvReason.setText(reason);
        tvReason.setTextColor(Color.parseColor("#E53935"));
        tvReason.setTypeface(null, Typeface.BOLD);
        tvReason.setTextSize(14f);
        card.addView(tvReason);

        if (details != null && !details.isEmpty()) {
            TextView tvDetailsLabel = new TextView(this);
            tvDetailsLabel.setText("DETAILS");
            tvDetailsLabel.setTextSize(10f);
            tvDetailsLabel.setTextColor(Color.parseColor("#A8B4C4"));
            tvDetailsLabel.setTypeface(null, Typeface.BOLD);
            tvDetailsLabel.setLetterSpacing(0.1f);
            tvDetailsLabel.setPadding(0, 20, 0, 8);
            card.addView(tvDetailsLabel);

            TextView tvDetails = new TextView(this);
            tvDetails.setText(details);
            tvDetails.setTextColor(Color.parseColor("#0F2645"));
            tvDetails.setTextSize(14f);
            card.addView(tvDetails);
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

        Button btnSuspend = new Button(this);
        btnSuspend.setText("Suspend User");
        btnSuspend.setBackgroundColor(Color.parseColor("#E53935"));
        btnSuspend.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams suspendParams = new LinearLayout.LayoutParams(0, 110, 1f);
        suspendParams.setMargins(0, 0, 10, 0);
        btnSuspend.setLayoutParams(suspendParams);
        btnSuspend.setOnClickListener(v -> suspendUser(reportId, targetUserId, reason));
        btnRow.addView(btnSuspend);

        Button btnDismiss = new Button(this);
        btnDismiss.setText("Dismiss");
        btnDismiss.setBackgroundColor(Color.GRAY);
        btnDismiss.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(0, 110, 1f);
        dismissParams.setMargins(10, 0, 0, 0);
        btnDismiss.setLayoutParams(dismissParams);
        btnDismiss.setOnClickListener(v -> dismissReport(reportId));
        btnRow.addView(btnDismiss);

        card.addView(btnRow);
        container.addView(card);
    }

    private void suspendUser(String reportId, String userId, String reason) {
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("isSuspended", true);
        userUpdate.put("suspensionReason", "Suspended after admin review of report: " + reason);
        userUpdate.put("suspensionDate", System.currentTimeMillis());

        db.collection("Users").document(userId).update(userUpdate)
                .addOnSuccessListener(aVoid -> {
                    dismissReport(reportId);
                    Toast.makeText(this, "User suspended and report closed.", Toast.LENGTH_SHORT).show();
                });
    }

    private void dismissReport(String reportId) {
        db.collection("Reports").document(reportId).update("status", "RESOLVED")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Report resolved.", Toast.LENGTH_SHORT).show();
                    loadReports();
                });
    }
}
