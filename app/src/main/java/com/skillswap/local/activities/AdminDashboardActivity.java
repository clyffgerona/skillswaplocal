package com.skillswap.local.activities;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.skillswap.local.R;

public class AdminDashboardActivity extends AppCompatActivity {

    private LinearLayout container;
    private FirebaseFirestore db;
    private Button btnAdminLogout;
    private View btnManageAppeals, btnManageReports;
    private TextView tvPendingCount, tvAppealsCount, tvReportsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        container      = findViewById(R.id.llPendingUsersContainer);
        btnAdminLogout = findViewById(R.id.btnAdminLogout);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvAppealsCount = findViewById(R.id.tvAppealsCount);
        tvReportsCount = findViewById(R.id.tvReportsCount);
        btnManageAppeals = findViewById(R.id.btnManageAppeals);
        btnManageReports = findViewById(R.id.btnManageReports);

        btnManageAppeals.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminAppealManagementActivity.class));
        });

        btnManageReports.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminReportManagementActivity.class));
        });

        db             = FirebaseFirestore.getInstance();

        btnAdminLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadPendingUsers();
        loadAppealStats();
        loadReportStats();
    }

    private void loadReportStats() {
        db.collection("Reports").whereEqualTo("status", "OPEN")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots != null) {
                        tvReportsCount.setText(snapshots.size() + " reports");
                    }
                });
    }

    private void loadAppealStats() {
        db.collection("appeals")
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots != null) {
                        int count = snapshots.size();
                        if (tvAppealsCount != null) {
                            tvAppealsCount.setText(count + " appeals");
                        }
                    }
                });
    }

    private void loadPendingUsers() {
        db.collection("Users")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    container.removeAllViews();
                    int count = snapshots != null ? snapshots.size() : 0;

                    if (tvPendingCount != null) {
                        tvPendingCount.setText(count + " pending");
                    }

                    if (count == 0) {
                        TextView tvEmpty = new TextView(this);
                        tvEmpty.setText("✓  No pending approvals");
                        tvEmpty.setTextSize(15f);
                        tvEmpty.setTextColor(Color.parseColor("#7A8B9A"));
                        tvEmpty.setGravity(Gravity.CENTER);
                        tvEmpty.setPadding(0, 80, 0, 0);
                        container.addView(tvEmpty);
                        return;
                    }

                    for (QueryDocumentSnapshot document : snapshots) {
                        String userId        = document.getId();
                        String name          = document.getString("fullName");
                        String email         = document.getString("email");
                        String idUrl         = document.getString("idPhotoUrl");
                        String selfieUrl     = document.getString("selfiePhotoUrl");

                        addUserCard(
                                userId,
                                name  != null ? name  : "Unknown Name",
                                email != null ? email : "Unknown Email",
                                idUrl,
                                selfieUrl
                        );
                    }
                });
    }

    private void addUserCard(String userId, String name, String email,
                             String idUrl, String selfieUrl) {

        // ── Card wrapper ────────────────────────────────────────────────────
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#FFFFFF"));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);
        card.setElevation(10f);

        // Teal accent top bar
        View topBar = new View(this);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6));
        topBar.setBackgroundColor(Color.parseColor("#4DB6AC"));
        card.addView(topBar);

        // ── Card body ───────────────────────────────────────────────────────
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(40, 36, 40, 36);
        card.addView(body);

        // Name
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(18f);
        tvName.setTextColor(Color.parseColor("#0F2645"));
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setPadding(0, 0, 0, 4);
        body.addView(tvName);

        // Email
        TextView tvEmail = new TextView(this);
        tvEmail.setText(email);
        tvEmail.setTextSize(13f);
        tvEmail.setTextColor(Color.parseColor("#7A8B9A"));
        tvEmail.setPadding(0, 0, 0, 28);
        body.addView(tvEmail);

        // ── Photos ──────────────────────────────────────────────────────────
        if (idUrl != null || selfieUrl != null) {

            TextView tvPhotosLabel = new TextView(this);
            tvPhotosLabel.setText("VERIFICATION PHOTOS");
            tvPhotosLabel.setTextSize(10f);
            tvPhotosLabel.setTextColor(Color.parseColor("#A8B4C4"));
            tvPhotosLabel.setTypeface(null, Typeface.BOLD);
            tvPhotosLabel.setLetterSpacing(0.12f);
            tvPhotosLabel.setPadding(0, 0, 0, 14);
            body.addView(tvPhotosLabel);

            LinearLayout photoRow = new LinearLayout(this);
            photoRow.setOrientation(LinearLayout.HORIZONTAL);
            photoRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            photoRow.setPadding(0, 0, 0, 28);

            if (idUrl != null) {
                photoRow.addView(buildPhotoColumn("Valid ID", idUrl, true));
            }

            if (idUrl != null && selfieUrl != null) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(24,
                        LinearLayout.LayoutParams.MATCH_PARENT));
                photoRow.addView(spacer);
            }

            if (selfieUrl != null) {
                photoRow.addView(buildPhotoColumn("Selfie", selfieUrl, false));
            }

            body.addView(photoRow);
        }

        // Divider
        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divParams.setMargins(0, 0, 0, 24);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(Color.parseColor("#EEF1F5"));
        body.addView(divider);

        // ── Buttons ─────────────────────────────────────────────────────────
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setWeightSum(2f);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnApprove = new Button(this);
        btnApprove.setText("✓  Approve");
        btnApprove.setBackgroundColor(Color.parseColor("#4DB6AC"));
        btnApprove.setTextColor(Color.WHITE);
        btnApprove.setTextSize(13f);
        btnApprove.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        approveParams.setMargins(0, 0, 12, 0);
        btnApprove.setLayoutParams(approveParams);
        btnApprove.setElevation(4f);

        Button btnDecline = new Button(this);
        btnDecline.setText("✕  Decline");
        btnDecline.setBackgroundColor(Color.parseColor("#E53935"));
        btnDecline.setTextColor(Color.WHITE);
        btnDecline.setTextSize(13f);
        btnDecline.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        declineParams.setMargins(12, 0, 0, 0);
        btnDecline.setLayoutParams(declineParams);
        btnDecline.setElevation(4f);

        btnApprove.setOnClickListener(v -> updateStatus(userId, "approved"));
        btnDecline.setOnClickListener(v -> updateStatus(userId, "rejected"));

        btnRow.addView(btnApprove);
        btnRow.addView(btnDecline);
        body.addView(btnRow);

        container.addView(card);
    }

    /**
     * FIXED: Uses Glide to fetch Cloudinary URL instead of crashing on Base64
     */
    private LinearLayout buildPhotoColumn(String label, String url, boolean isIdPhoto) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, isIdPhoto ? 1.6f : 1f));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setTextColor(Color.parseColor("#7A8B9A"));
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setPadding(0, 0, 0, 8);
        col.addView(tvLabel);

        ImageView iv = new ImageView(this);
        float density = getResources().getDisplayMetrics().density;
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(120 * density)));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Color.parseColor("#EEF1F5"));

        try {
            Glide.with(this).load(url).into(iv);
            iv.setOnClickListener(v -> showFullScreenImage(url));
        } catch (Exception e) {
            iv.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        col.addView(iv);
        return col;
    }

    private void showFullScreenImage(String imageUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(Color.BLACK);

        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(imageUrl).into(imageView);

        TextView btnClose = new TextView(this);
        btnClose.setText("X");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(24f);
        btnClose.setTypeface(null, android.graphics.Typeface.BOLD);
        btnClose.setPadding(40, 40, 40, 40);

        RelativeLayout.LayoutParams btnParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        btnClose.setLayoutParams(btnParams);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        layout.addView(imageView);
        layout.addView(btnClose);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void updateStatus(String userId, String newStatus) {
        db.collection("Users").document(userId)
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    String msg = newStatus.equals("approved") ? "User approved!" : "User rejected.";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    loadPendingUsers(); // Refresh the list
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error updating status.", Toast.LENGTH_SHORT).show());
    }
}