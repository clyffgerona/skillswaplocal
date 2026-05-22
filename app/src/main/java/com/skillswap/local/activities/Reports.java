package com.skillswaplocal.models;

/** Mirrors the Firestore 'reports' collection document schema. */
public class Reports {

    public static final String REASON_NO_SHOW      = "No-Show";
    public static final String REASON_FAKE_SKILLS  = "Misrepresented Skills";
    public static final String REASON_INAPPROPRIATE= "Inappropriate Behavior";
    public static final String REASON_SCAM         = "Scam / Fraud";
    public static final String REASON_OTHER        = "Other";

    public static final String STATUS_OPEN     = "OPEN";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_RESOLVED = "RESOLVED";

    public String reportId;
    public String reporterId;
    public String reporterName;
    public String targetUserId;
    public String targetUserName;
    public String reason;
    public String details;
    public String status;       // OPEN | REVIEWED | RESOLVED
    public String adminNote;
    public long createdAt;

    // Required no-arg constructor for Firestore
    public Reports() {}

    public Reports(String reporterId, String reporterName,
                  String targetUserId, String targetUserName,
                  String reason, String details) {
        this.reporterId      = reporterId;
        this.reporterName    = reporterName;
        this.targetUserId    = targetUserId;
        this.targetUserName  = targetUserName;
        this.reason          = reason;
        this.details         = details;
        this.status          = STATUS_OPEN;
        this.adminNote       = "";
        this.createdAt       = System.currentTimeMillis();
    }
}