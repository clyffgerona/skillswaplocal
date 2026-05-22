package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.skillswap.local.R;
import com.skillswap.local.models.SkillProvider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout llSkillCards;
    private View vSuspensionRing, vWarningBadge;
    private CardView cvProfileContainer;
    private ImageView ivMyProfileButton;
    private TextView tvMyProfileInitials, tvGreeting, tvUserName;
    private EditText etSearch;

    private boolean isUserSuspended = false;
    private String userSuspensionReason = "";

    private List<SkillProvider> allProviders = new ArrayList<>();
    private Map<String, String> providerPhotoMap = new HashMap<>(); // Holds provider photos

    private TextView btnFilterAll, btnFilterSwap, btnFilterCash;

    private String currentFilter = "ALL";
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        llSkillCards        = findViewById(R.id.llSkillCards);
        vSuspensionRing     = findViewById(R.id.vSuspensionRing);
        vWarningBadge       = findViewById(R.id.vWarningBadge);
        cvProfileContainer  = findViewById(R.id.cvProfileContainer);
        ivMyProfileButton   = findViewById(R.id.ivMyProfileButton);
        tvMyProfileInitials = findViewById(R.id.tvMyProfileInitials);
        tvGreeting          = findViewById(R.id.tvGreeting);
        tvUserName          = findViewById(R.id.tvUserName);
        btnFilterAll        = findViewById(R.id.btnFilterAll);
        btnFilterSwap       = findViewById(R.id.btnFilterSwap);
        btnFilterCash       = findViewById(R.id.btnFilterCash);
        etSearch            = findViewById(R.id.etSearch);

        setTimeBasedGreeting();
        loadCurrentUserProfile();

        View.OnClickListener profileClick = v -> {
            if (isUserSuspended) {
                showSuspensionDialog();
            } else {
                Intent intent = new Intent(HomeActivity.this, UserActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        };

        if (cvProfileContainer != null) cvProfileContainer.setOnClickListener(profileClick);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s.toString().trim().toLowerCase();
                    applyFilters();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            etSearch.clearFocus();
        }

        setupFilterButtons();
        fetchProvidersFromDatabase();
        setupBottomNav();
        startBadgeListeners();
    }

    private void setTimeBasedGreeting() {
        if (tvGreeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = (hour >= 5 && hour < 12) ? "Good morning," : (hour >= 12 && hour < 18) ? "Good afternoon," : "Good evening,";
        tvGreeting.setText(greeting);
    }

    private void loadCurrentUserProfile() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(fbUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    // Check for suspension
                    Boolean suspended = doc.getBoolean("isSuspended");
                    isUserSuspended = (suspended != null && suspended);
                    userSuspensionReason = doc.getString("suspensionReason");

                    if (isUserSuspended) {
                        if (vSuspensionRing != null) vSuspensionRing.setVisibility(View.VISIBLE);
                        if (vWarningBadge != null) vWarningBadge.setVisibility(View.VISIBLE);
                    } else {
                        if (vSuspensionRing != null) vSuspensionRing.setVisibility(View.GONE);
                        if (vWarningBadge != null) vWarningBadge.setVisibility(View.GONE);
                    }

                    String fullName = doc.getString("fullName");
                    if (fullName == null || fullName.isEmpty()) fullName = "";
                    if (!fullName.isEmpty()) {
                        tvUserName.setText(fullName.split(" ")[0]);
                    }

                    String photoUrl = doc.getString("profilePhotoUrl");

                    // Bind to profile container
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        ivMyProfileButton.setVisibility(View.VISIBLE);
                        tvMyProfileInitials.setVisibility(View.GONE);

                        Glide.with(HomeActivity.this).load(photoUrl)
                                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                                .into(ivMyProfileButton);
                    } else {
                        ivMyProfileButton.setVisibility(View.GONE);
                        tvMyProfileInitials.setVisibility(View.VISIBLE);

                        String firstName = fullName.isEmpty() ? "?" : fullName.split(" ")[0];
                        String initial = firstName.isEmpty() ? "?" : String.valueOf(firstName.charAt(0)).toUpperCase();
                        tvMyProfileInitials.setText(initial);
                    }
                });
    }

    private void showSuspensionDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        // Custom View for the dialog to make it "professional"
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 40);

        TextView title = new TextView(this);
        title.setText("Account Status");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#E53935"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);

        TextView message = new TextView(this);
        String reasonStr = (userSuspensionReason != null && !userSuspensionReason.isEmpty())
                ? userSuspensionReason : "Violation of community guidelines.";
        message.setText("Your account has been suspended for the following reason:\n\n\"" + reasonStr + "\"\n\nYou can submit an appeal if you believe this is a mistake.");
        message.setTextColor(Color.parseColor("#0F2645"));
        message.setTextSize(16);

        builder.setView(layout);
        builder.setPositiveButton("Submit Appeal", (dialog, which) -> {
            startActivity(new Intent(HomeActivity.this, AppealActivity.class));
        });
        builder.setNegativeButton("Close", (dialog, which) -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void setupFilterButtons() {
        btnFilterAll.setOnClickListener(v -> setFilter("ALL"));
        btnFilterSwap.setOnClickListener(v -> setFilter("SWAP"));
        btnFilterCash.setOnClickListener(v -> setFilter("CASH"));
    }

    private void setFilter(String filterType) {
        currentFilter = filterType;

        btnFilterAll.setBackgroundColor(Color.parseColor("#E2E8EF"));
        btnFilterAll.setTextColor(Color.parseColor("#7A8B9A"));
        btnFilterSwap.setBackgroundColor(Color.parseColor("#E2E8EF"));
        btnFilterSwap.setTextColor(Color.parseColor("#7A8B9A"));
        btnFilterCash.setBackgroundColor(Color.parseColor("#E2E8EF"));
        btnFilterCash.setTextColor(Color.parseColor("#7A8B9A"));

        if (filterType.equals("ALL")) {
            btnFilterAll.setBackgroundColor(Color.parseColor("#0F2645"));
            btnFilterAll.setTextColor(Color.WHITE);
        } else if (filterType.equals("SWAP")) {
            btnFilterSwap.setBackgroundResource(R.drawable.bg_badge_swap);
            btnFilterSwap.setTextColor(Color.parseColor("#2E7D32"));
        } else if (filterType.equals("CASH")) {
            btnFilterCash.setBackgroundResource(R.drawable.bg_badge_cash);
            btnFilterCash.setTextColor(Color.parseColor("#FF8F00"));
        }
        applyFilters();
    }

    private void fetchProvidersFromDatabase() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        TextView loadingText = new TextView(this);
        loadingText.setText("Loading providers near you...");
        loadingText.setTextColor(Color.parseColor("#7A8B9A"));
        loadingText.setPadding(24, 32, 24, 32);
        llSkillCards.addView(loadingText);

        db.collection("Users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            allProviders.clear();
            providerPhotoMap.clear(); // Clear old photos

            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                if (doc.getId().equals(currentUserId)) continue;

                String status = doc.getString("status");
                if (status == null || !status.equalsIgnoreCase("approved")) continue;

                Boolean isSuspended = doc.getBoolean("isSuspended");
                if (isSuspended != null && isSuspended) continue;

                Boolean isDiscoverable = doc.getBoolean("isDiscoverable");
                if (isDiscoverable == null || !isDiscoverable) continue;

                Boolean acceptsSwap = doc.getBoolean("acceptsSkillSwap");
                Boolean acceptsCash = doc.getBoolean("acceptsCash");
                boolean isSwap = (acceptsSwap != null && acceptsSwap);
                boolean isCash = (acceptsCash != null && acceptsCash);

                List<String> skillsList = (List<String>) doc.get("skillsOffered");
                List<String> days = (List<String>) doc.get("availability");
                String location = doc.getString("location");
                String bio = doc.getString("bio");
                String skillDesc = doc.getString("skillDescription");

                boolean hasSkills = skillsList != null && !skillsList.isEmpty();
                boolean hasDays = days != null && !days.isEmpty();
                boolean hasLocation = location != null && !location.trim().isEmpty();
                boolean hasBio = bio != null && !bio.trim().isEmpty();
                boolean hasSkillDesc = skillDesc != null && !skillDesc.trim().isEmpty();
                boolean hasPayment = isSwap || isCash;

                if (!hasSkills || !hasDays || !hasLocation || !hasBio || !hasPayment || !hasSkillDesc) {
                    continue;
                }

                String rawName = doc.getString("fullName");
                String formattedName = formatOldNames(rawName);

                // Save their photo to the map!
                String photoUrl = doc.getString("profilePhotoUrl");
                if (photoUrl == null || photoUrl.isEmpty()) photoUrl = doc.getString("selfiePhotoUrl");
                if (photoUrl != null) {
                    providerPhotoMap.put(formattedName, photoUrl);
                }

                String skillTitle = skillsList.get(0);
                String priceRange = doc.getString("priceRange");

                if (isCash && (priceRange == null || priceRange.isEmpty())) {
                    priceRange = "Cash";
                } else if (!isCash) {
                    priceRange = "";
                }
                if (priceRange == null) priceRange = "";

                Double ratingObj = doc.getDouble("rating");
                Long sessionsObj = doc.getLong("totalSessions");
                Long showUpObj = doc.getLong("showUpCount");

                float rating = ratingObj != null ? ratingObj.floatValue() : 5.0f;
                int sessions = sessionsObj != null ? sessionsObj.intValue() : 0;
                int showUpCount = showUpObj != null ? showUpObj.intValue() : 0;
                int showUpPercent = sessions > 0 ? (int) (((float) showUpCount / sessions) * 100f) : 100;

                allProviders.add(new SkillProvider(formattedName, skillTitle, String.join(", ", skillsList), rating, sessions, showUpPercent, location, isSwap, priceRange, true));
            }
            applyFilters();
        }).addOnFailureListener(e -> {
            llSkillCards.removeAllViews();
            TextView errorText = new TextView(this);
            errorText.setText("Could not fetch data. Please check connection.");
            errorText.setPadding(24, 32, 24, 32);
            llSkillCards.addView(errorText);
        });
    }

    private void applyFilters() {
        List<SkillProvider> filtered = new ArrayList<>();

        for (SkillProvider p : allProviders) {
            boolean hasSwap = p.isSwap();
            boolean hasCash = p.getPrice() != null && !p.getPrice().isEmpty();

            boolean matchesSearch = true;
            if (!searchQuery.isEmpty()) {
                String providerName = p.getName().toLowerCase();
                String providerSkill = p.getSkillTitle().toLowerCase().replace(",", " ");

                boolean nameMatches = providerName.startsWith(searchQuery) || providerName.contains(" " + searchQuery);
                boolean skillMatches = providerSkill.startsWith(searchQuery) || providerSkill.contains(" " + searchQuery);

                matchesSearch = nameMatches || skillMatches;
            }

            if (!matchesSearch) {
                continue;
            }

            if (currentFilter.equals("ALL")) {
                filtered.add(p);
            } else if (currentFilter.equals("SWAP") && hasSwap) {
                filtered.add(p);
            } else if (currentFilter.equals("CASH") && hasCash) {
                filtered.add(p);
            }
        }
        buildSkillCards(filtered);
    }

    private void buildSkillCards(List<SkillProvider> providers) {
        llSkillCards.removeAllViews();
        if (providers == null || providers.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No providers match your filter.");
            empty.setTextColor(Color.parseColor("#7A8B9A"));
            empty.setPadding(24, 64, 24, 64);
            empty.setGravity(android.view.Gravity.CENTER);
            llSkillCards.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SkillProvider provider : providers) {
            View card = inflater.inflate(R.layout.item_skill_card, llSkillCards, false);
            bindSkillCard(card, provider);
            card.setOnClickListener(v -> openProviderProfile(provider));
            llSkillCards.addView(card);
        }
    }

    private void bindSkillCard(View card, SkillProvider p) {
        TextView tvSkillTitle = card.findViewById(R.id.tvSkillTitle);
        if (tvSkillTitle != null) tvSkillTitle.setText(p.getSkillTitle());

        TextView tvProvider = card.findViewById(R.id.tvProviderName);
        if (tvProvider != null) tvProvider.setText(p.getName() + (p.isVerified() ? " · Verified ✓" : ""));

        // Load Avatar or show Initials
        TextView tvInitials = card.findViewById(R.id.tvProviderInitials);
        ImageView ivAvatar = card.findViewById(R.id.ivProviderAvatar);

        if (tvInitials != null && ivAvatar != null) {
            String photoUrl = providerPhotoMap.get(p.getName());

            if (photoUrl != null && !photoUrl.isEmpty()) {
                ivAvatar.setVisibility(View.VISIBLE);
                tvInitials.setVisibility(View.GONE);

                Glide.with(this)
                        .load(photoUrl)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(ivAvatar);
            } else {
                ivAvatar.setVisibility(View.GONE);
                tvInitials.setVisibility(View.VISIBLE);
                tvInitials.setText(getInitialsFallback(p.getName()));
            }
        }

        TextView tvBadge = card.findViewById(R.id.tvBadge);
        if (tvBadge != null) {
            boolean hasSwap = p.isSwap();
            String priceStr = p.getPrice();
            boolean hasCash = priceStr != null && !priceStr.isEmpty();

            if (hasSwap && hasCash) {
                String cleanPrice = priceStr.replace("₱", "").replace("P", "").trim();
                if (cleanPrice.equalsIgnoreCase("Cash") || cleanPrice.isEmpty()) {
                    tvBadge.setText("Swap • Cash");
                } else {
                    tvBadge.setText("Swap • Cash (₱" + cleanPrice + ")");
                }
                tvBadge.setBackgroundResource(R.drawable.bg_badge_swap);
                tvBadge.setTextColor(Color.parseColor("#085041"));
            } else if (hasSwap) {
                tvBadge.setText("Skill Swap");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_swap);
                tvBadge.setTextColor(Color.parseColor("#085041"));
            } else if (hasCash) {
                String cleanPrice = priceStr.replace("₱", "").replace("P", "").trim();
                if (!cleanPrice.equalsIgnoreCase("Cash") && !cleanPrice.isEmpty()) {
                    tvBadge.setText("Cash (₱" + cleanPrice + ")");
                } else {
                    tvBadge.setText("Cash");
                }
                tvBadge.setBackgroundResource(R.drawable.bg_badge_cash);
                tvBadge.setTextColor(Color.parseColor("#854F0B"));
            } else {
                tvBadge.setVisibility(View.GONE);
            }
        }

        TextView tvStars = card.findViewById(R.id.tvStars);
        if (tvStars != null) {
            tvStars.setText(p.getSessions() > 0 ? "★" : "");
        }

        TextView tvRating = card.findViewById(R.id.tvRating);
        if (tvRating != null) {
            tvRating.setText(p.getSessions() > 0 ? String.format(Locale.US, "%.1f", p.getRating()) : "New");
        }

        TextView tvDistance = card.findViewById(R.id.tvDistance);
        if (tvDistance != null) tvDistance.setText(p.getDistance());
    }

    private String getInitialsFallback(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        } else {
            return fullName.substring(0, 1).toUpperCase();
        }
    }

    private void openProviderProfile(SkillProvider provider) {
        Intent intent = new Intent(this, ProviderProfileActivity.class);
        intent.putExtra("provider_name", provider.getName());
        intent.putExtra("provider_skill", provider.getSkillTitle());
        intent.putExtra("provider_rating", provider.getRating());
        intent.putExtra("provider_sessions", provider.getSessions());
        intent.putExtra("provider_showup", provider.getShowUpPercent());
        intent.putExtra("provider_verified", provider.isVerified());
        intent.putExtra("provider_swap", provider.isSwap());
        intent.putExtra("provider_price", provider.getPrice() != null ? provider.getPrice() : "");
        startActivity(intent);
        overridePendingTransition(0, 0);
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
        ImageView navIcon = findViewById(R.id.navHomeIcon);
        if (navIcon != null) navIcon.setColorFilter(Color.WHITE);
        TextView navLabel = findViewById(R.id.navHomeLabel);
        if (navLabel != null) navLabel.setTextColor(Color.WHITE);

        findViewById(R.id.navBookings).setOnClickListener(v -> {
            Intent intent = new Intent(this, MyBookingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.navMessages).setOnClickListener(v -> {
            Intent intent = new Intent(this, InboxActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.navProfile).setOnClickListener(v -> {
            Intent intent = new Intent(this, UserActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    private String formatOldNames(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return "Unknown Provider";

        if (rawName.contains(".")) {
            return capitalizeWords(rawName);
        }

        String[] words = rawName.trim().split("\\s+");
        if (words.length <= 2) {
            return capitalizeWords(rawName);
        }

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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}
