package ai.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessageAdapter extends ListAdapter<Message, MessageAdapter.MessageViewHolder> {

    private final Context context;
    private final OnMessageInteractionListener listener;

    public interface OnMessageInteractionListener {
        void onMessageLongClick(Message message);
    }

    public MessageAdapter(Context context, OnMessageInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == MainActivity.MESSAGE_TYPE_USER) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_user, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_ai, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            Bundle payload = (Bundle) payloads.get(0);
            if (payload.containsKey("KEY_TEXT")) {
                String newText = payload.getString("KEY_TEXT");
                holder.messageText.setText(newText);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = getItem(position);
        holder.bind(message, listener);
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ImageView copyButton;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textViewMessage);
            copyButton = itemView.findViewById(R.id.imageViewCopy);
        }

        void bind(final Message message, final OnMessageInteractionListener listener) {
            messageText.setText(message.getText());

            itemView.setOnLongClickListener(v -> {
                listener.onMessageLongClick(message);
                return true;
            });

            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("chat_message", message.getText());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "کپی شد!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<Message> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Message>() {
                @Override
                public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return oldItem.getText().equals(newItem.getText()) && oldItem.getId() == newItem.getId();
                }

                @Nullable
                @Override
                public Object getChangePayload(@NonNull Message oldItem, @NonNull Message newItem) {
                    if (!oldItem.getText().equals(newItem.getText())) {
                        Bundle diffBundle = new Bundle();
                        diffBundle.putString("KEY_TEXT", newItem.getText());
                        return diffBundle;
                    }
                    return null;
                }
            };
}
