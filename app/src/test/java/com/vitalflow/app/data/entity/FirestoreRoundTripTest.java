package com.vitalflow.app.data.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.util.CustomClassMapper;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * SyncRepository writes entities with {@code WriteBatch.set}, which uses Firestore's CustomClassMapper,
 * and restores them with {@code DocumentSnapshot.toObject}, which uses the same mapper. These tests run
 * that exact round trip for every synced entity.
 *
 * <p>The mapper needs a public no-argument constructor, and a setter or public field for every stored
 * property. It does not fail for a property without a setter: it silently leaves the field empty, so a
 * restored record would look fine and have lost data. Every test therefore fills every property with a
 * non-default value and requires the whole record to come back unchanged.
 *
 * <p>When you add a field to one of these entities, add it to the matching test as well.
 */
public class FirestoreRoundTripTest {

    @Test
    public void dailyHealth_survivesAFirestoreRoundTrip() {
        DailyHealth original = new DailyHealth("2026-11-25", 1250, 2500, 8400, "7h 30m", 4);

        assertSurvivesRoundTrip(original, DailyHealth.class, 6);
    }

    @Test
    public void vital_survivesAFirestoreRoundTrip() {
        Vital original = new Vital("Blood Pressure", "120", "80", "mmHg", "2026-11-25 10:30:00",
                1_764_066_600_000L, "Stressed, Missed Meds", "Dizziness");

        assertSurvivesRoundTrip(original, Vital.class, 9);
    }

    @Test
    public void periodLog_survivesAFirestoreRoundTrip() {
        PeriodLog original = new PeriodLog("2026-11-25", true, 2, "Cramps,Headache");

        assertSurvivesRoundTrip(original, PeriodLog.class, 4);
    }

    @Test
    public void foodLog_survivesAFirestoreRoundTrip() {
        FoodLog original = new FoodLog("2026-11-25", "13:15", "Dal rice", 420, "P: 14g, C: 70g, F: 8g");

        assertSurvivesRoundTrip(original, FoodLog.class, 6);
    }

    @Test
    public void medication_survivesAFirestoreRoundTrip() {
        // The status getter resets the daily counters when lastActionDate is not today, so use today.
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Medication original = new Medication("med-1", "Iron", "65 mg", "Tablet", "08:00 AM",
                "Dr. Rao", "City Hospital", "2026-11-01", "2026-12-01",
                24, "SKIPPED", today, 2, 1, "Mon,Wed,Fri", "file:///rx_1.jpg");

        assertSurvivesRoundTrip(original, Medication.class, 16);
    }

    /** Writes {@code original} like set() does, restores it like toObject() does, and compares. */
    private static <T> void assertSurvivesRoundTrip(T original, Class<T> type, int minProperties) {
        Map<String, Object> written = toStoredProperties(original);
        assertTrue("expected at least " + minProperties + " stored properties but got " + written,
                written.size() >= minProperties);

        T restored = CustomClassMapper.convertToCustomClass(written, type, null);

        assertEquals("every stored property of " + type.getSimpleName() + " is restored",
                written, toStoredProperties(restored));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStoredProperties(Object entity) {
        return (Map<String, Object>) CustomClassMapper.convertToPlainJavaTypes(entity);
    }
}
