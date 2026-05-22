package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.skillswap.local.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private EditText etFullName, etBio, etLocation, etSkillsOffered, etSkillDescription, etPriceRange;
    private TextView tvAvatarInitials, btnBack, btnSave, tvChangePfp;

    private TextView tvMyRating, tvMySessions, tvMyShowUp;
    private Button btnAppeal;

    private ImageView ivAvatar;
    private Button btnLogout;
    private CheckBox cbSkillSwap, cbCash, cbDiscoverable;
    private final List<String> selectedDays = new ArrayList<>();
    private Bitmap newProfileBitmap = null;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        newProfileBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        Glide.with(this).load(newProfileBitmap).apply(RequestOptions.bitmapTransform(new CircleCrop())).into(ivAvatar);
                        ivAvatar.setVisibility(View.VISIBLE);
                        tvAvatarInitials.setVisibility(View.GONE);
                    } catch (IOException e) { Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show(); }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", "dykygrydm");
            config.put("api_key", "995585552577712");
            config.put("api_secret", "MrId8H8hwOzVVjjYr5_GNYqQZFw");
            MediaManager.init(this, config);
        } catch (Exception e) {}

        initViews();
        loadUserData();

        cbDiscoverable.setOnClickListener(v -> {
            if (cbDiscoverable.isChecked() && !isProfileComplete()) {
                cbDiscoverable.setChecked(false);
                Toast.makeText(this, "Please complete your Bio, Location, Skills, Service Details, Availability, and Payment methods to be discoverable.", Toast.LENGTH_LONG).show();
            }
        });

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        btnSave.setOnClickListener(v -> saveProfileToFirestore());

        ivAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        tvAvatarInitials.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        tvChangePfp.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(UserActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        setupDayChips();
        setupBottomNav();
        startBadgeListeners();
    }

    private void initViews() {
        btnBack            = findViewById(R.id.btnBack);
        btnSave            = findViewById(R.id.btnSave);
        tvAvatarInitials   = findViewById(R.id.tvAvatarInitials);
        tvChangePfp        = findViewById(R.id.tvChangePfp);
        ivAvatar           = findViewById(R.id.ivAvatar);
        btnLogout          = findViewById(R.id.btnLogout);

        etFullName         = findViewById(R.id.etFullName);
        etBio              = findViewById(R.id.etBio);
        etLocation         = findViewById(R.id.etLocation);
        etSkillsOffered    = findViewById(R.id.etSkillsOffered);
        etSkillDescription = findViewById(R.id.etSkillDescription);
        etPriceRange       = findViewById(R.id.etPriceRange);

        cbSkillSwap        = findViewById(R.id.cbSkillSwap);
        cbCash             = findViewById(R.id.cbCash);
        cbDiscoverable     = findViewById(R.id.cbDiscoverable);

        tvMyRating         = findViewById(R.id.tvMyRating);
        tvMySessions       = findViewById(R.id.tvMySessions);
        tvMyShowUp         = findViewById(R.id.tvMyShowUp);

        btnAppeal = new Button(this);
        btnAppeal.setText("Submit Appeal");
        btnAppeal.setBackgroundColor(Color.parseColor("#E53935"));
        btnAppeal.setTextColor(Color.WHITE);
        btnAppeal.setVisibility(View.GONE);
        btnAppeal.setOnClickListener(v -> startActivity(new Intent(this, AppealActivity.class)));
        ((LinearLayout)btnLogout.getParent()).addView(btnAppeal, ((LinearLayout)btnLogout.getParent()).indexOfChild(btnLogout));

        tvAvatarInitials.setText("");
        etFullName.setEnabled(false);
        etFullName.setAlpha(0.6f);
    }

    private boolean isProfileComplete() {
        String bio = etBio.getText().toString().trim();
        String loc = etLocation.getText().toString().trim();
        String skills = etSkillsOffered.getText().toString().trim();
        String skillDesc = etSkillDescription.getText().toString().trim();
        boolean hasPayment = cbSkillSwap.isChecked() || cbCash.isChecked();
        boolean hasDays = !selectedDays.isEmpty();

        return !bio.isEmpty() && !loc.isEmpty() && !skills.isEmpty() && !skillDesc.isEmpty() && hasPayment && hasDays;
    }

    private void loadUserData() {
        FirebaseUser fbUser = mAuth.getCurrentUser();
        if (fbUser == null) return;

        db.collection("Users").document(fbUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {

                        String rawName = doc.getString("fullName");
                        if (rawName == null) rawName = doc.getString("name");
                        if (rawName != null) {
                            String fixedName = formatOldNames(rawName);
                            etFullName.setText(fixedName);
                            tvAvatarInitials.setText(getInitials(fixedName));
                        }

                        Double ratingObj = doc.getDouble("rating");
                        Long sessionsObj = doc.getLong("totalSessions");
                        Long showUpObj = doc.getLong("showUpCount");

                        float dbRating = ratingObj != null ? ratingObj.floatValue() : 5.0f;
                        int dbSessions = sessionsObj != null ? sessionsObj.intValue() : 0;
                        int dbShowUpCount = showUpObj != null ? showUpObj.intValue() : 0;
                        int dbShowUpPercent = dbSessions > 0 ? (int) (((float) dbShowUpCount / dbSessions) * 100f) : 100;

                        // --- NEW: If 0 sessions, show "N/A" for database reads ---
                        if (tvMyRating != null) tvMyRating.setText(dbSessions > 0 ? String.valueOf(dbRating) : "N/A");
                        if (tvMySessions != null) tvMySessions.setText(String.valueOf(dbSessions));
                        if (tvMyShowUp != null) tvMyShowUp.setText(dbSessions > 0 ? (dbShowUpPercent + "%") : "N/A");

                        Boolean isSuspended = doc.getBoolean("isSuspended");
                        if (isSuspended != null && isSuspended) {
                            btnAppeal.setVisibility(View.VISIBLE);
                        } else {
                            btnAppeal.setVisibility(View.GONE);
                        }

                        etBio.setText(doc.getString("bio"));
                        etLocation.setText(doc.getString("location"));
                        etPriceRange.setText(doc.getString("priceRange"));
                        etSkillDescription.setText(doc.getString("skillDescription"));

                        Boolean isSkillSwap = doc.getBoolean("acceptsSkillSwap");
                        Boolean isCash = doc.getBoolean("acceptsCash");
                        cbSkillSwap.setChecked(isSkillSwap != null && isSkillSwap);
                        cbCash.setChecked(isCash != null && isCash);

                        Boolean isDiscoverable = doc.getBoolean("isDiscoverable");
                        cbDiscoverable.setChecked(isDiscoverable == null || isDiscoverable);

                        Object skillsObj = doc.get("skillsOffered");
                        if (skillsObj instanceof List) {
                            etSkillsOffered.setText(String.join(", ", (List<String>) skillsObj));
                        } else if (skillsObj instanceof String) {
                            etSkillsOffered.setText((String) skillsObj);
                        }

                        Object daysObj = doc.get("availability");
                        if (daysObj instanceof List) {
                            selectedDays.addAll((List<String>) daysObj);
                        } else if (daysObj instanceof String) {
                            String[] arr = ((String) daysObj).split(",");
                            for (String d : arr) selectedDays.add(d.trim());
                        }
                        updateChipVisuals();

                        String photoUrl = doc.getString("profilePhotoUrl");
                        if (photoUrl == null || photoUrl.isEmpty()) photoUrl = doc.getString("selfiePhotoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ivAvatar.setVisibility(View.VISIBLE); tvAvatarInitials.setVisibility(View.GONE);
                            Glide.with(this).load(photoUrl).apply(RequestOptions.bitmapTransform(new CircleCrop())).into(ivAvatar);
                        }
                    }
                });
    }

    private void saveProfileToFirestore() {
        FirebaseUser fbUser = mAuth.getCurrentUser();
        if (fbUser == null) return;

        String bio = etBio.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String skillsRaw = etSkillsOffered.getText().toString().trim();
        String skillDesc = etSkillDescription.getText().toString().trim();
        String priceRange = etPriceRange.getText().toString().trim();

        List<String> skillsList = new ArrayList<>();
        if (!skillsRaw.isEmpty()) skillsList = new ArrayList<>(Arrays.asList(skillsRaw.split("\\s*,\\s*")));

        boolean isDiscoverable = cbDiscoverable.isChecked();
        if (isDiscoverable && !isProfileComplete()) {
            isDiscoverable = false;
            cbDiscoverable.setChecked(false);
            Toast.makeText(this, "Profile incomplete. Discoverability automatically turned off.", Toast.LENGTH_LONG).show();
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName",         etFullName.getText().toString().trim());
        updates.put("bio",              bio);
        updates.put("location",         location);
        updates.put("skillsOffered",    skillsList);
        updates.put("skillDescription", skillDesc);
        updates.put("availability",     selectedDays);
        updates.put("acceptsSkillSwap", cbSkillSwap.isChecked());
        updates.put("acceptsCash",      cbCash.isChecked());
        updates.put("priceRange",       priceRange);
        updates.put("isDiscoverable",   isDiscoverable);

        btnSave.setEnabled(false); btnSave.setText("Saving...");

        if (newProfileBitmap != null) uploadImageToCloudinaryAndSave(updates, fbUser);
        else saveDataToDatabase(updates, fbUser);
    }

    private void uploadImageToCloudinaryAndSave(Map<String, Object> updates, FirebaseUser fbUser) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newProfileBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        String customPublicId = "profile_" + fbUser.getUid();

        MediaManager.get().upload(imageBytes)
                .option("folder", "SkillSwap Local/Profile Pictures")
                .option("public_id", customPublicId)
                .option("overwrite", true)
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override public void onSuccess(String requestId, Map resultData) {
                        updates.put("profilePhotoUrl", (String) resultData.get("secure_url"));
                        runOnUiThread(() -> saveDataToDatabase(updates, fbUser));
                    }
                    @Override public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> { Toast.makeText(UserActivity.this, "Failed to upload image.", Toast.LENGTH_SHORT).show(); btnSave.setEnabled(true); btnSave.setText("Save"); });
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void saveDataToDatabase(Map<String, Object> updates, FirebaseUser fbUser) {
        db.collection("Users").document(fbUser.getUid()).set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save profile. Check connection.", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                });
    }

    private void setupDayChips() {
        int[] chipIds = {R.id.chipMon, R.id.chipTue, R.id.chipWed, R.id.chipThu, R.id.chipFri, R.id.chipSat, R.id.chipSun};
        for (int id : chipIds) {
            TextView chip = findViewById(id);
            chip.setOnClickListener(v -> { String day = chip.getText().toString(); if (selectedDays.contains(day)) selectedDays.remove(day); else selectedDays.add(day); updateChipVisuals(); });
        }
    }

    private void updateChipVisuals() {
        int[] chipIds = {R.id.chipMon, R.id.chipTue, R.id.chipWed, R.id.chipThu, R.id.chipFri, R.id.chipSat, R.id.chipSun};
        for (int id : chipIds) {
            TextView chip = findViewById(id);
            if (selectedDays.contains(chip.getText().toString())) { chip.setBackgroundColor(Color.parseColor("#4DB6AC")); chip.setTextColor(Color.WHITE); }
            else { chip.setBackgroundColor(Color.parseColor("#E5E9ED")); chip.setTextColor(Color.parseColor("#7A8B9A")); }
        }
    }

    private String formatOldNames(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return "Unknown Provider";

        if (rawName.contains(".")) return capitalizeWords(rawName);

        String[] words = rawName.trim().split("\\s+");
        if (words.length <= 2) return capitalizeWords(rawName);

        String first = capitalizeWords(words[0]);
        String middleInitial = String.valueOf(words[1].charAt(0)).toUpperCase() + ".";

        StringBuilder last = new StringBuilder();
        for (int i = 2; i < words.length; i++) {
            last.append(words[i]).append(" ");
        }

        return first + " " + middleInitial + " " + capitalizeWords(last.toString().trim());
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return "";
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "NA";
        String[] parts = name.split(" ");
        return (parts.length >= 2) ? (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase() : name.substring(0, Math.min(name.length(), 2)).toUpperCase();
    }

    private void startBadgeListeners() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Chats")
                .whereArrayContains("participants", user.getUid())
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    int unreadCount = 0;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        List<String> seenBy = (List<String>) doc.get("seenBy");
                        String lastSenderUid = doc.getString("lastSenderUid");
                        if (lastSenderUid != null && !lastSenderUid.equals(user.getUid())) {
                            if (seenBy == null || !seenBy.contains(user.getUid())) {
                                unreadCount++;
                            }
                        }
                    }
                    updateBadge(R.id.badgeMessages, unreadCount);
                });

        db.collection("Bookings")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    int count = 0;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String status = doc.getString("status");
                        if (status == null) continue;

                        String pId = doc.getString("providerId");
                        String cId = doc.getString("clientId");

                        if (user.getUid().equals(pId)) {
                            if (status.equalsIgnoreCase("PENDING")) count++;
                        } else if (user.getUid().equals(cId)) {
                            if (status.equalsIgnoreCase("CONFIRMED") || status.equalsIgnoreCase("APPROVED")) {
                                Boolean viewed = doc.getBoolean("viewedByClient");
                                if (viewed == null || !viewed) count++;
                            }
                        }
                    }
                    updateBadge(R.id.badgeBookings, count);
                });
    }

    private void updateBadge(int badgeId, int count) {
        TextView badge = findViewById(badgeId);
        if (badge == null) return;
        if (count > 0) {
            badge.setText(String.valueOf(count));
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    private void setupBottomNav() {
        ImageView navIcon = findViewById(R.id.navProfileIcon);
        if (navIcon != null) navIcon.setColorFilter(Color.WHITE);
        TextView navLabel = findViewById(R.id.navProfileLabel);
        if (navLabel != null) navLabel.setTextColor(Color.WHITE);

        findViewById(R.id.navHome).setOnClickListener(v -> { Intent intent = new Intent(this, HomeActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
        findViewById(R.id.navBookings).setOnClickListener(v -> { Intent intent = new Intent(this, MyBookingsActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
        findViewById(R.id.navMessages).setOnClickListener(v -> { Intent intent = new Intent(this, InboxActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent); overridePendingTransition(0, 0); });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}