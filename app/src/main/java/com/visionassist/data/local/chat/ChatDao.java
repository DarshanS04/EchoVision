package com.visionassist.data.local.chat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatDao {

    @Insert
    void insertMessage(ChatMessage message);

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<ChatMessage> getHistoryBySession(String sessionId);

    @Query("SELECT * FROM (SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    List<ChatMessage> getRecentHistory(String sessionId, int limit);

    @Query("DELETE FROM chat_history WHERE sessionId = :sessionId")
    void clearSessionHistory(String sessionId);
}
