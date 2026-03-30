package com.vitalflow.app.ui.chatbot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.gemini.ChatMessage;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.enums.UploadType;

public class FloatingChatManager {

    private final AppCompatActivity activity;
    private final GeminiRepository geminiRepository;

    // UI Components
    private FrameLayout overlayRoot;
    private FloatingActionButton fabBubble;
    private CardView chatWindow;
    private RecyclerView rvChat;
    private EditText etInput;
    private ImageButton btnAttach, btnClose;
    private FloatingActionButton btnSend;
    private FrameLayout attachmentPreviewContainer;
    private ImageView imgAttachmentPreview;
    private ImageButton btnCloseAttachment;

    private ChatAdapter adapter;
    private boolean isChatOpen = false;

    // Drag Variables
    private float dX, dY;
    private float startX, startY;

    private final int clickDragTolerance;

    private boolean shouldShow = true;

    // Attachment State
    private Uri selectedFileUri;
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    // Back press callback
    private OnBackPressedCallback backCallback;

    public FloatingChatManager(AppCompatActivity activity, GeminiRepository geminiRepository) {
        this.activity = activity;
        this.geminiRepository = geminiRepository;

        float density = activity.getResources().getDisplayMetrics().density;
        this.clickDragTolerance = (int) (10 * density);

        // Initialize Android Photo Picker (No Permissions Needed!)
        pickMedia = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        setAttachment(uri);
                    }
                }
        );

        setupBackPressHandler();
    }

    private void setupBackPressHandler() {
        backCallback = new OnBackPressedCallback(false) { // Initially disabled
            @Override
            public void handleOnBackPressed() {
                closeChatWindow();
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    public void setVisibility(boolean visible) {
        this.shouldShow = visible;
        if (overlayRoot != null) {
            overlayRoot.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void attachToActivity() {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        View overlayView = activity.getLayoutInflater().inflate(R.layout.layout_floating_chat, rootView, false);
        rootView.addView(overlayView);

        initViews(overlayView);
        setupDragBehavior();
        setupChatLogic();
    }

    private void initViews(View root) {
        overlayRoot = root.findViewById(R.id.chatOverlayRoot);
        fabBubble = root.findViewById(R.id.fabChatBubble);
        chatWindow = root.findViewById(R.id.chatWindow);
        rvChat = root.findViewById(R.id.rvChatMessages);
        etInput = root.findViewById(R.id.etChatMessage);
        btnAttach = root.findViewById(R.id.btnAttach);
        btnClose = root.findViewById(R.id.btnCloseChat);
        btnSend = root.findViewById(R.id.btnSendChat);
        attachmentPreviewContainer = root.findViewById(R.id.attachmentPreviewContainer);
        imgAttachmentPreview = root.findViewById(R.id.imgAttachmentPreview);
        btnCloseAttachment = root.findViewById(R.id.btnCloseAttachment);

        overlayRoot.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDragBehavior() {
        fabBubble.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    startX = event.getRawX();
                    startY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    view.animate()
                            .x(event.getRawX() + dX)
                            .y(event.getRawY() + dY)
                            .setDuration(0)
                            .start();
                    return true;

                case MotionEvent.ACTION_UP:
                    float endX = event.getRawX();
                    float endY = event.getRawY();
                    if (Math.abs(endX - startX) < clickDragTolerance && Math.abs(endY - startY) < clickDragTolerance) {
                        openChatWindow(); // It was a click
                    } else {
                        snapToEdge(view); // It was a drag, snap it
                    }
                    return true;
            }
            return false;
        });
    }

    private void snapToEdge(View view) {
        int screenWidth = overlayRoot.getWidth();
        float viewX = view.getX();

        // Snap to Left or Right
        float targetX = (viewX + view.getWidth() / 2f) < (screenWidth / 2f) ?
                24f : (screenWidth - view.getWidth() - 24f);

        view.animate().x(targetX).setDuration(300).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void setupChatLogic() {
        // RecyclerView
        adapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(activity));
        rvChat.setAdapter(adapter);
        adapter.addMessage(new ChatMessage("Hello! I am VitalBot. How can I help you today?", false, UploadType.TEXT));

        // Click Listeners
        btnClose.setOnClickListener(v -> closeChatWindow());

        btnAttach.setOnClickListener(v ->
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build())
        );

        btnCloseAttachment.setOnClickListener(v -> clearAttachment());

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty() && selectedFileUri == null) return;

            sendMessage(text, selectedFileUri);
        });
    }

    private void sendMessage(String text, Uri imageUri) {
        ChatMessage userMsg = new ChatMessage(text, true, imageUri != null ? UploadType.IMAGE : UploadType.TEXT);
        if (imageUri != null) userMsg.setImageUri(imageUri);
        adapter.addMessage(userMsg);
        rvChat.smoothScrollToPosition(adapter.getItemCount() - 1);

        etInput.setText("");
        clearAttachment();
        hideKeyboard();

        ChatMessage loadingMsg = new ChatMessage("", false, UploadType.TEXT);
        loadingMsg.setLoading(true);
        adapter.addMessage(loadingMsg);

        rvChat.postDelayed(() -> {
            rvChat.smoothScrollToPosition(adapter.getItemCount() - 1);
        }, 150);

        geminiRepository.sendMessage(text, imageUri, new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                activity.runOnUiThread(() -> {
                    adapter.removeLoading();
                    adapter.addMessage(new ChatMessage(response, false, UploadType.TEXT));
                    rvChat.smoothScrollToPosition(adapter.getItemCount() - 1);
                });
            }

            @Override
            public void onError(Throwable t) {
                activity.runOnUiThread(() -> {
                    adapter.removeLoading();
                    String msg = t.getMessage();
                    if (t instanceof SecurityException ||
                            (msg != null && msg.contains("API_KEY_MISSING"))) {

                        Toast.makeText(activity, "Gemini Key Required!", Toast.LENGTH_SHORT).show();

                        GeminiKeyBottomSheet sheet = new GeminiKeyBottomSheet();
                        sheet.show(activity.getSupportFragmentManager(), "GeminiKey");

                    } else {
                        Toast.makeText(activity, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // --- HELPER METHODS ---

    private void setAttachment(Uri uri) {
        this.selectedFileUri = uri;
        attachmentPreviewContainer.setVisibility(View.VISIBLE);
        Glide.with(activity).load(uri).into(imgAttachmentPreview);
    }

    private void clearAttachment() {
        this.selectedFileUri = null;
        attachmentPreviewContainer.setVisibility(View.GONE);
        imgAttachmentPreview.setImageDrawable(null);
    }

    private void openChatWindow() {
        if (isChatOpen) return;

        // Circular Reveal
        int cx = (int) (fabBubble.getX() + (float) fabBubble.getWidth() / 2);
        int cy = (int) (fabBubble.getY() + (float) fabBubble.getHeight() / 2);
        float finalRadius = (float) Math.hypot(chatWindow.getWidth(), chatWindow.getHeight());

        fabBubble.setVisibility(View.INVISIBLE);
        chatWindow.setVisibility(View.VISIBLE);

        Animator anim = ViewAnimationUtils.createCircularReveal(chatWindow, cx, cy, 0f, finalRadius);
        anim.setDuration(300);
        anim.start();

        isChatOpen = true;
    }

    private void closeChatWindow() {
        if (!isChatOpen) return;

        hideKeyboard();

        int cx = (int) (fabBubble.getX() + (float) fabBubble.getWidth() / 2);
        int cy = (int) (fabBubble.getY() + (float) fabBubble.getHeight() / 2);
        float startRadius = (float) Math.hypot(chatWindow.getWidth(), chatWindow.getHeight());

        Animator anim = ViewAnimationUtils.createCircularReveal(chatWindow, cx, cy, startRadius, 0f);
        anim.setDuration(300);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                chatWindow.setVisibility(View.INVISIBLE);
                fabBubble.setVisibility(View.VISIBLE);
            }
        });
        anim.start();

        isChatOpen = false;
        backCallback.setEnabled(false);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
    }
}
