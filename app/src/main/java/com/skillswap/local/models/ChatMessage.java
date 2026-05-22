package com.skillswap.local.models;

public class ChatMessage {

    public enum Type {
        INCOMING,
        OUTGOING,
        BOOKING_CARD
    }

    private Type type;
    private String text;
    private String time;

    // Booking card fields (used when type == BOOKING_CARD)
    private String service;
    private String date;
    private String location;
    private String exchange;
    private String status;

    // Text message constructor
    public ChatMessage(Type type, String text, String time) {
        this.type = type;
        this.text = text;
        this.time = time;
    }

    // Booking card constructor
    public ChatMessage(String service, String date, String location,
                       String exchange, String status) {
        this.type = Type.BOOKING_CARD;
        this.service = service;
        this.date = date;
        this.location = location;
        this.exchange = exchange;
        this.status = status;
    }

    public Type getType()     { return type; }
    public String getText()   { return text; }
    public String getTime()   { return time; }
    public String getService()  { return service; }
    public String getDate()     { return date; }
    public String getLocation() { return location; }
    public String getExchange() { return exchange; }
    public String getStatus()   { return status; }
}
