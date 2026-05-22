package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.skillswap.local.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InboxActivity extends AppCompatActivity {

    private LinearLayout llInboxList;
    private FirebaseFirestore db;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        llInboxList = findViewById(R.id.llInboxList);
        db = FirebaseFirestore.getInstance();

        if(FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            fetchInboxFromDatabase();
        }

        setupBottomNav();
        startBadgeListeners();
    }

    private void fetchInboxFromDatabase() {
        TextView loading = new TextView(this);
        loading.setText("Loading messages...");
        loading.setPadding(0, 40, 0, 0);
        llInboxList.addView(loading);

        db.collection("Chats").whereArrayContains("participants", currentUid)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    llInboxList.removeAllViews();

                    if (e != null || queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("You don't have any messages yet.");
                        empty.setPadding(0, 40, 0, 0);
                        llInboxList.addView(empty);
                        return;
                    }

                    List<DocumentSnapshot> docs = new ArrayList<>(queryDocumentSnapshots.getDocuments());

                    Collections.sort(docs, (d1, d2) -> {
                        Date date1 = d1.getDate("lastUpdated");
                        Date date2 = d2.getDate("lastUpdated");

                        if (date1 == null && date2 == null) return 0;
                        if (date1 == null) return 1;
                        if (date2 == null) return -1;

                        return date2.compareTo(date1);
                    });

                    for (DocumentSnapshot chatDoc : docs) {
                        List<String> participants = (List<String>) chatDoc.get("participants");
                        Date lastUpdated = chatDoc.getDate("lastUpdated");
                        String chatRoomId = chatDoc.getId();

                        if (participants != null && participants.size() == 2) {
                            String otherUserId = participants.get(0).equals(currentUid) ? participants.get(1) : participants.get(0);

                            View chatItem = getLayoutInflater().inflate(R.layout.item_inbox_chat, llInboxList, false);
                            llInboxList.addView(chatItem);

                            loadOtherUserDetails(chatItem, chatDoc, otherUserId);
                        }
                    }
                });
    }

    private void loadOtherUserDetails(View chatItem, DocumentSnapshot chatDoc, String otherUserId) {
        String chatRoomId = chatDoc.getId();
        Date lastUpdated = chatDoc.getDate("lastUpdated");

        TextView tvName = chatItem.findViewById(R.id.tvChatName);
        TextView tvMsg = chatItem.findViewById(R.id.tvLastMessage);
        TextView tvTime = chatItem.findViewById(R.id.tvChatTime);
        ImageView ivAvatar = chatItem.findViewById(R.id.ivChatAvatar);
        TextView tvInitials = chatItem.findViewById(R.id.tvChatInitials);
        View unreadIndicator = chatItem.findViewById(R.id.unreadIndicator);

        tvName.setText("Loading...");
        tvMsg.setText("");
        tvTime.setText("");

        // Handle Unread UI
        List<String> seenBy = (List<String>) chatDoc.get("seenBy");
        String lastSenderUid = chatDoc.getString("lastSenderUid");
        boolean isUnread = lastSenderUid != null && !lastSenderUid.equals(currentUid) && (seenBy == null || !seenBy.contains(currentUid));

        if (isUnread) {
            unreadIndicator.setVisibility(View.VISIBLE);
            tvMsg.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMsg.setTextColor(Color.BLACK);
        } else {
            unreadIndicator.setVisibility(View.GONE);
            tvMsg.setTypeface(null, android.graphics.Typeface.NORMAL);
            tvMsg.setTextColor(Color.parseColor("#7A8B9A"));
        }

        db.collection("Users").document(otherUserId).get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String fullName = userDoc.getString("fullName");
                        if (fullName == null) fullName = "Unknown User";

                        String firstName = fullName.split(" ")[0];
                        tvName.setText(fullName);

                        // --- PROFILE PICTURE LOGIC ---
                        String photoUrl = userDoc.getString("profilePhotoUrl");

                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ivAvatar.setVisibility(View.VISIBLE);
                            tvInitials.setVisibility(View.GONE);
                            if (!isFinishing() && !isDestroyed()) {
                                Glide.with(InboxActivity.this)
                                        .load(photoUrl)
                                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                                        .into(ivAvatar);
                            }
                        } else {
                            ivAvatar.setVisibility(View.GONE);
                            tvInitials.setVisibility(View.VISIBLE);
                            tvInitials.setText(getInitials(fullName));
                        }
                        // -----------------------------

                        final String passName = fullName;
                        chatItem.setOnClickListener(v -> {
                            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
                            intent.putExtra("provider_name", passName);
                            startActivity(intent);
                            overridePendingTransition(0, 0);
                        });

                        // Fetch last message for Messenger style format
                        db.collection("Chats").document(chatRoomId).collection("Messages")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(msgTask -> {
                                    String displayMsg = "";

                                    if (!msgTask.isEmpty()) {
                                        DocumentSnapshot msgDoc = msgTask.getDocuments().get(0);
                                        String senderUid = msgDoc.getString("senderUid");
                                        String type = msgDoc.getString("type");
                                        String text = msgDoc.getString("message");

                                        if ("BOOKING_CARD".equals(type)) {
                                            text = "Sent a booking request.";
                                        } else if ("IMAGE".equals(type)) {
                                            text = "Sent a photo.";
                                        } else if ("VIDEO".equals(type)) {
                                            text = "Sent a video.";
                                        }

                                        if (currentUid.equals(senderUid)) {
                                            displayMsg = "You: " + text;
                                        } else {
                                            displayMsg = firstName + ": " + text;
                                        }
                                    } else {
                                        displayMsg = "No messages yet.";
                                    }

                                    tvMsg.setText(displayMsg);

                                    if (lastUpdated != null) {
                                        java.util.Calendar now = java.util.Calendar.getInstance();
                                        java.util.Calendar msgTime = java.util.Calendar.getInstance();
                                        msgTime.setTime(lastUpdated);

                                        if (now.get(java.util.Calendar.YEAR) == msgTime.get(java.util.Calendar.YEAR) &&
                                                now.get(java.util.Calendar.DAY_OF_YEAR) == msgTime.get(java.util.Calendar.DAY_OF_YEAR)) {
                                            // Today: Show time only (e.g. 10:30 AM)
                                            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                            tvTime.setText(sdf.format(lastUpdated));
                                        } else {
                                            // Older than today: Show date only (e.g. May 10)
                                            SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
                                            tvTime.setText(sdf.format(lastUpdated));
                                        }
                                    }
                                });
                    }
                });
    }

    // Helper method para sa default Initials
    private String getInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty()) sb.append(p.charAt(0));
        return sb.toString().toUpperCase();
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

        // Booking Badges (Pending requests for provider OR newly approved for client)
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
                            // Provider sees PENDING requests they haven't responded to
                            if (status.equalsIgnoreCase("PENDING")) count++;
                        } else if (user.getUid().equals(cId)) {
                            // Client sees APPROVED requests they haven't viewed yet
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
        ImageView navIcon = findViewById(R.id.navMessagesIcon);
        if (navIcon != null) navIcon.setColorFilter(Color.WHITE);
        TextView navLabel = findViewById(R.id.navMessagesLabel);
        if (navLabel != null) navLabel.setTextColor(Color.WHITE);

        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.navBookings).setOnClickListener(v -> {
            Intent intent = new Intent(this, MyBookingsActivity.class);
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}