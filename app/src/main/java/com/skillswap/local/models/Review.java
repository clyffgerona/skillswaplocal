package com.skillswap.local.models;

import java.util.List;

/** Mirrors the Firestore 'reviews' collection document schema. */
public class Review {

    public String reviewId;
    public String providerId;
    public String clientId;
    public String clientName;
    public String bookingId;
    public int    stars;          // 1–5
    public List<String> tags;    // e.g. ["Punctual","Friendly"]
    public String reviewText;
    public boolean isNoShowReport;
    public long createdAt;

    // Required no-arg constructor for Firestore
    public Review() {}

    public Review(String providerId, String clientId, String clientName,
                  String bookingId, int stars, List<String> tags,
                  String reviewText, boolean isNoShowReport) {
        this.providerId     = providerId;
        this.clientId       = clientId;
        this.clientName     = clientName;
        this.bookingId      = bookingId;
        this.stars          = stars;
        this.tags           = tags;
        this.reviewText     = reviewText;
        this.isNoShowReport = isNoShowReport;
        this.createdAt      = System.currentTimeMillis();
    }
}