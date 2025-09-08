package ai.chat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        ChatSessionAdapter.OnSessionInteractionListener,
        MessageAdapter.OnMessageInteractionListener {

    private static final String TAG = "MainActivity";
    private static final String WORKER_URL = "https://chatai-worker.fa-ra9931143.workers.dev";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    public static final int MESSAGE_TYPE_USER = 0;
    public static final int MESSAGE_TYPE_AI = 1;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private RecyclerView recyclerViewChat;
    private EditText editTextPrompt;
    private ImageButton buttonSend;
    private RecyclerView recyclerViewSessions;

    private MessageAdapter messageAdapter;
    private List<ChatSession> sessionList;
    private ChatSessionAdapter sessionAdapter;
    private long currentSessionId = -1;
    private boolean isNewSession = false;

    private OkHttpClient httpClient;
    private Gson gson;
    private Handler mainHandler;
    private AppDatabase db;
    private ExecutorService databaseExecutor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupToolbarAndDrawer();
        initializeChat();
        initializeHttpClient();

        databaseExecutor = Executors.newSingleThreadExecutor();
        db = AppDatabase.getDatabase(this);

        setupSessionList();
        loadChatSessions();

        buttonSend.setOnClickListener(v -> handleSendClick());
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
    }

    private void setupToolbarAndDrawer() {
        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void initializeChat() {
        messageAdapter = new MessageAdapter(this, this);
        recyclerViewChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChat.setAdapter(messageAdapter);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private void initializeHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
    }

    private void setupSessionList() {
        navigationView.getMenu().clear();
        navigationView.inflateMenu(R.menu.drawer_menu);

        View headerView = navigationView.getHeaderView(0);
        recyclerViewSessions = headerView.findViewById(R.id.recyclerViewSessions);

        sessionList = new ArrayList<>();
        sessionAdapter = new ChatSessionAdapter(sessionList, this);
        recyclerViewSessions.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSessions.setAdapter(sessionAdapter);
    }

    private void loadChatSessions() {
        databaseExecutor.execute(() -> {
            List<ChatSession> sessions = db.chatSessionDao().getAllSessions();
            mainHandler.post(() -> {
                sessionList.clear();
                sessionList.addAll(sessions);
                sessionAdapter.notifyDataSetChanged();

                if (currentSessionId == -1) {
                    if (sessions.isEmpty()) {
                        createNewChatSession();
                    } else {
                        loadChatForSession(sessions.get(0));
                    }
                }
            });
        });
    }

    private void createNewChatSession() {
        isNewSession = true;
        ChatSession tempSession = new ChatSession("چت جدید");
        tempSession.setId(-1);
        loadChatForSession(tempSession);
    }

    private void loadChatForSession(ChatSession session) {
        currentSessionId = session.getId();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(session.getTitle());
        }

        if (currentSessionId == -1) {
            messageAdapter.submitList(new ArrayList<>());
            isNewSession = true;
            return;
        }

        isNewSession = false;
        databaseExecutor.execute(() -> {
            List<Message> messages = db.messageDao().getMessagesForSession(currentSessionId);
            mainHandler.post(() -> {
                messageAdapter.submitList(messages);
                if (!messages.isEmpty()) {
                    recyclerViewChat.scrollToPosition(messages.size() - 1);
                }
            });
        });
    }

    private void handleSendClick() {
        String prompt = editTextPrompt.getText().toString().trim();
        if (prompt.isEmpty() || !buttonSend.isEnabled()) return;

        editTextPrompt.setText("");
        setSendButtonEnabled(false);

        if (isNewSession) {
            String title = prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
            ChatSession newSession = new ChatSession(title);

            databaseExecutor.execute(() -> {
                long newId = db.chatSessionDao().insertSession(newSession);
                newSession.setId(newId);

                mainHandler.post(() -> {
                    currentSessionId = newId;
                    isNewSession = false;

                    loadChatSessions();

                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(title);
                    }

                    addMessage(prompt, MESSAGE_TYPE_USER);
                    sendRequestToWorker(prompt);
                });
            });
        } else {
            addMessage(prompt, MESSAGE_TYPE_USER);
            sendRequestToWorker(prompt);
        }
    }

    private void addMessage(String text, int type) {
        if (currentSessionId == -1) return;
        Message message = new Message(currentSessionId, text, type);

        List<Message> currentList = new ArrayList<>(messageAdapter.getCurrentList());
        currentList.add(message);
        messageAdapter.submitList(currentList);
        recyclerViewChat.scrollToPosition(currentList.size() - 1);

        databaseExecutor.execute(() -> {
            db.messageDao().insertOrUpdate(message);
            ChatSession sessionToUpdate = null;
            for(ChatSession s : sessionList) {
                if (s.getId() == currentSessionId) {
                    sessionToUpdate = s;
                    break;
                }
            }
            if (sessionToUpdate != null) {
                sessionToUpdate.setLastModified(System.currentTimeMillis());
                db.chatSessionDao().updateSession(sessionToUpdate);
            }
        });
    }

    private void sendRequestToWorker(String prompt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("prompt", prompt);
        payload.addProperty("task", "analyze");

        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request request = new Request.Builder().url(WORKER_URL).post(body).build();

        addTypingIndicator();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Request failed: ", e);
                updateAiMessage("خطا در ارتباط با سرور: " + e.getMessage(), true);
                setSendButtonEnabled(true);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        updateAiMessage("خطا: " + response.code(), true);
                        return;
                    }
                    handleStreamingResponse(responseBody);
                } finally {
                    setSendButtonEnabled(true);
                }
            }
        });
    }

    private void handleStreamingResponse(ResponseBody responseBody) {
        StringBuilder fullResponse = new StringBuilder();
        try (BufferedSource source = responseBody.source()) {
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line != null && line.startsWith("data: ")) {
                    String jsonString = line.substring(6).trim();
                    if ("[DONE]".equals(jsonString)) break;
                    try {
                        Map<String, String> data = gson.fromJson(jsonString, Map.class);
                        if (data != null && data.containsKey("response")) {
                            fullResponse.append(data.get("response"));
                            updateAiMessage(fullResponse.toString(), false);
                        }
                    } catch (JsonSyntaxException e) {
                        Log.w(TAG, "JSON parsing error: " + jsonString, e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading stream: ", e);
            updateAiMessage("خطا در پردازش پاسخ.", true);
        }
        updateAiMessage(fullResponse.toString(), true);
    }

    private void addTypingIndicator() {
        mainHandler.post(() -> addMessage("...", MESSAGE_TYPE_AI));
    }

    private void updateAiMessage(String newText, boolean isFinal) {
        mainHandler.post(() -> {
            List<Message> currentList = messageAdapter.getCurrentList();
            if (currentList.isEmpty()) return;

            ArrayList<Message> newList = new ArrayList<>(currentList);
            int lastIndex = newList.size() - 1;
            Message lastMessage = newList.get(lastIndex);

            if (lastMessage.getType() == MESSAGE_TYPE_AI) {
                Message updatedMessage = new Message();
                updatedMessage.setId(lastMessage.getId());
                updatedMessage.setSessionId(lastMessage.getSessionId());
                updatedMessage.setType(lastMessage.getType());
                updatedMessage.setText(newText);

                newList.set(lastIndex, updatedMessage);
                messageAdapter.submitList(newList, () -> {
                    if (recyclerViewChat.isComputingLayout()) return;
                    recyclerViewChat.scrollToPosition(lastIndex);
                });

                if (isFinal) {
                    databaseExecutor.execute(() -> db.messageDao().insertOrUpdate(updatedMessage));
                }
            }
        });
    }

    private void setSendButtonEnabled(boolean enabled) {
        mainHandler.post(() -> buttonSend.setEnabled(enabled));
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.nav_new_chat) {
            createNewChatSession();
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSessionClick(ChatSession session) {
        loadChatForSession(session);
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    @Override
    public void onSessionLongClick(ChatSession session) {
        showDeleteConfirmationDialog(
                "حذف گفتگو",
                "آیا از حذف این گفتگو مطمئن هستید؟",
                () -> deleteChatSession(session)
        );
    }

    @Override
    public void onMessageLongClick(Message message) {
        showDeleteConfirmationDialog(
                "حذف پیام",
                "آیا از حذف این پیام مطمئن هستید؟",
                () -> deleteMessage(message)
        );
    }

    private void deleteChatSession(ChatSession session) {
        databaseExecutor.execute(() -> {
            db.chatSessionDao().deleteSession(session);
            mainHandler.post(() -> {
                if (currentSessionId == session.getId()) {
                    currentSessionId = -1;
                }
                loadChatSessions();
            });
        });
    }

    private void deleteMessage(Message message) {
        databaseExecutor.execute(() -> {
            db.messageDao().deleteMessage(message);
            mainHandler.post(() -> {
                List<Message> currentList = new ArrayList<>(messageAdapter.getCurrentList());
                currentList.removeIf(m -> m.getId() == message.getId());
                messageAdapter.submitList(currentList);
            });
        });
    }

    private void showDeleteConfirmationDialog(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("بله", (dialog, which) -> onConfirm.run())
                .setNegativeButton("خیر", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
