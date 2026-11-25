package com.vitalflow.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Environment;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the real FileProvider declared in AndroidManifest.xml with the folders from
 * res/xml/provider_paths.xml. A JVM test cannot do this because the provider needs an Android runtime.
 *
 * <p>Run it on an emulator or device with {@code ./gradlew connectedDebugAndroidTest}. Gradle removes the
 * app after that run, so to keep the app installed use {@code assembleDebugAndroidTest}, install the test
 * APK and call {@code adb shell am instrument -w com.vitalflow.app.test/androidx.test.runner.AndroidJUnitRunner}.
 */
@RunWith(AndroidJUnit4.class)
public class FileShareUtilsInstrumentedTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final List<File> createdFiles = new ArrayList<>();

    @After
    public void deleteTestFiles() {
        for (File file : createdFiles) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void authority_isTheProviderDeclaredInTheManifest() {
        ProviderInfo provider = context.getPackageManager()
                .resolveContentProvider(FileShareUtils.authority(context), 0);

        assertNotNull("the manifest declares the provider", provider);
        assertFalse("other apps cannot query the provider directly", provider.exported);
        assertTrue("access is granted per URI", provider.grantUriPermissions);
    }

    @Test
    public void oldWrongAuthority_isNotDeclared() {
        // DataExportHelper used to ask for "<package>.fileprovider", which never existed, so the
        // Excel export could not be shared.
        assertNull(context.getPackageManager()
                .resolveContentProvider(context.getPackageName() + ".fileprovider", 0));
    }

    @Test
    public void scanFile_isSharedThroughTheScansFolderAndCanBeRead() throws IOException {
        File file = track(FileShareUtils.newScanFile(context, "test_scan_"));
        write(file, "photo");

        Uri uri = FileShareUtils.uriFor(context, file);

        assertTrue(uri.getPath(), uri.getPath().startsWith("/scans/"));
        assertEquals("photo", read(uri));
    }

    @Test
    public void exportFile_isSharedThroughTheExportsFolderAndCanBeRead() throws IOException {
        File file = track(FileShareUtils.exportFile(context, "test_export.xlsx"));
        write(file, "sheet");

        Uri uri = FileShareUtils.uriFor(context, file);

        assertTrue(uri.getPath(), uri.getPath().startsWith("/exports/"));
        assertEquals("sheet", read(uri));
    }

    @Test
    public void fileInTheCacheRoot_isRefused() throws IOException {
        File file = track(new File(context.getCacheDir(), "test_secret.txt"));
        write(file, "secret");

        assertThrows(IllegalArgumentException.class, () -> FileShareUtils.uriFor(context, file));
    }

    @Test
    public void filesInThePrivateFilesFolder_areRefused() throws IOException {
        File file = track(new File(context.getFilesDir(), "test_private.txt"));
        write(file, "secret");

        assertThrows(IllegalArgumentException.class, () -> FileShareUtils.uriFor(context, file));
    }

    @Test
    @SuppressWarnings("deprecation") // The shared-storage root is only used to prove it is NOT exposed.
    public void sharedStorageOutsideDownload_isRefused() {
        File picture = new File(Environment.getExternalStorageDirectory(), "Pictures/test.jpg");

        assertThrows(IllegalArgumentException.class, () -> FileShareUtils.uriFor(context, picture));
    }

    @Test
    @SuppressWarnings("deprecation") // Same path as PdfReportManager uses on Android 9 and older.
    public void legacyDownloadFolder_isStillShareable() {
        File report = new File(Environment.getExternalStorageDirectory(), "Download/test.pdf");

        Uri uri = FileShareUtils.uriFor(context, report);

        assertTrue(uri.getPath(), uri.getPath().startsWith("/legacy_downloads/"));
    }

    private File track(File file) {
        createdFiles.add(file);
        return file;
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String read(Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            assertNotNull(in);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            int count;
            while ((count = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
