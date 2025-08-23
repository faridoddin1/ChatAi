package ai.chat;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ChatSessionDao {
    @Insert
    long insertSession(ChatSession session);

    @Query("SELECT * FROM chat_sessions ORDER BY lastModified DESC")
    List<ChatSession> getAllSessions();

    @Update
    void updateSession(ChatSession session);

    @Delete
    void deleteSession(ChatSession session);
}
