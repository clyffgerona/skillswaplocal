package com.skillswap.local.activities;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.skillswap.local.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateBookingActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String providerUid, providerName, chatRoomId;
    private ArrayList<String> providerSkills;

    private String selectedDate = "";
    private String selectedTime = "";
    private String selectedExchangeMode = "Skill Swap";
    private String selectedLocation = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MAP SPEED OPTIMIZATION (More download threads)
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setTileDownloadThreads((short) 4);
        Configuration.getInstance().setTileDownloadMaxQueueSize((short) 40);

        setContentView(R.layout.activity_create_booking);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Get Data passed from ChatActivity
        providerUid = getIntent().getStringExtra("provider_uid");
        providerName = getIntent().getStringExtra("provider_name");
        chatRoomId = getIntent().getStringExtra("chat_room_id");
        providerSkills = getIntent().getStringArrayListExtra("provider_skills");

        if (providerSkills == null || providerSkills.isEmpty()) {
            providerSkills = new ArrayList<>();
            providerSkills.add("General Service");
        }

        initUI();
    }

    private void initUI() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView tvProviderName = findViewById(R.id.tvBookingProviderName);
        Spinner spinnerService = findViewById(R.id.spinnerService);
        TextView tvPickDate = findViewById(R.id.tvPickDate);
        TextView tvPickTime = findViewById(R.id.tvPickTime);
        TextView tvMeetingLocation = findViewById(R.id.tvMeetingLocation);
        TextView btnSwap = findViewById(R.id.btnExchangeSwap);
        TextView btnCash = findViewById(R.id.btnExchangeCash);
        EditText etNote = findViewById(R.id.etBookingNote);
        Button btnSubmit = findViewById(R.id.btnSendBookingRequest);

        tvProviderName.setText("Booking with " + providerName);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, providerSkills);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerService.setAdapter(spinnerAdapter);

        tvPickDate.setOnClickListener(v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                selectedDate = (month + 1) + "/" + dayOfMonth + "/" + year;
                tvPickDate.setText(selectedDate);
                tvPickDate.setTextColor(getColor(R.color.text_primary));
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dpd.show();
        });

        tvPickTime.setOnClickListener(v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String amPm = hourOfDay >= 12 ? "PM" : "AM";
                int hr = hourOfDay % 12;
                if (hr == 0) hr = 12;
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d %s", hr, minute, amPm);
                tvPickTime.setText(selectedTime);
                tvPickTime.setTextColor(getColor(R.color.text_primary));
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), false).show();
        });

        tvMeetingLocation.setOnClickListener(v -> showFoodpandaMapPicker(tvMeetingLocation));

        btnSwap.setOnClickListener(v -> {
            selectedExchangeMode = "Skill Swap";
            btnSwap.setAlpha(1.0f);
            btnCash.setAlpha(0.4f);
        });

        btnCash.setOnClickListener(v -> {
            selectedExchangeMode = "Cash";
            btnCash.setAlpha(1.0f);
            btnSwap.setAlpha(0.4f);
        });
        btnCash.setAlpha(0.4f);

        btnSubmit.setOnClickListener(v -> {
            String serviceName = spinnerService.getSelectedItem().toString();
            String note = etNote.getText().toString().trim();

            if (selectedDate.isEmpty() || selectedTime.isEmpty() || selectedLocation.isEmpty()) {
                Toast.makeText(this, "Please complete Date, Time, and Location.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy hh:mm a", Locale.getDefault());
                Date bookingDateTime = sdf.parse(selectedDate + " " + selectedTime);
                if (bookingDateTime != null && bookingDateTime.before(new Date())) {
                    Toast.makeText(this, "You cannot select a time in the past.", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Exception e) {}

            btnSubmit.setText("Checking...");
            btnSubmit.setEnabled(false);

            // Check if there's an existing active booking with this provider
            db.collection("Bookings")
                    .whereEqualTo("clientId", mAuth.getCurrentUser().getUid())
                    .whereEqualTo("providerId", providerUid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        boolean hasActiveBooking = false;
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            String status = doc.getString("status");
                            if (status != null && (status.equalsIgnoreCase("PENDING") || status.equalsIgnoreCase("APPROVED") || status.equalsIgnoreCase("CONFIRMED"))) {
                                hasActiveBooking = true;
                                break;
                            }
                        }

                        if (hasActiveBooking) {
                            Toast.makeText(this, "You have an ongoing or pending booking with this provider. Please complete or cancel it first.", Toast.LENGTH_LONG).show();
                            btnSubmit.setText("Send Booking Request");
                            btnSubmit.setEnabled(true);
                        } else {
                            sendBookingRequest(serviceName, note, btnSubmit);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error checking booking status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSubmit.setText("Send Booking Request");
                        btnSubmit.setEnabled(true);
                    });
        });
    }

    private void sendBookingRequest(String serviceName, String note, Button btnSubmit) {
        btnSubmit.setText("Sending...");
        String combinedDateTime = selectedDate + " · " + selectedTime;

        Map<String, Object> cardMap = new HashMap<>();
        cardMap.put("type", "BOOKING_CARD");
        cardMap.put("senderUid", mAuth.getCurrentUser().getUid());
        cardMap.put("service", serviceName);
        cardMap.put("date", combinedDateTime);
        cardMap.put("location", selectedLocation);
        cardMap.put("exchange", selectedExchangeMode);
        cardMap.put("status", "Pending · Awaiting response");
        cardMap.put("timestamp", new Date());

        db.collection("Chats").document(chatRoomId)
                .collection("Messages")
                .add(cardMap)
                .addOnSuccessListener(docRef -> {
                    Map<String, Object> bookingData = new HashMap<>();
                    bookingData.put("bookingId", docRef.getId());
                    bookingData.put("providerId", providerUid);
                    bookingData.put("providerName", providerName);
                    bookingData.put("clientId", mAuth.getCurrentUser().getUid());
                    bookingData.put("clientName", mAuth.getCurrentUser().getDisplayName() != null ? mAuth.getCurrentUser().getDisplayName() : "Client");
                    bookingData.put("service", serviceName);
                    bookingData.put("dateTimeStr", combinedDateTime);
                    bookingData.put("meetingLocation", selectedLocation);
                    bookingData.put("exchangeMode", selectedExchangeMode);
                    bookingData.put("note", note);
                    bookingData.put("status", "PENDING");
                    bookingData.put("reviewedByClient", false);
                    bookingData.put("createdAt", System.currentTimeMillis());

                    db.collection("Bookings").document(docRef.getId()).set(bookingData)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(this, "Booking request sent!", Toast.LENGTH_SHORT).show();
                                updateChatThread("Sent a booking request: " + serviceName);
                                finish(); // Bumalik sa ChatActivity pagkatapos
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setText("Send Booking Request");
                    btnSubmit.setEnabled(true);
                });
    }

    private void updateChatThread(String lastMessage) {
        if (chatRoomId == null) return;
        Map<String, Object> threadMap = new HashMap<>();
        threadMap.put("participants", Arrays.asList(mAuth.getCurrentUser().getUid(), providerUid));
        threadMap.put("lastMessage", lastMessage);
        threadMap.put("lastUpdated", new Date());
        threadMap.put("lastSenderUid", mAuth.getCurrentUser().getUid());
        threadMap.put("seenBy", Arrays.asList(mAuth.getCurrentUser().getUid()));
        db.collection("Chats").document(chatRoomId).set(threadMap, SetOptions.merge());
    }

    static class LocationResult {
        String name, address;
        double lat, lon;
    }

    private void showFoodpandaMapPicker(TextView targetTextView) {
        Dialog mapDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        RelativeLayout root = new RelativeLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MapView map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMultiTouchControls(true);

        RotationGestureOverlay mRotationGestureOverlay = new RotationGestureOverlay(map);
        mRotationGestureOverlay.setEnabled(true);
        map.getOverlays().add(mRotationGestureOverlay);

        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        map.getOverlayManager().getTilesOverlay().setColorFilter(filter);

        IMapController mapController = map.getController();
        mapController.setZoom(16.0);
        mapController.setCenter(new GeoPoint(14.1106, 122.9553)); // Default

        root.addView(map, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Center Pin
        TextView centerPin = new TextView(this);
        centerPin.setText("📍");
        centerPin.setTextSize(48f);
        RelativeLayout.LayoutParams pinParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pinParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        centerPin.setLayoutParams(pinParams);
        root.addView(centerPin);

        // CLOSE BUTTON FIX: Idinagdag muna bago ang Search Bar para nasa taas siya
        TextView btnClose = new TextView(this);
        btnClose.setId(View.generateViewId());
        btnClose.setText("✖");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent black background
        btnClose.setTextSize(20f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setPadding(30, 20, 30, 20);
        RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        closeParams.setMargins(0, 40, 40, 0);
        btnClose.setLayoutParams(closeParams);
        root.addView(btnClose);
        btnClose.setOnClickListener(v -> mapDialog.dismiss());

        // SEARCH BAR FIX: Inilagay sa ibaba ng Close Button
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setId(View.generateViewId());
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setBackgroundColor(Color.WHITE);
        searchBox.setPadding(20, 20, 20, 20);
        searchBox.setElevation(8f);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Search location");
        etSearch.setBackgroundResource(R.drawable.bg_input_field);
        etSearch.setPadding(20, 20, 20, 20);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnSearch = new Button(this);
        btnSearch.setText("Find");
        btnSearch.setBackgroundColor(getColor(R.color.navy_primary));
        btnSearch.setTextColor(Color.WHITE);

        searchBox.addView(etSearch);
        searchBox.addView(btnSearch);

        RelativeLayout.LayoutParams searchParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.addRule(RelativeLayout.BELOW, btnClose.getId()); // ETO ANG FIX NG OVERLAP!
        searchParams.setMargins(40, 20, 40, 0);
        searchBox.setLayoutParams(searchParams);
        root.addView(searchBox);

        // Dropdown List
        ListView listView = new ListView(this);
        listView.setBackgroundColor(Color.WHITE);
        listView.setElevation(8f);
        listView.setVisibility(View.GONE);

        RelativeLayout.LayoutParams listParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.addRule(RelativeLayout.BELOW, searchBox.getId());
        listParams.setMargins(40, 0, 40, 0);
        listView.setLayoutParams(listParams);

        List<LocationResult> searchResults = new ArrayList<>();
        ArrayAdapter<LocationResult> adapter = new ArrayAdapter<LocationResult>(this, 0, searchResults) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LinearLayout layout = new LinearLayout(getContext());
                    layout.setOrientation(LinearLayout.HORIZONTAL);
                    layout.setPadding(20, 30, 20, 30);
                    layout.setGravity(Gravity.CENTER_VERTICAL);
                    TextView icon = new TextView(getContext());
                    icon.setText("📍");
                    icon.setTextSize(18f);
                    icon.setPadding(0, 0, 20, 0);
                    layout.addView(icon);
                    LinearLayout textContainer = new LinearLayout(getContext());
                    textContainer.setOrientation(LinearLayout.VERTICAL);
                    TextView tvMain = new TextView(getContext());
                    tvMain.setTag("main");
                    tvMain.setTextSize(15f);
                    tvMain.setTextColor(Color.BLACK);
                    tvMain.setTypeface(null, Typeface.BOLD);
                    textContainer.addView(tvMain);
                    TextView tvSub = new TextView(getContext());
                    tvSub.setTag("sub");
                    tvSub.setTextSize(12f);
                    tvSub.setTextColor(Color.GRAY);
                    textContainer.addView(tvSub);
                    layout.addView(textContainer);
                    convertView = layout;
                }
                LocationResult item = getItem(position);
                TextView tvMain = convertView.findViewWithTag("main");
                TextView tvSub = convertView.findViewWithTag("sub");
                tvMain.setText(item.name);
                tvSub.setText(item.address);
                return convertView;
            }
        };
        listView.setAdapter(adapter);
        root.addView(listView);

        // Confirm Button
        Button btnConfirm = new Button(this);
        btnConfirm.setText("Confirm Pinned Location");
        btnConfirm.setBackgroundResource(R.drawable.bg_book_btn);
        btnConfirm.setTextColor(Color.WHITE);
        btnConfirm.setTextSize(16f);
        btnConfirm.setTypeface(null, Typeface.BOLD);

        RelativeLayout.LayoutParams confirmParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150);
        confirmParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        confirmParams.setMargins(40, 0, 40, 80);
        btnConfirm.setLayoutParams(confirmParams);
        root.addView(btnConfirm);

        mapDialog.setContentView(root);

        btnSearch.setOnClickListener(v -> {
            String query = etSearch.getText().toString().trim();
            if (query.isEmpty()) return;
            btnSearch.setText("...");
            btnSearch.setEnabled(false);

            new Thread(() -> {
                try {
                    String urlStr = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json&addressdetails=1&limit=5";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "SkillSwapApp/1.0");

                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

                    JSONArray jsonArray = new JSONArray(sb.toString());
                    List<LocationResult> results = new ArrayList<>();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        String displayName = obj.getString("display_name");
                        String[] parts = displayName.split(",", 2);

                        LocationResult res = new LocationResult();
                        res.name = parts[0].trim();
                        res.address = parts.length > 1 ? parts[1].trim() : "";
                        res.lat = obj.getDouble("lat");
                        res.lon = obj.getDouble("lon");
                        results.add(res);
                    }

                    runOnUiThread(() -> {
                        adapter.clear();
                        adapter.addAll(results);
                        adapter.notifyDataSetChanged();
                        if (!results.isEmpty()) {
                            listView.setVisibility(View.VISIBLE);
                        } else {
                            listView.setVisibility(View.GONE);
                            Toast.makeText(CreateBookingActivity.this, "No locations found.", Toast.LENGTH_SHORT).show();
                        }
                        btnSearch.setText("Find");
                        btnSearch.setEnabled(true);
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnSearch.setText("Find");
                        btnSearch.setEnabled(true);
                        Toast.makeText(CreateBookingActivity.this, "Network error.", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            LocationResult selected = adapter.getItem(position);
            if (selected != null) {
                mapController.animateTo(new GeoPoint(selected.lat, selected.lon));
                mapController.setZoom(18.0);
                listView.setVisibility(View.GONE);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        });

        btnConfirm.setOnClickListener(v -> {
            btnConfirm.setText("Saving...");
            btnConfirm.setEnabled(false);

            double centerLat = map.getMapCenter().getLatitude();
            double centerLon = map.getMapCenter().getLongitude();

            new Thread(() -> {
                try {
                    String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + centerLat + "&lon=" + centerLon + "&format=json";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "SkillSwapApp/1.0");

                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

                    JSONObject obj = new JSONObject(sb.toString());
                    String displayName = obj.getString("display_name");

                    String[] parts = displayName.split(",");
                    StringBuilder cleanName = new StringBuilder();
                    for(int p=0; p < Math.min(3, parts.length); p++) {
                        cleanName.append(parts[p]).append(p == Math.min(3, parts.length)-1 ? "" : ", ");
                    }

                    runOnUiThread(() -> {
                        selectedLocation = cleanName.toString().trim();
                        targetTextView.setText(selectedLocation);
                        targetTextView.setTextColor(getColor(R.color.text_primary));
                        mapDialog.dismiss();
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        selectedLocation = "Pinned Location (" + String.format(Locale.US, "%.4f, %.4f", centerLat, centerLon) + ")";
                        targetTextView.setText(selectedLocation);
                        targetTextView.setTextColor(getColor(R.color.text_primary));
                        mapDialog.dismiss();
                    });
                }
            }).start();
        });

        mapDialog.show();
    }
}