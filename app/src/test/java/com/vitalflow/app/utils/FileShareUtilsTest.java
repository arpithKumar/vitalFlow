package com.vitalflow.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/** Unit tests for the clean-up of old scan photos in {@link FileShareUtils}. */
public class FileShareUtilsTest {

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void pruneOldFiles_removesOnlyFilesOlderThanTheLimit() throws IOException {
        File old = folder.newFile("old.jpg");
        File fresh = folder.newFile("fresh.jpg");
        assertTrue(old.setLastModified(System.currentTimeMillis() - 2 * ONE_DAY_MS));

        FileShareUtils.pruneOldFiles(folder.getRoot(), ONE_DAY_MS);

        assertFalse("a two day old photo is removed", old.exists());
        assertTrue("a new photo is kept", fresh.exists());
    }

    @Test
    public void pruneOldFiles_leavesSubfoldersAlone() throws IOException {
        File subfolder = folder.newFolder("nested");
        assertTrue(subfolder.setLastModified(System.currentTimeMillis() - 5 * ONE_DAY_MS));

        FileShareUtils.pruneOldFiles(folder.getRoot(), ONE_DAY_MS);

        assertTrue(subfolder.exists());
    }

    @Test
    public void pruneOldFiles_missingFolder_doesNothing() {
        FileShareUtils.pruneOldFiles(new File(folder.getRoot(), "does-not-exist"), ONE_DAY_MS);
    }
}
