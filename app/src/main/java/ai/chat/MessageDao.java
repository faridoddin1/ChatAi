package ai.chat;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import java.util.List;

@Dao
public interface MessageDao {

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY id ASC")
    List<Message> getMessagesForSession(long sessionId);

    @Insert
    long insertMessageAndGetId(Message message);

    @Update
    void updateMessage(Message message);

    @Delete
    void deleteMessage(Message message);

    @Transaction
    default void insertOrUpdate(Message message) {
        if (message.id == 0) {
            long newId = insertMessageAndGetId(message);
            message.setId((int) newId);
        } else {
            updateMessage(message);
        }
    }
}
