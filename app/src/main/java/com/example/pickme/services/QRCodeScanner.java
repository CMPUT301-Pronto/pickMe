package com.example.pickme.services;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * QRCodeScanner - Scans QR codes to join events using ZXing
 *
 * Integrates with ZXing Android Embedded library for camera scanning.
 * Parses QR codes in format: "eventlottery://event/{eventId}"
 * Validates event exists before returning
 *
 * Usage:
 * 1. Call startScanning() to launch camera
 * 2. Handle result in Activity.onActivityResult()
 * 3. Call parseScannedResult() to extract event
 *
 * Related User Stories: US 01.06.01, US 01.06.02
 */
public class QRCodeScanner {

    private static final String TAG = "QRCodeScanner";
    private static final String DEEP_LINK_PREFIX = "eventlottery://event/";

    private EventRepository eventRepository;

    public QRCodeScanner() {
        this.eventRepository = new EventRepository();
    }

    /**
     * Start QR code scanning using device camera
     *
     * Launches ZXing IntentIntegrator camera activity
     * Result will be returned to Activity.onActivityResult()
     *
     * @param activity Activity to receive scan result
     */
    public void startScanning(@NonNull Activity activity) {
        Log.d(TAG, "Starting QR code scanner");

        IntentIntegrator integrator = new IntentIntegrator(activity);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan Event QR Code");
        integrator.setCameraId(0); // Use back camera
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    /**
     * Parse scanned QR code result
     *
     * Process:
     * 1. Check if scan was successful
     * 2. Extract data from IntentResult
     * 3. Parse event ID from deep link format
     * 4. Validate event exists in database
     * 5. Return Event object if valid
     *
     * @param result IntentResult from ZXing scanner
     * @param listener Callback with Event or error
     */
    public void parseScannedResult(@NonNull IntentResult result,
                                   @NonNull OnQRScannedListener listener) {
        if (result.getContents() == null) {
            Log.w(TAG, "Scan cancelled or failed");
            listener.onError(new Exception("Scan cancelled"));
            return;
        }

        String scannedData = result.getContents();
        Log.d(TAG, "Scanned data: " + scannedData);

        // Validate format
        if (!scannedData.startsWith(DEEP_LINK_PREFIX)) {
            Log.w(TAG, "Invalid QR code format: " + scannedData);
            listener.onError(new Exception("Invalid QR code format"));
            return;
        }

        // Extract event ID
        String eventId = scannedData.substring(DEEP_LINK_PREFIX.length());

        if (eventId.isEmpty()) {
            Log.w(TAG, "Empty event ID in QR code");
            listener.onError(new Exception("Invalid event ID"));
            return;
        }

        Log.d(TAG, "Extracted event ID: " + eventId);

        // Validate event exists
        validateAndReturnEvent(eventId, listener);
    }

    /**
     * Validate event exists and return Event object
     *
     * @param eventId Event ID to validate
     * @param listener Callback with Event
     */
    private void validateAndReturnEvent(@NonNull String eventId,
                                       @NonNull OnQRScannedListener listener) {
        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                if (event == null) {
                    Log.w(TAG, "Event not found: " + eventId);
                    listener.onError(new Exception("Event not found"));
                    return;
                }

                Log.d(TAG, "Valid event scanned: " + event.getName());
                listener.onQRScanned(event);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to validate event", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Extract event ID from scanned data without validation
     * Useful for quick parsing
     *
     * @param scannedData Raw scanned data
     * @return Event ID or null if invalid
     */
    public String extractEventId(String scannedData) {
        if (scannedData == null || !scannedData.startsWith(DEEP_LINK_PREFIX)) {
            return null;
        }

        String eventId = scannedData.substring(DEEP_LINK_PREFIX.length());
        return eventId.isEmpty() ? null : eventId;
    }

    /**
     * Check if scanned data is valid event QR code format
     *
     * @param scannedData Raw scanned data
     * @return true if valid format
     */
    public boolean isValidEventQRCode(String scannedData) {
        return scannedData != null
                && scannedData.startsWith(DEEP_LINK_PREFIX)
                && scannedData.length() > DEEP_LINK_PREFIX.length();
    }

    /**
     * Callback for QR scan result
     */
    public interface OnQRScannedListener {
        void onQRScanned(Event event);
        void onError(Exception e);
    }
}

