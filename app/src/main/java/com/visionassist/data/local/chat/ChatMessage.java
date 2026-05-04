package com.visionassist.data.local.chat;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_history")
public class ChatMessage {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String role; // "user" or "model"
    private String content;
    private long timestamp;
    private String sessionId;

    public ChatMessage(String role, String content, long timestamp, String sessionId) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
