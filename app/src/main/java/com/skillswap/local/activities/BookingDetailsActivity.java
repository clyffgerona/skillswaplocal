package com.skillswap.local.activities;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.skillswap.local.R;
import com.skillswaplocal.models.Reports;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookingDetailsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String bookingId;
    private String currentOtherUserName = "";
    private String currentUid;
    private String currentClientId;
    private int selectedRating = 5;
    private ListenerRegistration bookingListener;

    private TextView tvService, tvProviderName, tvDate, tvLocation, tvExchange, tvStatus, tvNote, tvCancellationReason;
    private ImageView btnBack;
    private TextView btnComplete, btnReject, btnCancelClient, btnDeclineProvider, btnApproveProvider;
    private TextView tvProofTitle, tvProofSubtitle;
    private Button btnUploadProof, btnReport;
    private ImageView ivProofImage;
    private LinearLayout actionLayout, proofLayout, reportLayout, reviewDisplayLayout, cancellationLayout;

    private Uri proofImageUri = null;

    private final ActivityResultLauncher<String> pickProofLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    proofImageUri = uri;
                    ivProofImage.setVisibility(View.VISIBLE);
                    Glide.with(this).load(uri).into(ivProofImage);
                    btnUploadProof.setText("Change Photo");
                    btnComplete.setAlpha(1.0f);
                    ivProofImage.setOnClickListener(v -> showFullScreenImage(uri));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_details);

        db = FirebaseFirestore.getInstance();
        bookingId = getIntent().getStringExtra("booking_id");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", "dykygrydm");
            config.put("api_key", "995585552577712");
            config.put("api_secret", "MrId8H8hwOzVVjjYr5_GNYqQZFw");
            MediaManager.init(this, config);
        } catch (Exception e) {}

        initViews();
        listenToBooking();

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        btnUploadProof.setOnClickListener(v -> pickProofLauncher.launch("image/*"));

        btnComplete.setOnClickListener(v -> {
            if (proofImageUri == null) {
                Toast.makeText(this, "Please attach a proof of work photo first.", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadProofToCloudinaryAndComplete();
        });

        btnReject.setOnClickListener(v -> updateStatus("CANCELLED"));
        btnCancelClient.setOnClickListener(v -> updateStatus("CANCELLED"));
        btnDeclineProvider.setOnClickListener(v -> updateStatus("REJECTED"));
        btnApproveProvider.setOnClickListener(v -> updateStatus("CONFIRMED"));

        btnReport.setOnClickListener(v -> showReportDialog());
    }

    private void showReportDialog() {
        Dialog reportDialog = new Dialog(this);
        reportDialog.setContentView(R.layout.dialog_report_user);
        if (reportDialog.getWindow() != null) {
            reportDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            reportDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTargetName = reportDialog.findViewById(R.id.tvReportTargetName);
        tvTargetName.setText("Reporting: " + currentOtherUserName);

        RadioGroup rgReason = reportDialog.findViewById(R.id.rgReportReason);
        EditText etDetails = reportDialog.findViewById(R.id.etReportDetails);
        Button btnCancel = reportDialog.findViewById(R.id.btnCancelReport);
        Button btnSubmit = reportDialog.findViewById(R.id.btnSubmitReport);

        btnCancel.setOnClickListener(v -> reportDialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            int selectedId = rgReason.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Please select a reason.", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton rb = reportDialog.findViewById(selectedId);
            final String reason = rb.getText().toString();
            final String details = etDetails.getText().toString().trim();

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting...");

            String name = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            final String reporterName = (name == null || name.isEmpty()) ? "User" : name;

            db.collection("Users").whereEqualTo("fullName", currentOtherUserName).limit(1).get()
                    .addOnSuccessListener(snapshots -> {
                        if (!snapshots.isEmpty()) {
                            DocumentSnapshot targetDoc = snapshots.getDocuments().get(0);
                            String targetUid = targetDoc.getId();

                            Reports report = new Reports(currentUid, reporterName, targetUid, currentOtherUserName, reason, details);
                            db.collection("Reports").add(report).addOnSuccessListener(ref -> {
                                
                                // Increment reportsCount and check for suspension if it's a No-Show
                                if (reason.contains("No-Show")) {
                                    db.collection("Users").document(targetUid).update("reportsCount", FieldValue.increment(1))
                                            .addOnSuccessListener(aVoid -> {
                                                db.collection("Users").document(targetUid).get().addOnSuccessListener(userDoc -> {
                                                    Long count = userDoc.getLong("reportsCount");
                                                    if (count != null && count >= 3) {
                                                        db.collection("Users").document(targetUid).update(
                                                                "isSuspended", true,
                                                                "suspensionReason", "Automatic suspension: 3 confirmed No-Show reports.",
                                                                "suspensionDate", System.currentTimeMillis()
                                                        );
                                                    }
                                                });
                                            });
                                }

                                Toast.makeText(this, "Report submitted successfully.", Toast.LENGTH_SHORT).show();
                                reportDialog.dismiss();
                            });
                        }
                    });
        });

        reportDialog.show();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvService = findViewById(R.id.tvService);
        tvProviderName = findViewById(R.id.tvProviderName);
        tvDate = findViewById(R.id.tvDate);
        tvLocation = findViewById(R.id.tvLocation);
        tvExchange = findViewById(R.id.tvExchange);
        tvStatus = findViewById(R.id.tvStatus);
        tvNote = findViewById(R.id.tvNote);
        tvCancellationReason = findViewById(R.id.tvCancellationReason);

        tvProofTitle = findViewById(R.id.tvProofTitle);
        tvProofSubtitle = findViewById(R.id.tvProofSubtitle);

        btnComplete = findViewById(R.id.btnComplete);
        btnReject = findViewById(R.id.btnReject);
        btnCancelClient = findViewById(R.id.btnCancelClient);
        btnDeclineProvider = findViewById(R.id.btnDeclineProvider);
        btnApproveProvider = findViewById(R.id.btnApproveProvider);
        btnUploadProof = findViewById(R.id.btnUploadProof);
        btnReport = findViewById(R.id.btnReport);

        ivProofImage = findViewById(R.id.ivProofImage);

        actionLayout = findViewById(R.id.actionLayout);
        proofLayout = findViewById(R.id.proofLayout);
        reportLayout = findViewById(R.id.reportLayout);
        reviewDisplayLayout = findViewById(R.id.reviewDisplayLayout);
        cancellationLayout = findViewById(R.id.cancellationLayout);
    }

    // -------------------------------------------------------------
    // FIX: 100f Radius for Wrap-Content Floating Badges!
    // -------------------------------------------------------------
    private void setStatusBadge(TextView tv, String bgColorHex) {
        tv.setTextColor(Color.WHITE);
        tv.setGravity(android.view.Gravity.CENTER);
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);

        // Because the Details screen badge is wrap_content and floating,
        // 100f creates the perfect, beautifully rounded pill!
        pill.setCornerRadius(100f);
        pill.setColor(Color.parseColor(bgColorHex));
        tv.setBackground(pill);

        float density = getResources().getDisplayMetrics().density;
        tv.setPadding((int)(16 * density), (int)(6 * density), (int)(16 * density), (int)(6 * density));
    }

    private void listenToBooking() {
        if (bookingId == null) return;

        bookingListener = db.collection("Bookings").document(bookingId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;

                    String providerId = doc.getString("providerId");
                    currentClientId = doc.getString("clientId");
                    String clientName = doc.getString("clientName");
                    String providerName = doc.getString("providerName");
                    boolean isProvider = currentUid != null && currentUid.equals(providerId);

                    if (isProvider) {
                        currentOtherUserName = (clientName != null) ? clientName : "Client";
                    } else {
                        currentOtherUserName = (providerName != null) ? providerName : "Provider";
                    }

                    String service = doc.getString("service");
                    String date = doc.getString("dateTimeStr");
                    String loc = doc.getString("meetingLocation");
                    String exch = doc.getString("exchangeMode");
                    String note = doc.getString("note");

                    String dbStatus = doc.getString("status");
                    if (dbStatus == null) dbStatus = "PENDING";
                    String uiStatus = dbStatus.trim().toUpperCase();

                    if (uiStatus.equals("PENDING")) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy · hh:mm a", Locale.getDefault());
                            Date bookingDate = sdf.parse(date);
                            if (bookingDate != null && new Date().after(bookingDate)) {
                                String reason = "Booking cancelled. The skill provider hasn't replied to your request in time.";
                                Map<String, Object> cancelUpdate = new HashMap<>();
                                cancelUpdate.put("status", "CANCELLED");
                                cancelUpdate.put("cancellationReason", reason);
                                db.collection("Bookings").document(bookingId).update(cancelUpdate);
                                return;
                            }
                        } catch (Exception e) {}
                    }

                    if (uiStatus.equals("CONFIRMED")) uiStatus = "APPROVED";
                    if (uiStatus.equals("REJECTED")) uiStatus = "DECLINED";

                    Long savedRatingLong = doc.getLong("givenRating");
                    int savedRating = savedRatingLong != null ? savedRatingLong.intValue() : 0;
                    String savedProofUrl = doc.getString("proofPhotoUrl");

                    tvService.setText(service);
                    tvProviderName.setText(currentOtherUserName);
                    tvDate.setText(date);
                    tvLocation.setText(loc);
                    tvExchange.setText(exch);
                    tvNote.setText(note != null && !note.isEmpty() ? note : "No additional notes provided.");
                    tvStatus.setText(uiStatus);

                    if (savedProofUrl != null && !savedProofUrl.isEmpty()) {
                        ivProofImage.setVisibility(View.VISIBLE);
                        Glide.with(this).load(savedProofUrl).into(ivProofImage);
                        final String finalUrl = savedProofUrl;
                        ivProofImage.setOnClickListener(v -> showFullScreenImage(finalUrl));
                    }

                    hideAllActionButtons();
                    actionLayout.setVisibility(View.GONE);
                    proofLayout.setVisibility(View.GONE);
                    reportLayout.setVisibility(View.GONE);
                    reviewDisplayLayout.setVisibility(View.GONE);
                    cancellationLayout.setVisibility(View.GONE);

                    switch (uiStatus) {
                        case "PENDING":
                            setStatusBadge(tvStatus, "#FF8F00");
                            actionLayout.setVisibility(View.VISIBLE);

                            if (isProvider) {
                                btnDeclineProvider.setVisibility(View.VISIBLE);
                                btnApproveProvider.setVisibility(View.VISIBLE);
                            } else {
                                btnCancelClient.setVisibility(View.VISIBLE);
                            }
                            break;

                        case "APPROVED":
                            setStatusBadge(tvStatus, "#4DB6AC");
                            actionLayout.setVisibility(View.VISIBLE);

                            boolean timeArrived = false;
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy · hh:mm a", Locale.getDefault());
                                Date bookingDate = sdf.parse(date);
                                if (bookingDate != null && new Date().after(bookingDate)) {
                                    timeArrived = true;
                                }
                            } catch (Exception e) {}

                            if (timeArrived) {
                                proofLayout.setVisibility(View.VISIBLE);
                                btnComplete.setVisibility(View.VISIBLE);
                            } else {
                                proofLayout.setVisibility(View.GONE);
                                btnComplete.setVisibility(View.GONE);
                            }

                            btnReject.setVisibility(View.VISIBLE);
                            tvProofTitle.setText("Proof of Work");
                            tvProofSubtitle.setVisibility(View.VISIBLE);
                            btnUploadProof.setVisibility(View.VISIBLE);
                            break;

                        case "COMPLETED":
                            setStatusBadge(tvStatus, "#0F2645");
                            proofLayout.setVisibility(View.VISIBLE);
                            tvProofTitle.setText("Proof of Completed Work");
                            tvProofSubtitle.setVisibility(View.GONE);
                            btnUploadProof.setVisibility(View.GONE);

                            if (savedRating > 0) {
                                reviewDisplayLayout.setVisibility(View.VISIBLE);
                                setSavedStarsDisplay(savedRating);
                            }
                            break;

                        case "CANCELLED":
                            setStatusBadge(tvStatus, "#7A8B9A");
                            reportLayout.setVisibility(View.VISIBLE);

                            String reason = doc.getString("cancellationReason");
                            if (reason != null && !reason.isEmpty()) {
                                cancellationLayout.setVisibility(View.VISIBLE);
                                tvCancellationReason.setText(reason);
                            }
                            break;

                        case "DECLINED":
                            setStatusBadge(tvStatus, "#E53935");
                            break;
                    }
                });
    }

    private void hideAllActionButtons() {
        btnCancelClient.setVisibility(View.GONE);
        btnDeclineProvider.setVisibility(View.GONE);
        btnApproveProvider.setVisibility(View.GONE);
        btnReject.setVisibility(View.GONE);
        btnComplete.setVisibility(View.GONE);
    }

    private void showFullScreenImage(Object imageSource) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(Color.BLACK);

        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(imageSource).into(imageView);

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

    private void setSavedStarsDisplay(int rating) {
        TextView[] displayStars = {
                findViewById(R.id.tvDisplayStar1), findViewById(R.id.tvDisplayStar2),
                findViewById(R.id.tvDisplayStar3), findViewById(R.id.tvDisplayStar4),
                findViewById(R.id.tvDisplayStar5)
        };
        for (int i = 0; i < displayStars.length; i++) {
            displayStars[i].setTextColor(i < rating
                    ? Color.parseColor("#FFB300")
                    : Color.parseColor("#E2E8EF"));
        }
    }

    private void uploadProofToCloudinaryAndComplete() {
        btnComplete.setText("Uploading Proof...");
        btnComplete.setEnabled(false);

        String customId = "proof_" + bookingId + "_" + System.currentTimeMillis();

        MediaManager.get().upload(proofImageUri)
                .option("folder", "SkillSwap Local/Image Proof")
                .option("public_id", customId)
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override public void onSuccess(String requestId, Map resultData) {
                        String proofUrl = (String) resultData.get("secure_url");
                        db.collection("Bookings").document(bookingId)
                                .update("proofPhotoUrl", proofUrl)
                                .addOnSuccessListener(aVoid ->
                                        runOnUiThread(() -> updateStatus("COMPLETED")));
                    }
                    @Override public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> {
                            btnComplete.setText("Mark as Complete");
                            btnComplete.setEnabled(true);
                            Toast.makeText(BookingDetailsActivity.this,
                                    "Upload Failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                        });
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void updateStatus(String newStatus) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);

        if (newStatus.equals("CONFIRMED")) {
            updates.put("viewedByClient", false);
        }

        db.collection("Bookings").document(bookingId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (newStatus.equals("COMPLETED")) {
                        showRatingReviewDialog(currentOtherUserName);
                    } else {
                        Toast.makeText(this, "Booking marked as " + newStatus, Toast.LENGTH_SHORT).show();
                        finish();
                        overridePendingTransition(0, 0);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update.", Toast.LENGTH_SHORT).show());
    }

    private void showRatingReviewDialog(String providerName) {
        Dialog reviewDialog = new Dialog(this);
        reviewDialog.setContentView(R.layout.dialog_rating_review);
        if (reviewDialog.getWindow() != null) {
            reviewDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            reviewDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvName = reviewDialog.findViewById(R.id.tvRatingProviderName);
        Button btnSubmit = reviewDialog.findViewById(R.id.btnSubmitReview);
        EditText etReviewText = reviewDialog.findViewById(R.id.etReviewText);
        if (tvName != null) tvName.setText("Rate " + providerName);

        TextView[] stars = {
                reviewDialog.findViewById(R.id.star1), reviewDialog.findViewById(R.id.star2),
                reviewDialog.findViewById(R.id.star3), reviewDialog.findViewById(R.id.star4),
                reviewDialog.findViewById(R.id.star5)
        };
        for (int i = 0; i < stars.length; i++) {
            final int starIndex = i;
            stars[i].setOnClickListener(v -> {
                selectedRating = starIndex + 1;
                for (int j = 0; j < stars.length; j++) {
                    stars[j].setTextColor(j <= starIndex
                            ? Color.parseColor("#FFB300")
                            : Color.parseColor("#E2E8EF"));
                }
            });
        }

        TextView[] tags = {
                reviewDialog.findViewById(R.id.tagPunctual),
                reviewDialog.findViewById(R.id.tagKnowledgeable),
                reviewDialog.findViewById(R.id.tagFriendly)
        };
        for (TextView tag : tags) {
            tag.setOnClickListener(v -> {
                if (tag.getCurrentTextColor() == Color.WHITE) {
                    tag.setBackgroundResource(R.drawable.bg_category_chip);
                    tag.setTextColor(Color.parseColor("#7A8B9A"));
                } else {
                    tag.setBackgroundResource(R.drawable.bg_avail_chip_active);
                    tag.setTextColor(Color.WHITE);
                }
            });
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String reviewText = etReviewText != null ? etReviewText.getText().toString().trim() : "";
                btnSubmit.setText("Saving...");
                btnSubmit.setEnabled(false);

                db.collection("Users").whereEqualTo("fullName", providerName).limit(1).get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            if (!queryDocumentSnapshots.isEmpty()) {
                                DocumentSnapshot providerDoc = queryDocumentSnapshots.getDocuments().get(0);
                                String providerUid = providerDoc.getId();
                                Double currentRatingObj = providerDoc.getDouble("rating");
                                Long totalSessionsObj = providerDoc.getLong("totalSessions");

                                double currentRating = currentRatingObj != null ? currentRatingObj : 5.0;
                                long totalSessions = totalSessionsObj != null ? totalSessionsObj : 0;

                                double newRating = Math.round(((currentRating * totalSessions) + selectedRating) / (totalSessions + 1) * 10.0) / 10.0;

                                Map<String, Object> providerUpdates = new HashMap<>();
                                providerUpdates.put("rating", newRating);
                                providerUpdates.put("totalSessions", FieldValue.increment(1));
                                providerUpdates.put("showUpCount", FieldValue.increment(1));

                                // Logic for automatic suspension on "No-Show" report
                                boolean isNoShow = reviewText.toLowerCase().contains("no-show") || reviewText.toLowerCase().contains("didn't show up");
                                if (isNoShow) {
                                    providerUpdates.put("isSuspended", true);
                                    providerUpdates.put("suspensionReason", "Reported for No-Show by " + (FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "a user"));
                                    providerUpdates.put("suspensionDate", System.currentTimeMillis());
                                }

                                db.collection("Users").document(providerUid).update(providerUpdates)
                                        .addOnSuccessListener(aVoid -> {

                                            if (currentClientId != null && !currentClientId.equals(providerUid)) {
                                                db.collection("Users").document(currentClientId).update(
                                                        "totalSessions", FieldValue.increment(1),
                                                        "showUpCount", FieldValue.increment(1)
                                                );
                                            }

                                            db.collection("Bookings").document(bookingId)
                                                    .update("givenRating", selectedRating)
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        Toast.makeText(this, "Review submitted! Ratings updated.", Toast.LENGTH_SHORT).show();
                                                        reviewDialog.dismiss();
                                                        finish();
                                                        overridePendingTransition(0, 0);
                                                    });
                                        });
                            } else {
                                Toast.makeText(this, "Error: User not found.", Toast.LENGTH_SHORT).show();
                                reviewDialog.dismiss();
                                finish();
                            }
                        });
            });
        }

        reviewDialog.setCancelable(false);
        reviewDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookingListener != null) bookingListener.remove();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}