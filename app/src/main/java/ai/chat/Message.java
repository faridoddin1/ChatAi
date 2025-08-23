package ai.chat;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages",
        foreignKeys = @ForeignKey(entity = ChatSession.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE))
public class Message {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "session_id", index = true)
    public long sessionId;

    private String text;
    private int type;

    public Message() {}

    @Ignore
    public Message(long sessionId, String text, int type) {
        this.sessionId = sessionId;
        this.text = text;
        this.type = type;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getSessionId() { return sessionId; }
    public void setSessionId(long sessionId) { this.sessionId = sessionId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
}
