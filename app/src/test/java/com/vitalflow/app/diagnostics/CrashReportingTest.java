package com.vitalflow.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link CrashReporting}: crash reporting is opt-in, the choice is persisted, and
 * turning it off discards reports that were not sent yet.
 */
public class CrashReportingTest {

    private FakeConsentStore store;
    private RecordingBackend backend;
    private CrashReporting crashReporting;

    @Before
    public void setUp() {
        store = new FakeConsentStore();
        backend = new RecordingBackend();
        crashReporting = new CrashReporting(store, backend);
    }

    @Test
    public void freshInstall_isDisabledByDefault() {
        assertFalse(crashReporting.isEnabled());
    }

    @Test
    public void applySavedChoice_whenNeverAsked_keepsCollectionOff() {
        crashReporting.applySavedChoice();

        assertEquals(Boolean.FALSE, backend.lastCollectionEnabled);
    }

    @Test
    public void applySavedChoice_whenUserOptedIn_turnsCollectionOn() {
        store.granted = true;

        crashReporting.applySavedChoice();

        assertEquals(Boolean.TRUE, backend.lastCollectionEnabled);
    }

    @Test
    public void applySavedChoice_neverDeletesReports() {
        store.granted = false;

        crashReporting.applySavedChoice();

        assertEquals(0, backend.deleteUnsentReportsCalls);
    }

    @Test
    public void enabling_persistsTheChoiceAndTurnsCollectionOn() {
        crashReporting.setEnabled(true);

        assertTrue(store.granted);
        assertTrue(crashReporting.isEnabled());
        assertEquals(Boolean.TRUE, backend.lastCollectionEnabled);
        assertEquals("opting in must not discard anything", 0, backend.deleteUnsentReportsCalls);
    }

    @Test
    public void disabling_persistsTheChoice_turnsCollectionOff_andDeletesUnsentReports() {
        crashReporting.setEnabled(true);

        crashReporting.setEnabled(false);

        assertFalse(store.granted);
        assertFalse(crashReporting.isEnabled());
        assertEquals(Boolean.FALSE, backend.lastCollectionEnabled);
        assertEquals(1, backend.deleteUnsentReportsCalls);
    }

    // ---------------------------------------------------------------------------------------
    // test doubles
    // ---------------------------------------------------------------------------------------

    private static final class FakeConsentStore implements CrashReporting.ConsentStore {
        boolean granted = false; // mirrors the real default

        @Override
        public boolean isGranted() {
            return granted;
        }

        @Override
        public void setGranted(boolean granted) {
            this.granted = granted;
        }
    }

    private static final class RecordingBackend implements CrashReporting.Backend {
        Boolean lastCollectionEnabled; // null until the backend is touched
        int deleteUnsentReportsCalls = 0;

        @Override
        public void setCollectionEnabled(boolean enabled) {
            lastCollectionEnabled = enabled;
        }

        @Override
        public void deleteUnsentReports() {
            deleteUnsentReportsCalls++;
        }
    }
}
