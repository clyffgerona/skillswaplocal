package com.skillswap.local.models;

import java.util.List;

public class User {

    private String uid;
    private String fullName;
    private String email;
    private String location;
    private List<String> skillsOffered;

    public boolean isSuspended = false;
    public String suspensionReason;
    public long suspensionDate;
    public int reportsCount = 0;

    // IMPORTANT: Firestore requires a completely empty constructor to read data back!
    public User() {
    }

    // This is the constructor your RegisterActivity is trying to use
    public User(String uid, String fullName, String email, String location, List<String> skillsOffered) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.location = location;
        this.skillsOffered = skillsOffered;
    }

    // --- Getters and Setters (Also required by Firestore) ---

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> getSkillsOffered() {
        return skillsOffered;
    }

    public void setSkillsOffered(List<String> skillsOffered) {
        this.skillsOffered = skillsOffered;
    }
}