package com.skillswap.local.models;

public class SkillProvider {
    private String name;
    private String skillTitle;
    private String category;
    private float rating;
    private int sessions;
    private int showUpPercent;
    private String location;
    private boolean isSwap;
    private String price;
    private boolean isVerified;

    public SkillProvider(String name, String skillTitle, String category, float rating, int sessions, int showUpPercent, String location, boolean isSwap, String price, boolean isVerified) {
        this.name = name;
        this.skillTitle = skillTitle;
        this.category = category;
        this.rating = rating;
        this.sessions = sessions;
        this.showUpPercent = showUpPercent;
        this.location = location;
        this.isSwap = isSwap;
        this.price = price;
        this.isVerified = isVerified;
    }

    public String getName() { return name; }
    public String getSkillTitle() { return skillTitle; }
    public String getCategory() { return category; }
    public float getRating() { return rating; }
    public int getSessions() { return sessions; }
    public int getShowUpPercent() { return showUpPercent; }
    public String getLocation() { return location; }
    public boolean isSwap() { return isSwap; }
    public String getPrice() { return price; }
    public boolean isVerified() { return isVerified; }

    public String getDistance() { return location; }

    public String getStars() {
        if (rating >= 4.8) return "★★★★★";
        if (rating >= 4.0) return "★★★★☆";
        if (rating >= 3.0) return "★★★☆☆";
        if (rating >= 2.0) return "★★☆☆☆";
        if (rating >= 1.0) return "★☆☆☆☆";
        return "☆☆☆☆☆";
    }
}