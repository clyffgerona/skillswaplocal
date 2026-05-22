package com.skillswap.local.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.skillswap.local.R;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LegalAgreementActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "skillswap_legal_prefs";
    private static final String KEY_ACCEPTED = "legal_terms_accepted";
    private static final String KEY_ACCEPTED_VERSION = "legal_terms_version";
    private static final String CURRENT_VERSION = "2.0.0";

    private LinearLayout llDocumentsContainer;
    private Button btnAcceptAll;
    private TextView tvAgreementStatus;
    private ScrollView scrollView;

    private boolean[] documentAccepted = new boolean[3]; // 0=Guidelines, 1=Terms, 2=Privacy
    private String[] documentTitles = {"Community Guidelines", "Terms of Service", "Privacy Policy"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal_agreement);

        initViews();
        setupDocuments();
        setupAcceptButton();
        checkExistingAcceptance();
    }

    private void initViews() {
        llDocumentsContainer = findViewById(R.id.llDocumentsContainer);
        btnAcceptAll = findViewById(R.id.btnAcceptAll);
        tvAgreementStatus = findViewById(R.id.tvAgreementStatus);
        scrollView = findViewById(R.id.scrollView);

        // Set up status bar color
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.navy_primary));
    }

    private void setupDocuments() {
        // Document 1: Community Guidelines
        addDocumentCard(0, documentTitles[0], getString(R.string.guidelines_summary), "Read Full Guidelines");

        // Document 2: Terms of Service
        addDocumentCard(1, documentTitles[1], getString(R.string.terms_summary), "Read Full Terms");

        // Document 3: Privacy Policy
        addDocumentCard(2, documentTitles[2], getString(R.string.privacy_summary), "Read Full Policy");
    }

    private void addDocumentCard(int index, String title, String summary, String readMoreText) {
        View card = getLayoutInflater().inflate(R.layout.item_legal_document, llDocumentsContainer, false);

        TextView tvTitle = card.findViewById(R.id.tvDocumentTitle);
        TextView tvSummary = card.findViewById(R.id.tvDocumentSummary);
        TextView btnReadMore = card.findViewById(R.id.btnReadMore);
        Button btnAccept = card.findViewById(R.id.btnAcceptDocument);
        View acceptedBadge = card.findViewById(R.id.viewAcceptedBadge);

        tvTitle.setText(title);
        tvSummary.setText(summary);
        btnReadMore.setText(readMoreText);

        // Check if already accepted
        if (documentAccepted[index]) {
            acceptedBadge.setVisibility(View.VISIBLE);
            btnAccept.setVisibility(View.GONE);
            btnAccept.setEnabled(false);
        } else {
            acceptedBadge.setVisibility(View.GONE);
            btnAccept.setVisibility(View.VISIBLE);
            btnAccept.setEnabled(true);
        }

        btnReadMore.setOnClickListener(v -> showFullDocument(index, title));

        btnAccept.setOnClickListener(v -> {
            documentAccepted[index] = true;
            acceptedBadge.setVisibility(View.VISIBLE);
            btnAccept.setVisibility(View.GONE);
            updateOverallStatus();

            // Add success animation
            Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
            acceptedBadge.startAnimation(fadeIn);

            Toast.makeText(this, "You accepted the " + title, Toast.LENGTH_SHORT).show();
        });

        llDocumentsContainer.addView(card);
    }

    private void showFullDocument(int docIndex, String title) {
        String content = getDocumentContent(docIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        ScrollView scrollView = new ScrollView(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(padding, padding, padding, padding);
        scrollView.setClipToPadding(false);

        TextView textView = new TextView(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            textView.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY));
        } else {
            @SuppressWarnings("deprecation")
            Spanned spanned = Html.fromHtml(content);
            textView.setText(spanned);
        }
        textView.setTextSize(14f);
        textView.setTextColor(Color.parseColor("#333333"));
        textView.setLineSpacing(0f, 1.2f);

        scrollView.addView(textView);

        builder.setView(scrollView);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        
        if (!documentAccepted[docIndex]) {
            builder.setNegativeButton("Accept Document", (dialog, which) -> {
                documentAccepted[docIndex] = true;
                updateOverallStatus();
                // Refresh the UI for this document card
                refreshDocumentCards();
                Toast.makeText(this, "You accepted the " + title, Toast.LENGTH_SHORT).show();
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        // Style the buttons
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.mint_dark));
            negativeButton.setTypeface(null, Typeface.BOLD);
        }
        
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.navy_primary));
        }
    }

    private void refreshDocumentCards() {
        // Rebuild the document cards to show accepted states
        llDocumentsContainer.removeAllViews();
        setupDocuments();
    }

    private String getDocumentContent(int docIndex) {
        String fileName;
        switch (docIndex) {
            case 0: fileName = "skillswap_community_guidelines.md"; break;
            case 1: fileName = "skillswap_terms_of_service.md"; break;
            case 2: fileName = "skillswap_privacy_policy.md"; break;
            default: return "";
        }

        try (InputStream inputStream = getAssets().open(fileName);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            String content = sb.toString();

            // Convert markdown-style headers to HTML
            content = content.replaceAll("(?m)^# (.*?)$", "<h2 style='color:#0F2645;'>$1</h2>")
                    .replaceAll("(?m)^## (.*?)$", "<h3 style='color:#4DB6AC;'>$1</h3>")
                    .replaceAll("(?m)^### (.*?)$", "<h4 style='color:#4DB6AC;'>$1</h4>")
                    .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                    .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                    .replaceAll("\n\n", "<br/><br/>")
                    .replaceAll("\n", "<br/>");

            return content;

        } catch (Exception e) {
            android.util.Log.e("LegalAgreement", "Error loading asset: " + fileName, e);
            return "Unable to load document content. Please check your internet connection or try again later.";
        }
    }

    private void updateOverallStatus() {
        boolean allAccepted = documentAccepted[0] && documentAccepted[1] && documentAccepted[2];

        if (allAccepted) {
            tvAgreementStatus.setText("✓ All documents accepted. You may now proceed.");
            tvAgreementStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_dark));
            btnAcceptAll.setEnabled(true);
            btnAcceptAll.setAlpha(1.0f);
        } else {
            int count = 0;
            for (boolean b : documentAccepted) if (b) count++;
            tvAgreementStatus.setText("Accepted " + count + "/3 documents. Please accept all to continue.");
            tvAgreementStatus.setTextColor(ContextCompat.getColor(this, R.color.amber_dark));
            btnAcceptAll.setEnabled(false);
            btnAcceptAll.setAlpha(0.5f);
        }
    }

    private void setupAcceptButton() {
        btnAcceptAll.setOnClickListener(v -> {
            if (documentAccepted[0] && documentAccepted[1] && documentAccepted[2]) {
                saveAcceptanceAndProceed();
            } else {
                Toast.makeText(this, "Please accept all three documents first.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveAcceptanceAndProceed() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ACCEPTED, true)
                .putString(KEY_ACCEPTED_VERSION, CURRENT_VERSION)
                .apply();

        Toast.makeText(this, "Thank you for accepting the terms!", Toast.LENGTH_SHORT).show();

        // Proceed to Login
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void checkExistingAcceptance() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean alreadyAccepted = prefs.getBoolean(KEY_ACCEPTED, false);
        String savedVersion = prefs.getString(KEY_ACCEPTED_VERSION, "");

        if (alreadyAccepted && savedVersion.equals(CURRENT_VERSION)) {
            // Already accepted with current version, go to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else if (alreadyAccepted && !savedVersion.equals(CURRENT_VERSION)) {
            // Terms updated, need re-acceptance
            tvAgreementStatus.setText("Terms have been updated. Please review and accept again.");
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent going back without accepting
        if (documentAccepted[0] && documentAccepted[1] && documentAccepted[2]) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, "You must accept all legal documents to use the app.", Toast.LENGTH_LONG).show();
        }
    }
}