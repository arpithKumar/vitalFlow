package com.vitalflow.app.data.entity.gemini;

import android.net.Uri;

import com.vitalflow.app.enums.UploadType;

public class ChatMessage {

    private String id;
    private String text;
    private boolean isUser;
    private UploadType type;
    private Uri imageUri;
    private boolean isLoading;

    public ChatMessage(String text, boolean isUser, UploadType type) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.text = text;
        this.isUser = isUser;
        this.type = type;
        this.isLoading = false;
    }

    public void setImageUri(Uri uri) {
        this.imageUri = uri;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public UploadType getType() {
        return type;
    }

    public Uri getImageUri() {
        return imageUri;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }
}
