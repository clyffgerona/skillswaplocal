package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import com.skillswap.local.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etFullName, etBio, etLocation, etSkillsOffered, etSkillDescription, etPriceRange;
    private TextView tvAvatarInitials, btnBack, btnSave, tvVerifiedBadge, tvChangePfp;
    private ImageView ivAvatar;
    private Button btnLogout;
    private CheckBox cbSkillSwap, cbCash, cbDiscoverable;
    private final List<String> selectedDays = new ArrayList<>();

    private FirebaseFirestore db;
    private String userId;

    private String newProfilePicBase64 = null;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                try {
                    Bitmap raw     = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    Bitmap scaled  = scaleBitmap(raw, 400);

                    ivAvatar.setImageBitmap(scaled);
                    ivAvatar.setVisibility(View.VISIBLE);
                    tvAvatarInitials.setVisibility(View.GONE);

                    newProfilePicBase64 = bitmapToBase64(scaled, 60);

                } catch (IOException e) {
                    Toast.makeText(this, "Could not load image.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        db = FirebaseFirestore.getInstance();

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) { finish(); return; }
        userId = fbUser.getUid();

        initViews();
        loadUserData();
        setupDayChips();

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveProfileChanges());

        ivAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        tvAvatarInitials.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        tvChangePfp.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void initViews() {
        btnBack          = findViewById(R.id.btnBack);
        btnSave          = findViewById(R.id.btnSave);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvVerifiedBadge  = findViewById(R.id.tvVerifiedBadge);
        tvChangePfp      = findViewById(R.id.tvChangePfp);
        ivAvatar         = findViewById(R.id.ivAvatar);
        btnLogout        = findViewById(R.id.btnLogout);

        etFullName         = findViewById(R.id.etFullName);
        etBio              = findViewById(R.id.etBio);
        etLocation         = findViewById(R.id.etLocation);
        etSkillsOffered    = findViewById(R.id.etSkillsOffered);
        etSkillDescription = findViewById(R.id.etSkillDescription);
        etPriceRange       = findViewById(R.id.etPriceRange);

        cbSkillSwap      = findViewById(R.id.cbSkillSwap);
        cbCash           = findViewById(R.id.cbCash);
        cbDiscoverable   = findViewById(R.id.cbDiscoverable); // Included just in case

        tvAvatarInitials.setText("");
        etFullName.setEnabled(false);
        etFullName.setAlpha(0.6f);
    }

    private void loadUserData() {
        DocumentReference userRef = db.collection("Users").document(userId);

        userRef.get()
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show())
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    String fullName = doc.getString("fullName");
                    if (TextUtils.isEmpty(fullName)) fullName = doc.getString("name");
                    if (!TextUtils.isEmpty(fullName)) {
                        etFullName.setText(fullName);
                        setInitials(fullName);
                    } else {
                        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (fbUser != null && fbUser.getEmail() != null) {
                            String prefix = fbUser.getEmail().split("@")[0];
                            etFullName.setText(prefix);
                            tvAvatarInitials.setText(String.valueOf(prefix.charAt(0)).toUpperCase());
                        }
                    }

                    String pfp = doc.getString("profilePicBase64");
                    if (TextUtils.isEmpty(pfp)) {
                        pfp = doc.getString("selfiePhotoBase64");
                    }
                    if (!TextUtils.isEmpty(pfp)) {
                        loadBase64Avatar(pfp);
                    }

                    if ("approved".equals(doc.getString("status"))) {
                        tvVerifiedBadge.setVisibility(View.VISIBLE);
                    }

                    setText(etBio,              doc.getString("bio"));
                    setText(etLocation,         doc.getString("location"));
                    setText(etSkillsOffered,    doc.getString("skillsOffered"));
                    setText(etSkillDescription, doc.getString("skillDescription"));
                    setText(etPriceRange,       doc.getString("priceRange"));

                    Boolean swap = doc.getBoolean("modeSkillSwap");
                    Boolean cash = doc.getBoolean("modeCash");
                    if (swap != null) cbSkillSwap.setChecked(swap);
                    if (cash != null) cbCash.setChecked(cash);

                    if (cbDiscoverable != null) {
                        Boolean discoverable = doc.getBoolean("isDiscoverable");
                        cbDiscoverable.setChecked(discoverable == null || discoverable);
                    }

                    List<String> days = (List<String>) doc.get("availableDays");
                    if (days != null) {
                        selectedDays.addAll(days);
                        restoreDayChips(days);
                    }
                });
    }

    private void saveProfileChanges() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("bio",              etBio.getText().toString().trim());
        updates.put("location",         etLocation.getText().toString().trim());
        updates.put("skillsOffered",    etSkillsOffered.getText().toString().trim());
        updates.put("skillDescription", etSkillDescription.getText().toString().trim());
        updates.put("priceRange",       etPriceRange.getText().toString().trim());
        updates.put("modeSkillSwap",    cbSkillSwap.isChecked());
        updates.put("modeCash",         cbCash.isChecked());
        updates.put("availableDays",    new ArrayList<>(selectedDays));

        if (cbDiscoverable != null) {
            updates.put("isDiscoverable", cbDiscoverable.isChecked());
        }

        if (newProfilePicBase64 != null) {
            updates.put("profilePicBase64", newProfilePicBase64);
        }

        db.collection("Users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                    newProfilePicBase64 = null;
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show());
    }

    private void loadBase64Avatar(String base64) {
        try {
            byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp     = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            if (bmp != null) {
                ivAvatar.setImageBitmap(bmp);
                ivAvatar.setVisibility(View.VISIBLE);
                tvAvatarInitials.setVisibility(View.GONE);
            }
        } catch (Exception ignored) { }
    }

    private void setInitials(String fullName) {
        if (TextUtils.isEmpty(fullName)) return;
        String[] raw = fullName.trim().split("\\s+");
        List<String> parts = new ArrayList<>();
        for (String token : raw) {
            if (token.length() > 0 && !(token.length() <= 2 && token.endsWith("."))) {
                parts.add(token);
            }
        }
        String initials;
        if (parts.size() >= 2) {
            initials = String.valueOf(parts.get(0).charAt(0))
                    + String.valueOf(parts.get(parts.size() - 1).charAt(0));
        } else if (parts.size() == 1) {
            initials = String.valueOf(parts.get(0).charAt(0));
        } else {
            initials = String.valueOf(fullName.charAt(0));
        }
        tvAvatarInitials.setText(initials.toUpperCase());
    }

    private void setupDayChips() {
        int[] chipIds = {
                R.id.chipMon, R.id.chipTue, R.id.chipWed,
                R.id.chipThu, R.id.chipFri, R.id.chipSat, R.id.chipSun
        };
        for (int id : chipIds) {
            TextView chip = findViewById(id);
            chip.setOnClickListener(v -> toggleChip(chip));
        }
    }

    private void toggleChip(TextView chip) {
        String day = chip.getText().toString();
        if (selectedDays.contains(day)) {
            selectedDays.remove(day);
            chip.setBackgroundColor(0xFFE5E9ED);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            selectedDays.add(day);
            chip.setBackgroundColor(0xFF4DB6AC);
            chip.setTextColor(0xFFFFFFFF);
        }
    }

    private void restoreDayChips(List<String> savedDays) {
        Map<String, Integer> chipMap = new HashMap<>();
        chipMap.put("Mon", R.id.chipMon); chipMap.put("Tue", R.id.chipTue);
        chipMap.put("Wed", R.id.chipWed); chipMap.put("Thu", R.id.chipThu);
        chipMap.put("Fri", R.id.chipFri); chipMap.put("Sat", R.id.chipSat);
        chipMap.put("Sun", R.id.chipSun);
        for (String day : savedDays) {
            Integer chipId = chipMap.get(day);
            if (chipId == null) continue;
            TextView chip = findViewById(chipId);
            chip.setBackgroundColor(0xFF4DB6AC);
            chip.setTextColor(0xFFFFFFFF);
        }
    }

    private Bitmap scaleBitmap(Bitmap original, int maxSize) {
        int w = original.getWidth(), h = original.getHeight();
        if (w <= maxSize && h <= maxSize) return original;
        float scale = (float) maxSize / Math.max(w, h);
        return Bitmap.createScaledBitmap(original, Math.round(w * scale), Math.round(h * scale), true);
    }

    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private void setText(EditText field, String value) {
        if (!TextUtils.isEmpty(value)) field.setText(value);
    }
}