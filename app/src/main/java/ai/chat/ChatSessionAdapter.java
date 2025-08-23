package ai.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.SessionViewHolder> {

    private final List<ChatSession> sessions;
    private final OnSessionInteractionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    public interface OnSessionInteractionListener {
        void onSessionClick(ChatSession session);
        void onSessionLongClick(ChatSession session);
    }

    public ChatSessionAdapter(List<ChatSession> sessions, OnSessionInteractionListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.drawer_item, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.bind(session, listener);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView timestamp;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textViewTitle);
            timestamp = itemView.findViewById(R.id.textViewTimestamp);
        }

        public void bind(final ChatSession session, final OnSessionInteractionListener listener) {
            title.setText(session.getTitle());
            timestamp.setText(dateFormat.format(new Date(session.getLastModified())));

            itemView.setOnClickListener(v -> listener.onSessionClick(session));

            itemView.setOnLongClickListener(v -> {
                listener.onSessionLongClick(session);
                return true;
            });
        }
    }
}
