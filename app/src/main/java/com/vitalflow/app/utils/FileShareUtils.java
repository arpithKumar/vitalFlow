package com.vitalflow.app.utils;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * Creates the files the app hands to other apps (the camera, the share sheet) and the content URIs
 * for them, so the FileProvider authority and the shareable folders live in one place.
 *
 * <p>Only the folders listed in {@code res/xml/provider_paths.xml} can be shared. Keep the folder
 * names below in sync with that file.
 */
public final class FileShareUtils {

    /** Camera captures for the food and prescription scans. */
    private static final String SCANS_DIR = "scans";
    /** Excel exports that are shared right after they are written. */
    private static final String EXPORTS_DIR = "exports";
    /** Scan photos are only needed while they are analysed, so a day is plenty. */
    private static final long SCAN_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private FileShareUtils() {
    }

    /** The authority declared for the FileProvider in AndroidManifest.xml. */
    public static String authority(Context context) {
        return context.getPackageName() + ".provider";
    }

    /** Content URI that lets another app read {@code file}, which must be in a shareable folder. */
    public static Uri uriFor(Context context, File file) {
        return FileProvider.getUriForFile(context, authority(context), file);
    }

    /** Creates an empty file for a camera capture and removes scan photos older than a day. */
    public static File newScanFile(Context context, String prefix) throws IOException {
        File dir = subDir(context, SCANS_DIR);
        pruneOldFiles(dir, SCAN_MAX_AGE_MS);
        return File.createTempFile(prefix, ".jpg", dir);
    }

    /** Where an export with this name is written before it is shared. */
    public static File exportFile(Context context, String fileName) {
        return new File(subDir(context, EXPORTS_DIR), fileName);
    }

    /** Deletes the files in {@code dir} that were last modified more than {@code maxAgeMs} ago. */
    static void pruneOldFiles(File dir, long maxAgeMs) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static File subDir(Context context, String name) {
        File dir = new File(context.getCacheDir(), name);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }
}
