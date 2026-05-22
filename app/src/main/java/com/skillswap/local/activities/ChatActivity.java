package com.skillswap.local.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.skillswap.local.R;
import com.skillswap.local.models.ChatMessage;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private LinearLayout llMessages;
    private ScrollView scrollChat;
    private EditText etMessage;
    private TextView btnSend, btnBack, tvChatName, tvChatAvatar, btnMakeRequest, btnAttach;
    private ImageView ivChatAvatar;
    private ProgressBar loadingSpinner;

    private String receiverName;
    private String receiverUid;
    private String chatRoomId;

    private ArrayList<String> receiverSkills = new ArrayList<>();
    private List<DocumentSnapshot> lastSnapshots = new ArrayList<>();
    private List<Uri> pendingUploads = new ArrayList<>();

    private final String CLOUD_NAME = "dykygrydm";
    private final String API_KEY = "995585552577712";
    private final String API_SECRET = "MrId8H8hwOzVVjjYr5_GNYqQZFw";

    private final ActivityResultLauncher<Intent> mediaPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri mediaUri = result.getData().getData();
                    if (mediaUri != null) checkAndUploadMedia(mediaUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", CLOUD_NAME);
            config.put("api_key", API_KEY);
            config.put("api_secret", API_SECRET);
            MediaManager.init(this, config);
        } catch (Exception e) {}

        receiverName = getIntent().getStringExtra("provider_name");
        if (receiverName == null || receiverName.isEmpty()) receiverName = "Unknown Provider";

        initViews();
        findReceiverUidAndStartChat();

        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        btnMakeRequest.setOnClickListener(v -> {
            if (lastSnapshots == null || lastSnapshots.isEmpty()) {
                Toast.makeText(this, "Please send a message first to discuss details before booking.", Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(ChatActivity.this, CreateBookingActivity.class);
            intent.putExtra("provider_uid", receiverUid);
            intent.putExtra("provider_name", receiverName);
            intent.putExtra("chat_room_id", chatRoomId);
            intent.putStringArrayListExtra("provider_skills", receiverSkills);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        // -------------------------------------------------------------
        // NEW: Header Clicks Open Provider Profile
        // -------------------------------------------------------------
        tvChatName.setOnClickListener(v -> openProviderProfile());
        ivChatAvatar.setOnClickListener(v -> openProviderProfile());
        tvChatAvatar.setOnClickListener(v -> openProviderProfile());

        btnAttach.setOnClickListener(v -> openMediaPicker());

        setupBottomNav();
    }

    private void initViews() {
        llMessages = findViewById(R.id.llMessages);
        scrollChat = findViewById(R.id.scrollChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        tvChatName = findViewById(R.id.tvChatName);
        tvChatAvatar = findViewById(R.id.tvChatAvatar);
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        btnMakeRequest = findViewById(R.id.btnMakeRequest);
        btnAttach = findViewById(R.id.btnAttach);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        loadingSpinner.setVisibility(View.VISIBLE);

        tvChatName.setText(receiverName);
        tvChatAvatar.setText(getInitials(receiverName));
    }

    private void openProviderProfile() {
        Intent intent = new Intent(ChatActivity.this, ProviderProfileActivity.class);
        intent.putExtra("provider_name", receiverName);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void findReceiverUidAndStartChat() {
        db.collection("Users").whereEqualTo("fullName", receiverName).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        receiverUid = doc.getId();

                        List<String> skills = (List<String>) doc.get("skillsOffered");
                        if (skills != null && !skills.isEmpty()) receiverSkills.addAll(skills);
                        else receiverSkills.add("General Service");

                        if (currentUser.getUid().equals(receiverUid)) {
                            Toast.makeText(this, "You cannot chat with yourself.", Toast.LENGTH_SHORT).show();
                            etMessage.setEnabled(false);
                            btnMakeRequest.setEnabled(false);
                            btnAttach.setEnabled(false);
                            loadingSpinner.setVisibility(View.GONE);
                            return;
                        }

                        String photoUrl = doc.getString("profilePhotoUrl");

                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ivChatAvatar.setVisibility(View.VISIBLE);
                            tvChatAvatar.setVisibility(View.GONE);
                            Glide.with(ChatActivity.this).load(photoUrl).apply(RequestOptions.bitmapTransform(new CircleCrop())).into(ivChatAvatar);
                        } else {
                            ivChatAvatar.setVisibility(View.GONE);
                            tvChatAvatar.setVisibility(View.VISIBLE);
                        }

                        chatRoomId = generateChatRoomId(currentUser.getUid(), receiverUid);
                        markAsRead();
                        listenForMessages();
                    }
                })
                .addOnFailureListener(e -> loadingSpinner.setVisibility(View.GONE));
    }

    private String generateChatRoomId(String uid1, String uid2) {
        return (uid1.compareTo(uid2) < 0) ? uid1 + "_" + uid2 : uid2 + "_" + uid1;
    }

    private void updateChatThread(String lastMessage) {
        Map<String, Object> threadMap = new HashMap<>();
        threadMap.put("participants", Arrays.asList(currentUser.getUid(), receiverUid));
        threadMap.put("lastMessage", lastMessage);
        threadMap.put("lastUpdated", new Date());
        threadMap.put("lastSenderUid", currentUser.getUid());
        threadMap.put("seenBy", Arrays.asList(currentUser.getUid()));
        db.collection("Chats").document(chatRoomId).set(threadMap, SetOptions.merge());
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) return;
        if (chatRoomId == null || receiverUid == null) return;

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("senderUid", currentUser.getUid());
        messageMap.put("receiverUid", receiverUid);
        messageMap.put("message", messageText);
        messageMap.put("type", "TEXT");
        messageMap.put("timestamp", new Date());

        etMessage.setText("");

        db.collection("Chats").document(chatRoomId).collection("Messages").add(messageMap)
                .addOnSuccessListener(aVoid -> updateChatThread(messageText));
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        mediaPickerLauncher.launch(intent);
    }

    private void checkAndUploadMedia(Uri mediaUri) {
        long fileSize = getFileSize(mediaUri);
        if (fileSize > (50 * 1024 * 1024)) return;

        pendingUploads.add(mediaUri);
        redrawAllMessages();

        String mimeType = getContentResolver().getType(mediaUri);
        String targetFolder = "";
        String resourceType = "auto";

        if (mimeType != null && mimeType.startsWith("video")) {
            targetFolder = "SkillSwap Local/Message Videos";
            resourceType = "video";
        } else {
            targetFolder = "SkillSwap Local/Message Pictures";
            resourceType = "image";
        }

        MediaManager.get().upload(mediaUri)
                .option("folder", targetFolder)
                .option("resource_type", resourceType)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        String format = (String) resultData.get("resource_type");
                        runOnUiThread(() -> { pendingUploads.remove(mediaUri); saveMediaToFirestore(secureUrl, format); });
                    }
                    @Override public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> { pendingUploads.remove(mediaUri); redrawAllMessages(); });
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void saveMediaToFirestore(String mediaUrl, String resourceType) {
        String dbType = resourceType.equals("video") ? "VIDEO" : "IMAGE";
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("senderUid", currentUser.getUid());
        messageMap.put("receiverUid", receiverUid);
        messageMap.put("message", "");
        messageMap.put("mediaUrl", mediaUrl);
        messageMap.put("type", dbType);
        messageMap.put("timestamp", new Date());

        db.collection("Chats").document(chatRoomId).collection("Messages").add(messageMap)
                .addOnSuccessListener(aVoid -> updateChatThread("Sent a " + resourceType));
    }

    private long getFileSize(Uri uri) {
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) { long size = cursor.getLong(sizeIndex); cursor.close(); return size; }
                cursor.close();
            }
        } catch (Exception e) {}
        return 0;
    }

    private void markAsRead() {
        if (chatRoomId == null || currentUser == null) return;
        db.collection("Chats").document(chatRoomId)
                .update("seenBy", FieldValue.arrayUnion(currentUser.getUid()));
    }

    private void listenForMessages() {
        db.collection("Chats").document(chatRoomId).collection("Messages").orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        loadingSpinner.setVisibility(View.GONE);
                        lastSnapshots = value.getDocuments();
                        redrawAllMessages();
                    }
                });
    }

    private void redrawAllMessages() {
        llMessages.removeAllViews();
        for (DocumentSnapshot doc : lastSnapshots) {
            try {
                List<String> deletedBy = (List<String>) doc.get("deletedBy");
                if (deletedBy != null && deletedBy.contains(currentUser.getUid())) continue;

                String msgType = doc.getString("type");
                String messageId = doc.getId();
                String senderId = doc.getString("senderUid");
                boolean isMine = senderId != null && senderId.equals(currentUser.getUid());

                if (msgType != null && msgType.equals("BOOKING_CARD")) {
                    ChatMessage cardMsg = new ChatMessage(doc.getString("service"), doc.getString("date"), doc.getString("location"), doc.getString("exchange"), doc.getString("status"));
                    appendBookingCardUI(cardMsg, messageId, doc.getDate("timestamp"), isMine);
                } else {
                    if (senderId != null) appendMessageUI(doc.getString("message"), doc.getString("mediaUrl"), msgType, formatTime(doc.getDate("timestamp")), isMine, messageId, doc.getDate("timestamp"));
                }
            } catch (Exception e) { }
        }
        for (Uri pendingUri : pendingUploads) appendPendingMediaUI(pendingUri);
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }

    private void appendBookingCardUI(ChatMessage msg, String messageId, Date timestamp, boolean isMine) {
        View msgView = LayoutInflater.from(this).inflate(isMine ? R.layout.item_message_outgoing : R.layout.item_message_incoming, llMessages, false);
        TextView tvText = msgView.findViewById(R.id.tvMsgText);
        TextView tvTime = msgView.findViewById(R.id.tvMsgTime);

        tvText.setVisibility(View.VISIBLE);

        if (isMine) {
            tvText.setText("You sent a booking request for " + msg.getService() + ".\n\nTap to view details.");
        } else {
            tvText.setText(receiverName + " sent a booking request for " + msg.getService() + ".\n\nTap to view details.");
        }

        tvText.setTypeface(null, android.graphics.Typeface.ITALIC);
        tvTime.setText(formatTime(timestamp));

        msgView.setOnClickListener(v -> {
            Intent intent = new Intent(ChatActivity.this, MyBookingsActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        llMessages.addView(msgView);
    }

    private void appendMessageUI(String text, String mediaUrl, String msgType, String time, boolean isMine, String messageId, Date timestamp) {
        View msgView = LayoutInflater.from(this).inflate(isMine ? R.layout.item_message_outgoing : R.layout.item_message_incoming, llMessages, false);
        TextView tvText = msgView.findViewById(R.id.tvMsgText);
        TextView tvTime = msgView.findViewById(R.id.tvMsgTime);

        if (text != null && !text.isEmpty()) { tvText.setText(text); tvText.setVisibility(View.VISIBLE); } else { tvText.setVisibility(View.GONE); }
        tvTime.setText(time);

        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            FrameLayout mediaContainer = new FrameLayout(this);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams((int) (200 * getResources().getDisplayMetrics().density), (int) (200 * getResources().getDisplayMetrics().density));
            containerParams.setMargins(0, 4, 0, 4);
            mediaContainer.setLayoutParams(containerParams);
            ImageView ivMedia = new ImageView(this);
            ivMedia.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ivMedia.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivMedia.setClipToOutline(true);
            mediaContainer.addView(ivMedia);

            if ("VIDEO".equals(msgType)) {
                Glide.with(this).load(mediaUrl.replace(".mp4", ".jpg").replace(".mov", ".jpg")).into(ivMedia);
                TextView playIcon = new TextView(this); playIcon.setText("▶"); playIcon.setTextColor(Color.WHITE); playIcon.setTextSize(48f); playIcon.setGravity(Gravity.CENTER); playIcon.setShadowLayer(5, 0, 0, Color.BLACK);
                playIcon.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                mediaContainer.addView(playIcon);
                mediaContainer.setOnClickListener(v -> showFullScreenVideo(mediaUrl));
            } else {
                Glide.with(this).load(mediaUrl).into(ivMedia);
                mediaContainer.setOnClickListener(v -> showFullScreenImage(mediaUrl));
            }
            mediaContainer.setOnLongClickListener(v -> { showOptionsDialog(text, mediaUrl, msgType, isMine, messageId, timestamp); return true; });
            ((LinearLayout) tvText.getParent()).addView(mediaContainer, ((LinearLayout) tvText.getParent()).indexOfChild(tvTime));
        }

        msgView.setOnLongClickListener(v -> { showOptionsDialog(text, mediaUrl, msgType, isMine, messageId, timestamp); return true; });
        llMessages.addView(msgView);
    }

    private void appendPendingMediaUI(Uri uri) {
        View msgView = LayoutInflater.from(this).inflate(R.layout.item_message_outgoing, llMessages, false);
        TextView tvText = msgView.findViewById(R.id.tvMsgText);
        TextView tvTime = msgView.findViewById(R.id.tvMsgTime);
        tvText.setVisibility(View.GONE);
        tvTime.setText("Sending...");

        ImageView ivMedia = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (200 * getResources().getDisplayMetrics().density), (int) (200 * getResources().getDisplayMetrics().density));
        params.setMargins(0, 4, 0, 4);
        ivMedia.setLayoutParams(params);
        ivMedia.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivMedia.setClipToOutline(true);
        ivMedia.setAlpha(0.5f);
        Glide.with(this).load(uri).into(ivMedia);

        ((LinearLayout) tvText.getParent()).addView(ivMedia, ((LinearLayout) tvText.getParent()).indexOfChild(tvTime));
        llMessages.addView(msgView);
    }

    private void showFullScreenImage(String imageUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        RelativeLayout layout = new RelativeLayout(this); layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); layout.setBackgroundColor(Color.BLACK);
        ImageView imageView = new ImageView(this); imageView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)); imageView.setScaleType(ImageView.ScaleType.FIT_CENTER); Glide.with(this).load(imageUrl).into(imageView);
        TextView btnClose = new TextView(this); btnClose.setText("X"); btnClose.setTextColor(Color.WHITE); btnClose.setTextSize(24f); btnClose.setTypeface(null, Typeface.BOLD); btnClose.setPadding(40, 40, 40, 40); RelativeLayout.LayoutParams btnParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT); btnParams.addRule(RelativeLayout.ALIGN_PARENT_TOP); btnParams.addRule(RelativeLayout.ALIGN_PARENT_END); btnClose.setLayoutParams(btnParams); btnClose.setOnClickListener(v -> dialog.dismiss());
        layout.addView(imageView); layout.addView(btnClose); dialog.setContentView(layout); dialog.show();
    }

    private void showFullScreenVideo(String videoUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        RelativeLayout layout = new RelativeLayout(this); layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); layout.setBackgroundColor(Color.BLACK);
        VideoView videoView = new VideoView(this); RelativeLayout.LayoutParams videoParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT); videoParams.addRule(RelativeLayout.CENTER_IN_PARENT); videoView.setLayoutParams(videoParams);
        MediaController mediaController = new MediaController(this); mediaController.setAnchorView(videoView); videoView.setMediaController(mediaController); videoView.setVideoURI(Uri.parse(videoUrl)); videoView.setOnPreparedListener(mp -> videoView.start());
        TextView btnClose = new TextView(this); btnClose.setText("X"); btnClose.setTextColor(Color.WHITE); btnClose.setTextSize(24f); btnClose.setTypeface(null, Typeface.BOLD); btnClose.setPadding(40, 40, 40, 40); RelativeLayout.LayoutParams btnParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT); btnParams.addRule(RelativeLayout.ALIGN_PARENT_TOP); btnParams.addRule(RelativeLayout.ALIGN_PARENT_END); btnClose.setLayoutParams(btnParams); btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> { if (videoView.isPlaying()) videoView.stopPlayback(); }); layout.addView(videoView); layout.addView(btnClose); dialog.setContentView(layout); dialog.show();
    }

    private void showOptionsDialog(String text, String mediaUrl, String msgType, boolean isMine, String messageId, Date timestamp) {
        try {
            if ("BOOKING_CARD".equals(msgType)) return;
            List<String> optionsList = new ArrayList<>();
            if (text != null && !text.isEmpty()) optionsList.add("Copy Text");
            optionsList.add("Delete for you");
            if (isMine) { long msgTime = (timestamp != null) ? timestamp.getTime() : System.currentTimeMillis(); if (System.currentTimeMillis() - msgTime <= 600000) optionsList.add("Unsend (Remove for everyone)"); } else { optionsList.add("Report to Admin"); }
            new AlertDialog.Builder(this).setTitle("Message Options").setItems(optionsList.toArray(new CharSequence[0]), (dialog, which) -> {
                String selected = optionsList.get(which);
                if (selected.equals("Copy Text")) copyToClipboard(text); else if (selected.equals("Delete for you")) deleteForMe(messageId, mediaUrl, msgType); else if (selected.equals("Unsend (Remove for everyone)")) unsendMessage(messageId, mediaUrl, msgType); else if (selected.equals("Report to Admin")) reportMessageToAdmin(messageId, text != null ? text : "Media File");
            }).show();
        } catch (Exception e) {}
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("Chat Message", text)); Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show(); }
    }

    private void deleteFromCloudinary(String mediaUrl, String resourceType) {
        if (mediaUrl == null || mediaUrl.isEmpty()) return;
        try {
            int lastSlash = mediaUrl.lastIndexOf('/'); int lastDot = mediaUrl.lastIndexOf('.');
            if (lastSlash != -1 && lastDot != -1 && lastDot > lastSlash) {
                String publicId = mediaUrl.substring(lastSlash + 1, lastDot);
                new Thread(() -> {
                    try {
                        long ts = System.currentTimeMillis() / 1000; String strToSign = "public_id=" + publicId + "&timestamp=" + ts + API_SECRET; MessageDigest md = MessageDigest.getInstance("SHA-1"); byte[] bytes = md.digest(strToSign.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for (byte b : bytes) sb.append(String.format("%02x", b));
                        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/" + ((resourceType != null && resourceType.equals("VIDEO")) ? "video" : "image") + "/destroy").openConnection(); conn.setRequestMethod("POST"); conn.setDoOutput(true); OutputStream os = conn.getOutputStream(); os.write(("public_id=" + publicId + "&timestamp=" + ts + "&api_key=" + API_KEY + "&signature=" + sb.toString()).getBytes("UTF-8")); os.flush(); os.close();
                    } catch (Exception e) {}
                }).start();
            }
        } catch (Exception e) {}
    }

    private void unsendMessage(String messageId, String mediaUrl, String msgType) { try { deleteFromCloudinary(mediaUrl, msgType); db.collection("Chats").document(chatRoomId).collection("Messages").document(messageId).delete(); } catch (Exception e) {} }
    private void deleteForMe(String messageId, String mediaUrl, String msgType) { try { db.collection("Chats").document(chatRoomId).collection("Messages").document(messageId).get().addOnSuccessListener(doc -> { if (doc.exists()) { List<String> deletedBy = (List<String>) doc.get("deletedBy"); if (deletedBy != null && deletedBy.size() > 0 && !deletedBy.contains(currentUser.getUid())) { deleteFromCloudinary(mediaUrl, msgType); db.collection("Chats").document(chatRoomId).collection("Messages").document(messageId).delete(); } else { Map<String, Object> updates = new HashMap<>(); updates.put("deletedBy", FieldValue.arrayUnion(currentUser.getUid())); db.collection("Chats").document(chatRoomId).collection("Messages").document(messageId).set(updates, SetOptions.merge()); } } }); } catch (Exception e) {} }
    private void reportMessageToAdmin(String messageId, String content) {
        db.collection("Users").document(currentUser.getUid()).get().addOnSuccessListener(doc -> {
            String reporterName = "User";
            if (doc.exists()) {
                String name = doc.getString("fullName");
                if (name != null && !name.isEmpty()) reporterName = name;
            }

            Map<String, Object> reportMap = new HashMap<>();
            reportMap.put("reporterId", currentUser.getUid());
            reportMap.put("reporterName", reporterName);
            reportMap.put("targetUserId", receiverUid);
            reportMap.put("targetUserName", receiverName);
            reportMap.put("messageId", messageId);
            reportMap.put("reason", "Inappropriate Chat Message");
            reportMap.put("details", "Message Content: " + content);
            reportMap.put("chatRoomId", chatRoomId);
            reportMap.put("timestamp", new Date());
            reportMap.put("createdAt", System.currentTimeMillis());
            reportMap.put("status", "OPEN");

            db.collection("Reports").add(reportMap)
                    .addOnSuccessListener(docRef -> Toast.makeText(this, "Reported to admin.", Toast.LENGTH_LONG).show());
        }).addOnFailureListener(e -> {
            // Fallback if user fetch fails
            Map<String, Object> reportMap = new HashMap<>();
            reportMap.put("reporterId", currentUser.getUid());
            reportMap.put("reporterName", "User");
            reportMap.put("targetUserId", receiverUid);
            reportMap.put("targetUserName", receiverName);
            reportMap.put("messageId", messageId);
            reportMap.put("reason", "Inappropriate Chat Message");
            reportMap.put("details", "Message Content: " + content);
            reportMap.put("chatRoomId", chatRoomId);
            reportMap.put("timestamp", new Date());
            reportMap.put("createdAt", System.currentTimeMillis());
            reportMap.put("status", "OPEN");

            db.collection("Reports").add(reportMap)
                    .addOnSuccessListener(docRef -> Toast.makeText(this, "Reported to admin.", Toast.LENGTH_LONG).show());
        });
    }

    private void setupBottomNav() {
        ImageView navIcon = findViewById(R.id.navMessagesIcon); if (navIcon != null) navIcon.setColorFilter(Color.WHITE); TextView navLabel = findViewById(R.id.navMessagesLabel); if (navLabel != null) navLabel.setTextColor(Color.WHITE);
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

    private String getInitials(String fullName) { if (fullName == null || fullName.trim().isEmpty()) return "?"; String[] parts = fullName.trim().split(" "); StringBuilder sb = new StringBuilder(); for (String p : parts) if (!p.isEmpty()) sb.append(p.charAt(0)); return sb.toString().toUpperCase(); }
    private String formatTime(Date date) {
        if (date == null) return "Just now";

        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar msgTime = java.util.Calendar.getInstance();
        msgTime.setTime(date);

        if (now.get(java.util.Calendar.YEAR) == msgTime.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) == msgTime.get(java.util.Calendar.DAY_OF_YEAR)) {
            return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date);
        } else {
            return new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()).format(date);
        }
    }
}