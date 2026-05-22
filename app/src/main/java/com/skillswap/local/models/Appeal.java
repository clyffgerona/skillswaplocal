package com.skillswap.local.models;

public class Appeal {
    public String appealId;
    public String suspendedUserId;
    public String suspendedUserName;
    public String reason;              // User's explanation for appeal
    public String evidence;            // Additional context/evidence
    public String status;              // "PENDING", "APPROVED", "REJECTED"
    public long createdAt;
    public long resolvedAt;
    public String adminNote;
    
    // No-arg constructor (required for Firestore)
    public Appeal() {}
    
    // Constructor with parameters
    public Appeal(String suspendedUserId, String suspendedUserName, 
                  String reason, String evidence) {
        this.appealId = null;          // Set by Firestore
        this.suspendedUserId = suspendedUserId;
        this.suspendedUserName = suspendedUserName;
        this.reason = reason;
        this.evidence = evidence;
        this.status = "PENDING";
        this.createdAt = System.currentTimeMillis();
        this.resolvedAt = 0;
        this.adminNote = "";
    }
}
