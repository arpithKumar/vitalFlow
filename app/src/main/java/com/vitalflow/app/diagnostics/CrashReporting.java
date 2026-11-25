package com.vitalflow.app.diagnostics;

/**
 * Owns the user's opt-in choice for crash reporting.
 *
 * <p>Collection is <b>off</b> until the user switches it on in Settings. Switching it off again
 * also discards reports that have not been sent yet. The class has no Android dependencies, so
 * the rules can be unit-tested; {@link SharedPrefsConsentStore} and {@link FirebaseCrashBackend}
 * connect it to the real storage and the real service.
 */
public final class CrashReporting {

    /** Persists the user's choice. */
    public interface ConsentStore {
        boolean isGranted();

        void setGranted(boolean granted);
    }

    /** The crash reporting service that is switched on or off. */
    public interface Backend {
        void setCollectionEnabled(boolean enabled);

        void deleteUnsentReports();
    }

    private final ConsentStore consentStore;
    private final Backend backend;

    public CrashReporting(ConsentStore consentStore, Backend backend) {
        this.consentStore = consentStore;
        this.backend = backend;
    }

    /** Whether the user has opted in. */
    public boolean isEnabled() {
        return consentStore.isGranted();
    }

    /** Call once at startup so the service matches the saved choice. */
    public void applySavedChoice() {
        backend.setCollectionEnabled(consentStore.isGranted());
    }

    /** Called when the user flips the switch in Settings. */
    public void setEnabled(boolean enabled) {
        consentStore.setGranted(enabled);
        backend.setCollectionEnabled(enabled);
        if (!enabled) {
            backend.deleteUnsentReports();
        }
    }
}
