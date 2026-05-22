package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
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

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.skillswap.local.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VerificationActivity extends AppCompatActivity {

    private EditText etFirstName, etMiddleInitial, etLastName;
    private ImageView ivIdPreview, ivSelfiePreview;
    private Button btnUploadId, btnUploadSelfie, btnSubmitVerification;
    private CheckBox cbUseAsProfilePic;
    private TextView tvVerificationError;

    private String userEmail;
    private String userPassword;
    private Bitmap idBitmap     = null;
    private Bitmap selfieBitmap = null;

    private final ActivityResultLauncher<String> pickIdLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        idBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        idBitmap = scaleBitmap(idBitmap, 800);
                        ivIdPreview.setImageBitmap(idBitmap);
                    } catch (IOException e) {
                        showError("Failed to load ID image.");
                    }
                }
            });

    private final ActivityResultLauncher<String> pickSelfieLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        selfieBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        selfieBitmap = scaleBitmap(selfieBitmap, 600);
                        ivSelfiePreview.setImageBitmap(selfieBitmap);
                    } catch (IOException e) {
                        showError("Failed to load selfie image.");
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        try {
            MediaManager.get();
        } catch (Exception e) {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", "dykygrydm");
            config.put("api_key", "995585552577712");
            config.put("api_secret", "MrId8H8hwOzVVjjYr5_GNYqQZFw");
            MediaManager.init(this, config);
        }

        userEmail    = getIntent().getStringExtra("user_email");
        userPassword = getIntent().getStringExtra("user_password");

        // Fallback: If redirected from LoginActivity, we get the email from FirebaseAuth
        if (userEmail == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        }

        etFirstName           = findViewById(R.id.etFirstName);
        etMiddleInitial       = findViewById(R.id.etMiddleName);
        etLastName            = findViewById(R.id.etLastName);
        ivIdPreview           = findViewById(R.id.ivIdPreview);
        ivSelfiePreview       = findViewById(R.id.ivSelfiePreview);
        btnUploadId           = findViewById(R.id.btnUploadId);
        btnUploadSelfie       = findViewById(R.id.btnUploadSelfie);
        cbUseAsProfilePic     = findViewById(R.id.cbUseAsProfilePic);
        btnSubmitVerification = findViewById(R.id.btnSubmitVerification);
        tvVerificationError   = findViewById(R.id.tvVerificationError);

        btnUploadId.setOnClickListener(v -> pickIdLauncher.launch("image/*"));
        btnUploadSelfie.setOnClickListener(v -> pickSelfieLauncher.launch("image/*"));
        btnSubmitVerification.setOnClickListener(v -> validateAndSubmit());
    }

    private void validateAndSubmit() {
        String firstName     = etFirstName.getText().toString().trim();
        String middleInitial = etMiddleInitial.getText().toString().trim();
        String lastName      = etLastName.getText().toString().trim();

        tvVerificationError.setVisibility(View.GONE);

        if (TextUtils.isEmpty(firstName)) { showError("Please enter your first name."); return; }
        if (TextUtils.isEmpty(lastName))  { showError("Please enter your last name.");  return; }
        if (idBitmap == null)             { showError("Please select an ID image.");    return; }
        if (selfieBitmap == null)         { showError("Please select a selfie image."); return; }

        firstName = capitalizeWords(firstName);
        lastName = capitalizeWords(lastName);

        String formattedMiddle = "";
        if (!TextUtils.isEmpty(middleInitial)) {
            formattedMiddle = " " + Character.toUpperCase(middleInitial.charAt(0)) + ".";
        }

        String fullName = firstName + formattedMiddle + " " + lastName;

        setBusyText("Uploading ID (1/2)...");
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        uploadIdToCloudinary(userId, fullName);
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

    private void uploadIdToCloudinary(String userId, String fullName) {
        setBusyText("Uploading ID (1/2)...");

        byte[] idBytes = bitmapToByteArray(idBitmap, 80);

        MediaManager.get().upload(idBytes)
                .option("folder", "SkillSwap Local/ID Verification")
                .option("public_id", "id_" + userId)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override public void onSuccess(String requestId, Map resultData) {
                        String idUrl = (String) resultData.get("secure_url");
                        runOnUiThread(() -> uploadSelfieToCloudinary(userId, fullName, idUrl));
                    }
                    @Override public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> rollbackAndFail("Failed to upload ID: " + error.getDescription()));
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void uploadSelfieToCloudinary(String userId, String fullName, String idUrl) {
        setBusyText("Uploading Selfie (2/2)...");

        byte[] selfieBytes = bitmapToByteArray(selfieBitmap, 80);

        MediaManager.get().upload(selfieBytes)
                .option("folder", "SkillSwap Local/Face Verification")
                .option("public_id", "face_" + userId)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override public void onSuccess(String requestId, Map resultData) {
                        String selfieUrl = (String) resultData.get("secure_url");
                        runOnUiThread(() -> saveDataToFirestore(userId, fullName, idUrl, selfieUrl));
                    }
                    @Override public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> rollbackAndFail("Failed to upload Selfie: " + error.getDescription()));
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void saveDataToFirestore(String userId, String fullName, String idUrl, String selfieUrl) {
        setBusyText("Saving data...");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> userData = new HashMap<>();
        userData.put("email",          userEmail);
        userData.put("fullName",       fullName);
        userData.put("status",         "pending");
        userData.put("idPhotoUrl",     idUrl);
        userData.put("selfiePhotoUrl", selfieUrl);

        if (cbUseAsProfilePic.isChecked()) {
            userData.put("profilePhotoUrl", selfieUrl);
        }

        db.collection("Users").document(userId).set(userData, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> {
                    rollbackAndFail("Failed to save your data. Please try again.");
                })
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Submitted! Please wait for admin approval.", Toast.LENGTH_LONG).show();

                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void rollbackAndFail(String errorMsg) {
        showError(errorMsg);
        setBusyText(null);
    }

    private Bitmap scaleBitmap(Bitmap original, int maxSize) {
        int width  = original.getWidth();
        int height = original.getHeight();

        if (width <= maxSize && height <= maxSize) return original;

        float scale = (float) maxSize / Math.max(width, height);
        int newWidth  = Math.round(width  * scale);
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private byte[] bitmapToByteArray(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }

    private void setBusyText(String text) {
        if (text == null) {
            btnSubmitVerification.setEnabled(true);
            btnSubmitVerification.setText("Submit for Review");
        } else {
            btnSubmitVerification.setEnabled(false);
            btnSubmitVerification.setText(text);
        }
    }

    private void showError(String message) {
        tvVerificationError.setText(message);
        tvVerificationError.setVisibility(View.VISIBLE);
    }
}