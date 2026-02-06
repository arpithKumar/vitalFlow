package com.vitalflow.app.ui.chatbot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.gemini.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;
    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void removeLoading() {
        // Find the last message (usually the loading one) and remove if it is marked loading
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            if (messages.get(lastIndex).isLoading()) {
                messages.remove(lastIndex);
                notifyItemRemoved(lastIndex);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_bot, parent, false);
            return new BotViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(message);
        } else if (holder instanceof BotViewHolder) {
            ((BotViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // --- VIEW HOLDERS ---

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        ImageView imgBody;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageBody);
            imgBody = itemView.findViewById(R.id.imgMessageBody);
        }

        void bind(ChatMessage message) {
            // Text Handling
            if (message.getText() != null && !message.getText().isEmpty()) {
                tvText.setVisibility(View.VISIBLE);
                tvText.setText(message.getText());
            } else {
                tvText.setVisibility(View.GONE);
            }

            // Image Handling
            if (message.getImageUri() != null) {
                imgBody.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getImageUri())
                        .transform(new MultiTransformation<>(new CenterCrop(), new RoundedCorners(24)))
                        .into(imgBody);
            } else {
                imgBody.setVisibility(View.GONE);
            }
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        LottieAnimationView lottieLoader;

        BotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageBody);
            lottieLoader = itemView.findViewById(R.id.lottieLoading);
        }

        void bind(ChatMessage message) {
            if (message.isLoading()) {
                tvText.setText("");
                tvText.setVisibility(View.GONE);
                lottieLoader.setVisibility(View.VISIBLE);
                lottieLoader.playAnimation();
            } else {
                lottieLoader.cancelAnimation();
                lottieLoader.setVisibility(View.GONE);
                tvText.setVisibility(View.VISIBLE);
                tvText.setText(message.getText());
            }
        }
    }
}
