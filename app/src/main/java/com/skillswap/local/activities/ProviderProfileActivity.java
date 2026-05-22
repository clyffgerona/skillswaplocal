package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.skillswap.local.R;
import java.util.ArrayList;
import java.util.List;

public class ProviderProfileActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView tvInitials, tvName, tvRole, tvAbout, tvSkillDesc;
    private TextView tvRating, tvSessions, tvShowUp;
    private TextView tvSwapBadge, tvCashBadge;
    private ImageView ivAvatar;
    private LinearLayout llTags, llAvailability, llVerified, contentLayout;
    private ProgressBar loadingSpinner;

    private static final String[] DAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_profile);

        db = FirebaseFirestore.getInstance();
        initViews();

        String providerName = getIntent().getStringExtra("provider_name");

        float intentRating = getIntent().getFloatExtra("provider_rating", 5.0f);
        int intentSessions = getIntent().getIntExtra("provider_sessions", 0);
        int intentShowUp = getIntent().getIntExtra("provider_showup", 100);

        if (providerName == null || providerName.isEmpty()) providerName = "Unknown Provider";

        loadingSpinner.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        tvName.setText(providerName);
        tvInitials.setText(getInitials(providerName));

        // --- NEW: If 0 sessions, show "N/A" for intents ---
        tvRating.setText(intentSessions > 0 ? String.valueOf(intentRating) : "N/A");
        tvSessions.setText(String.valueOf(intentSessions));
        tvShowUp.setText(intentSessions > 0 ? (intentShowUp + "%") : "N/A");

        fetchProviderDataFromFirebase(providerName);
        setupButtons(providerName);
        setupBottomNav();
    }

    private void initViews() {
        tvInitials = findViewById(R.id.tvProviderInitials);
        tvName = findViewById(R.id.tvProviderName);
        tvRole = findViewById(R.id.tvProviderRole);
        tvAbout = findViewById(R.id.tvAbout);
        tvSkillDesc = findViewById(R.id.tvSkillDesc);
        tvRating = findViewById(R.id.tvRating);
        tvSessions = findViewById(R.id.tvSessions);
        tvShowUp = findViewById(R.id.tvShowUp);
        tvSwapBadge = findViewById(R.id.tvSwapBadge);
        tvCashBadge = findViewById(R.id.tvCashBadge);
        ivAvatar = findViewById(R.id.ivProviderAvatar);
        llTags = findViewById(R.id.llTags);
        llAvailability = findViewById(R.id.llAvailability);
        llVerified = findViewById(R.id.llVerified);

        contentLayout = findViewById(R.id.contentLayout);
        loadingSpinner = findViewById(R.id.loadingSpinner);
    }

    private void fetchProviderDataFromFirebase(String providerName) {
        db.collection("Users").whereEqualTo("fullName", providerName).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);

                        Double ratingObj = doc.getDouble("rating");
                        Long sessionsObj = doc.getLong("totalSessions");
                        Long showUpObj = doc.getLong("showUpCount");

                        float dbRating = ratingObj != null ? ratingObj.floatValue() : 5.0f;
                        int dbSessions = sessionsObj != null ? sessionsObj.intValue() : 0;
                        int dbShowUpCount = showUpObj != null ? showUpObj.intValue() : 0;
                        int dbShowUpPercent = dbSessions > 0 ? (int) (((float) dbShowUpCount / dbSessions) * 100f) : 100;

                        // --- NEW: If 0 sessions, show "N/A" for database reads ---
                        tvRating.setText(dbSessions > 0 ? String.valueOf(dbRating) : "N/A");
                        tvSessions.setText(String.valueOf(dbSessions));
                        tvShowUp.setText(dbSessions > 0 ? (dbShowUpPercent + "%") : "N/A");

                        String bio = doc.getString("bio");
                        tvAbout.setText(bio != null && !bio.isEmpty() ? bio : "This provider hasn't written a bio yet.");

                        String skillDesc = doc.getString("skillDescription");
                        tvSkillDesc.setText(skillDesc != null && !skillDesc.trim().isEmpty() ? skillDesc : "No specific service details provided.");

                        String location = doc.getString("location");
                        tvRole.setText(location != null && !location.isEmpty() ? "Provider · " + location : "Local Provider");

                        String photoUrl = doc.getString("profilePhotoUrl");
                        if (photoUrl == null || photoUrl.isEmpty()) photoUrl = doc.getString("selfiePhotoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ivAvatar.setVisibility(View.VISIBLE);
                            tvInitials.setVisibility(View.GONE);

                            if (!isFinishing() && !isDestroyed()) {
                                Glide.with(ProviderProfileActivity.this)
                                        .load(photoUrl)
                                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                                        .into(ivAvatar);
                            }
                        }

                        String status = doc.getString("status");
                        llVerified.setVisibility(status != null && status.equalsIgnoreCase("approved") ? View.VISIBLE : View.GONE);

                        Boolean acceptsSwap = doc.getBoolean("acceptsSkillSwap");
                        Boolean acceptsCash = doc.getBoolean("acceptsCash");
                        String priceRange = doc.getString("priceRange");

                        tvSwapBadge.setVisibility((acceptsSwap != null && acceptsSwap) ? View.VISIBLE : View.GONE);
                        if (acceptsCash != null && acceptsCash) {
                            tvCashBadge.setVisibility(View.VISIBLE);
                            tvCashBadge.setText(priceRange != null && !priceRange.isEmpty() ? "Cash " + priceRange : "Cash Exchange");
                        } else {
                            tvCashBadge.setVisibility(View.GONE);
                        }

                        buildTags((List<String>) doc.get("skillsOffered"));
                        buildAvailabilityChips((List<String>) doc.get("availability"));

                        loadingSpinner.setVisibility(View.GONE);
                        contentLayout.setVisibility(View.VISIBLE);
                    } else {
                        loadingSpinner.setVisibility(View.GONE);
                        contentLayout.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Failed to load provider profile.", Toast.LENGTH_SHORT).show();
                });
    }

    private void buildTags(List<String> skills) {
        llTags.removeAllViews();
        if (skills == null || skills.isEmpty()) return;
        for (String skill : skills) {
            TextView tv = new TextView(this);
            tv.setText(skill.trim());
            tv.setTextColor(getColor(R.color.mint_dark));
            tv.setTextSize(9f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setBackgroundResource(R.drawable.bg_tag_chip);
            tv.setPadding(dp(7), dp(3), dp(7), dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(5));
            tv.setLayoutParams(lp);
            llTags.addView(tv);
        }
    }

    private void buildAvailabilityChips(List<String> activeDays) {
        llAvailability.removeAllViews();
        if (activeDays == null) activeDays = new ArrayList<>();
        for (String dayName : DAY_LABELS) {
            TextView chip = new TextView(this);
            chip.setText(dayName);
            chip.setTextSize(10f);
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(5));
            chip.setLayoutParams(lp);
            chip.setMinWidth(dp(36));
            chip.setPadding(dp(8), dp(4), dp(8), dp(4));

            if (activeDays.contains(dayName)) {
                chip.setBackgroundResource(R.drawable.bg_avail_chip_active);
                chip.setTextColor(getColor(R.color.white));
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                chip.setBackgroundResource(R.drawable.bg_avail_chip);
                chip.setTextColor(getColor(R.color.text_secondary));
            }
            llAvailability.addView(chip);
        }
    }

    private void setupButtons(String providerName) {
        findViewById(R.id.btnBook).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("provider_name", providerName);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });
    }

    private void setupBottomNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> { Intent intent = new Intent(this, HomeActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
        findViewById(R.id.navBookings).setOnClickListener(v -> { Intent intent = new Intent(this, MyBookingsActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
        findViewById(R.id.navMessages).setOnClickListener(v -> { Intent intent = new Intent(this, InboxActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
        findViewById(R.id.navProfile).setOnClickListener(v -> { Intent intent = new Intent(this, UserActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String getInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty()) sb.append(p.charAt(0));
        return sb.toString().toUpperCase();
    }
}