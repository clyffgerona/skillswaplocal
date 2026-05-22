package com.skillswap.local.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.skillswap.local.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyBookingsActivity extends AppCompatActivity {

    private LinearLayout llMyBookingsList, calendarContainer;
    private View calendarOverlayBg;
    private CalendarView calendarView;
    private ImageView btnToggleCalendar;
    private TextView btnCloseCalendar;
    private FirebaseFirestore db;
    private String currentUid;

    private TextView tvStatusFilterDropdown, tvSortDropdown;

    private ListenerRegistration clientListener, providerListener;
    private final Map<String, DocumentSnapshot> realTimeMap = new HashMap<>();
    private List<DocumentSnapshot> allBookings = new ArrayList<>();
    private boolean isCalendarVisible = false;
    private String selectedDateFilter = null;
    private String currentStatusFilter = "ALL";
    private String currentSortOption = "LATEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_bookings);

        llMyBookingsList = findViewById(R.id.llMyBookingsList);
        calendarContainer = findViewById(R.id.calendarContainer);
        calendarOverlayBg = findViewById(R.id.calendarOverlayBg);
        calendarView = findViewById(R.id.calendarView);
        btnToggleCalendar = findViewById(R.id.btnToggleCalendar);
        btnCloseCalendar = findViewById(R.id.btnCloseCalendar);
        tvStatusFilterDropdown = findViewById(R.id.tvStatusFilterDropdown);
        tvSortDropdown = findViewById(R.id.tvSortDropdown);

        if (btnToggleCalendar != null) btnToggleCalendar.setColorFilter(Color.WHITE);

        db = FirebaseFirestore.getInstance();

        if(FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            listenToRealTimeBookings();
        }

        setupInteractions();
        setupBottomNav();
        startBadgeListeners();
    }

    private void listenToRealTimeBookings() {
        if (currentUid == null) return;

        clientListener = db.collection("Bookings").whereEqualTo("clientId", currentUid)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        synchronized (realTimeMap) {
                            for (DocumentSnapshot doc : value.getDocuments()) realTimeMap.put(doc.getId(), doc);
                        }
                        processAndSortBookings();
                    }
                });

        providerListener = db.collection("Bookings").whereEqualTo("providerId", currentUid)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        synchronized (realTimeMap) {
                            for (DocumentSnapshot doc : value.getDocuments()) realTimeMap.put(doc.getId(), doc);
                        }
                        processAndSortBookings();
                    }
                });
    }

    private void processAndSortBookings() {
        if (!isFinishing() && !isDestroyed()) {
            runOnUiThread(() -> {
                synchronized (realTimeMap) {
                    allBookings = new ArrayList<>(realTimeMap.values());
                }

                if (allBookings.isEmpty()) {
                    applyFilters();
                    return;
                }

                try {
                    Collections.sort(allBookings, (doc1, doc2) -> {
                        long t1 = getTimestampFromDoc(doc1);
                        long t2 = getTimestampFromDoc(doc2);

                        if ("OLDEST".equals(currentSortOption)) {
                            return Long.compare(t1, t2);
                        } else {
                            return Long.compare(t2, t1);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
                applyFilters();
            });
        }
    }

    private long getTimestampFromDoc(DocumentSnapshot doc) {
        if (doc == null) return 0;

        Object createdAt = doc.get("createdAt");
        if (createdAt instanceof Number) return ((Number) createdAt).longValue();
        if (createdAt instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) createdAt).toDate().getTime();

        Object timestamp = doc.get("timestamp");
        if (timestamp instanceof Number) return ((Number) timestamp).longValue();
        if (timestamp instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) timestamp).toDate().getTime();

        return 0;
    }

    private void setupInteractions() {
        btnToggleCalendar.setOnClickListener(v -> {
            isCalendarVisible = !isCalendarVisible;
            toggleCalendar(isCalendarVisible);
        });

        if (calendarOverlayBg != null) {
            calendarOverlayBg.setOnClickListener(v -> {
                isCalendarVisible = false;
                toggleCalendar(false);
            });
        }

        if (btnCloseCalendar != null) {
            btnCloseCalendar.setOnClickListener(v -> {
                isCalendarVisible = false;
                toggleCalendar(false);
            });
        }

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDateFilter = (month + 1) + "/" + dayOfMonth + "/" + year;
            applyFilters();

            isCalendarVisible = false;
            toggleCalendar(false);
            Toast.makeText(this, "Filtering for: " + selectedDateFilter, Toast.LENGTH_SHORT).show();
        });

        tvStatusFilterDropdown.setOnClickListener(v -> showStatusPopup());
        if (tvSortDropdown != null) {
            tvSortDropdown.setOnClickListener(v -> showSortPopup());
        }
    }

    private void toggleCalendar(boolean show) {
        if (calendarContainer != null) calendarContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (calendarOverlayBg != null) calendarOverlayBg.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            btnToggleCalendar.setBackgroundColor(Color.parseColor("#FF8F00"));
        } else {
            btnToggleCalendar.setBackgroundResource(R.drawable.bg_avail_chip_active);
            if (selectedDateFilter == null) applyFilters();
        }
    }

    private void showStatusPopup() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, tvStatusFilterDropdown);
        popup.getMenu().add("All Bookings");
        popup.getMenu().add("Pending");
        popup.getMenu().add("Approved");
        popup.getMenu().add("Declined");
        popup.getMenu().add("Completed");
        popup.getMenu().add("Cancelled");

        popup.setOnMenuItemClickListener(item -> {
            String selected = item.getTitle().toString();
            String status = "ALL";
            String color = "#4DB6AC";

            if (selected.equals("Pending")) { status = "PENDING"; color = "#FF8F00"; }
            else if (selected.equals("Approved")) { status = "APPROVED"; color = "#4DB6AC"; } // Teal
            else if (selected.equals("Declined")) { status = "DECLINED"; color = "#E53935"; } // Red
            else if (selected.equals("Completed")) { status = "COMPLETED"; color = "#4DB6AC"; } // Teal
            else if (selected.equals("Cancelled")) { status = "CANCELLED"; color = "#E53935"; } // Red

            currentStatusFilter = status;
            tvStatusFilterDropdown.setText(selected);
            tvStatusFilterDropdown.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(color)));

            if (status.equals("ALL")) {
                selectedDateFilter = null;
                if (isCalendarVisible) {
                    isCalendarVisible = false;
                    toggleCalendar(false);
                }
            }

            applyFilters();
            return true;
        });
        popup.show();
    }

    private void showSortPopup() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, tvSortDropdown);
        popup.getMenu().add("Latest First");
        popup.getMenu().add("Oldest First");

        popup.setOnMenuItemClickListener(item -> {
            String selected = item.getTitle().toString();
            if (selected.equals("Latest First")) {
                currentSortOption = "LATEST";
            } else {
                currentSortOption = "OLDEST";
            }
            tvSortDropdown.setText(selected);
            processAndSortBookings();
            return true;
        });
        popup.show();
    }

    // -------------------------------------------------------------
    // FIXED: Programmatic Rounded Card Badge (Replaces XML drawables)
    // -------------------------------------------------------------
    private void setStatusBadge(TextView tv, String textColorHex, String bgColorHex) {
        tv.setTextColor(Color.parseColor(textColorHex));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);

        // 8dp radius keeps it looking like a clean, rounded badge inside the card
        float density = getResources().getDisplayMetrics().density;
        bg.setCornerRadius(8 * density);
        bg.setColor(Color.parseColor(bgColorHex));

        tv.setBackground(bg);
        tv.setPadding((int)(12 * density), (int)(6 * density), (int)(12 * density), (int)(6 * density));
    }

    private void applyFilters() {
        llMyBookingsList.removeAllViews();
        boolean hasItems = false;
        LayoutInflater inflater = LayoutInflater.from(this);

        List<DocumentSnapshot> bookingsCopy = new ArrayList<>(allBookings);

        for (DocumentSnapshot doc : bookingsCopy) {
            try {
                String dbStatus = doc.getString("status");
                if (dbStatus == null) dbStatus = "PENDING";
                String uiStatus = dbStatus.trim().toUpperCase();

                if (uiStatus.equals("CONFIRMED")) uiStatus = "APPROVED";
                if (uiStatus.equals("REJECTED")) uiStatus = "DECLINED";

                String dbDateStr = doc.getString("dateTimeStr");
                if (dbDateStr == null) dbDateStr = "";

                boolean isAll = currentStatusFilter.equalsIgnoreCase("ALL");
                boolean statusMatches = isAll || uiStatus.equalsIgnoreCase(currentStatusFilter);
                boolean dateMatches = selectedDateFilter == null || dbDateStr.toLowerCase().contains(selectedDateFilter.toLowerCase());

                if (statusMatches && dateMatches) {
                    hasItems = true;
                    String pId = doc.getString("providerId");
                    String cName = doc.getString("clientName");
                    String pName = doc.getString("providerName");
                    String other = currentUid.equals(pId) ? (cName != null ? cName : "Client") : (pName != null ? pName : "Provider");

                    View card = inflater.inflate(R.layout.item_booking_card, llMyBookingsList, false);
                    ((TextView) card.findViewById(R.id.tvService)).setText(doc.getString("service") + " with " + other);
                    ((TextView) card.findViewById(R.id.tvDate)).setText(dbDateStr);
                    ((TextView) card.findViewById(R.id.tvLocation)).setText(doc.getString("meetingLocation"));

                    TextView tvEx = card.findViewById(R.id.tvExchange);
                    String ex = doc.getString("exchangeMode");
                    tvEx.setText((ex != null && ex.contains("Swap")) ? "Skill Swap" : ex);

                    TextView tvSt = card.findViewById(R.id.tvStatus);
                    tvSt.setText(uiStatus);

                    // -------------------------------------------------------------
                    // APPLYING THE UNIFIED BADGES
                    // -------------------------------------------------------------
                    if (uiStatus.equals("APPROVED")) {
                        setStatusBadge(tvSt, "#FFFFFF", "#4DB6AC"); // Teal
                    } else if (uiStatus.equals("COMPLETED")) {
                        setStatusBadge(tvSt, "#FFFFFF", "#4DB6AC"); // Teal
                    } else if (uiStatus.equals("DECLINED")) {
                        setStatusBadge(tvSt, "#FFFFFF", "#E53935"); // Red
                    } else if (uiStatus.equals("CANCELLED")) {
                        setStatusBadge(tvSt, "#FFFFFF", "#E53935"); // Red
                    } else { // PENDING
                        setStatusBadge(tvSt, "#FFFFFF", "#FF8F00"); // Orange
                    }

                    card.findViewById(R.id.btnAccept).setVisibility(View.GONE);
                    card.findViewById(R.id.btnDecline).setVisibility(View.GONE);

                    View approvedMark = card.findViewById(R.id.viewApprovedMark);
                    if (uiStatus.equals("APPROVED")) {
                        boolean viewed = doc.getBoolean("viewedBy" + (currentUid.equals(pId) ? "Provider" : "Client")) != null && doc.getBoolean("viewedBy" + (currentUid.equals(pId) ? "Provider" : "Client"));
                        if (!viewed) {
                            approvedMark.setVisibility(View.VISIBLE);
                        } else {
                            approvedMark.setVisibility(View.GONE);
                        }
                    } else {
                        approvedMark.setVisibility(View.GONE);
                    }

                    String bId = doc.getId();
                    card.setOnClickListener(v -> {
                        String field = "viewedBy" + (currentUid.equals(pId) ? "Provider" : "Client");
                        db.collection("Bookings").document(bId).update(field, true);

                        Intent intent = new Intent(this, BookingDetailsActivity.class);
                        intent.putExtra("booking_id", bId);
                        startActivity(intent);
                        overridePendingTransition(0, 0);
                    });
                    llMyBookingsList.addView(card);
                }
            } catch (Exception e) {}
        }

        if (!hasItems) {
            TextView empty = new TextView(this);
            String emptyMessage;
            if (selectedDateFilter != null) {
                emptyMessage = "No bookings found for " + selectedDateFilter;
            } else if (currentStatusFilter.equalsIgnoreCase("ALL")) {
                emptyMessage = "You have no bookings yet.";
            } else {
                emptyMessage = "No " + currentStatusFilter.toLowerCase() + " bookings found.";
            }
            empty.setText(emptyMessage);
            empty.setTextColor(Color.parseColor("#7A8B9A"));
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 100, 0, 0);
            llMyBookingsList.addView(empty);
        }
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
        ImageView navIcon = findViewById(R.id.navBookingsIcon);
        if (navIcon != null) navIcon.setColorFilter(Color.WHITE);

        TextView navLabel = findViewById(R.id.navBookingsLabel);
        if (navLabel != null) navLabel.setTextColor(Color.WHITE);

        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.navMessages).setOnClickListener(v -> {
            startActivity(new Intent(this, InboxActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.navProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, UserActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clientListener != null) clientListener.remove();
        if (providerListener != null) providerListener.remove();
    }

    @Override public void onBackPressed() { super.onBackPressed(); overridePendingTransition(0, 0); }
}