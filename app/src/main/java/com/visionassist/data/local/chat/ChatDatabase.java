package com.visionassist.data.local.chat;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ChatMessage.class}, version = 1, exportSchema = false)
public abstract class ChatDatabase extends RoomDatabase {

    public abstract ChatDao chatDao();

    private static volatile ChatDatabase INSTANCE;

    public static ChatDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ChatDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            ChatDatabase.class, "echovision_chat_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
