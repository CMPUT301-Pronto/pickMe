package com.example.pickme.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.QRCode;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * QRCodeGenerator - Generates QR codes for events using ZXing
 *
 * QR codes encode deep links in format: "eventlottery://event/{eventId}"
 * Entrants scan these codes to join event waiting lists
 *
 * Process:
 * 1. Generate QR code bitmap with encoded event URL
 * 2. Save bitmap to device storage
 * 3. Save QRCode record to Firestore for tracking
 * 4. Return bitmap and file path
 *
 * Related User Stories: US 02.01.01, US 01.06.01
 */
public class QRCodeGenerator {

    private static final String TAG = "QRCodeGenerator";
    private static final String DEEP_LINK_SCHEME = "eventlottery://event/";
    private static final String COLLECTION_QR_CODES = "qr_codes";
    private static final int QR_CODE_SIZE = 512; // Size in pixels

    private FirebaseFirestore db;

    public QRCodeGenerator() {
        this.db = FirebaseManager.getFirestore();
    }

    /**
     * Generate QR code for an event
     *
     * @param eventId Event ID to encode
     * @param context Android context for file storage
     * @param listener Callback with bitmap and file path
     */
    public void generateQRCode(@NonNull String eventId,
                              @NonNull Context context,
                              @NonNull OnQRGeneratedListener listener) {
        Log.d(TAG, "Generating QR code for event: " + eventId);

        try {
            // Create encoded data
            String encodedData = DEEP_LINK_SCHEME + eventId;

            // Generate QR code bitmap
            Bitmap qrBitmap = generateQRCodeBitmap(encodedData, QR_CODE_SIZE, QR_CODE_SIZE);

            // Save to device storage
            String filePath = saveQRCodeToFile(context, qrBitmap, eventId);

            // Create QRCode record in Firestore
            QRCode qrCode = new QRCode(
                    db.collection(COLLECTION_QR_CODES).document().getId(),
                    eventId,
                    encodedData
            );

            db.collection(COLLECTION_QR_CODES)
                    .document(qrCode.getQrCodeId())
                    .set(qrCode.toMap())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "QR code record saved: " + qrCode.getQrCodeId());
                        listener.onQRGenerated(qrBitmap, filePath, qrCode.getQrCodeId());
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to save QR code record, but bitmap generated", e);
                        listener.onQRGenerated(qrBitmap, filePath, null);
                    });

        } catch (WriterException | IOException e) {
            Log.e(TAG, "Failed to generate QR code", e);
            listener.onError(e);
        }
    }

    /**
     * Regenerate QR code (delete old record, create new one)
     *
     * @param eventId Event ID
     * @param context Android context
     * @param listener Callback
     */
    public void regenerateQRCode(@NonNull String eventId,
                                 @NonNull Context context,
                                 @NonNull OnQRGeneratedListener listener) {
        Log.d(TAG, "Regenerating QR code for event: " + eventId);

        // Query and delete old QR code records for this event
        db.collection(COLLECTION_QR_CODES)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Delete old records
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete();
                    }

                    // Generate new QR code
                    generateQRCode(eventId, context, listener);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to delete old QR codes, generating new one anyway", e);
                    generateQRCode(eventId, context, listener);
                });
    }

    /**
     * Load existing QR code for an event
     * If QR code file exists, load it; otherwise generate a new one
     *
     * @param eventId Event ID
     * @param context Android context
     * @param listener Callback with bitmap and file path
     */
    public void loadOrGenerateQRCode(@NonNull String eventId,
                                     @NonNull Context context,
                                     @NonNull OnQRGeneratedListener listener) {
        Log.d(TAG, "Loading QR code for event: " + eventId);

        // Check if QR code file exists
        File qrCodesDir = new File(context.getFilesDir(), "qr_codes");
        String filename = "qr_event_" + eventId + ".png";
        File qrFile = new File(qrCodesDir, filename);

        if (qrFile.exists()) {
            Log.d(TAG, "QR code file exists, loading from: " + qrFile.getAbsolutePath());
            try {
                // Load bitmap from file
                Bitmap qrBitmap = android.graphics.BitmapFactory.decodeFile(qrFile.getAbsolutePath());

                if (qrBitmap != null) {
                    Log.d(TAG, "QR code bitmap loaded successfully");

                    // Get QR code ID from Firestore
                    db.collection(COLLECTION_QR_CODES)
                            .whereEqualTo("eventId", eventId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                String qrCodeId = null;
                                if (!querySnapshot.isEmpty()) {
                                    qrCodeId = querySnapshot.getDocuments().get(0).getId();
                                }
                                listener.onQRGenerated(qrBitmap, qrFile.getAbsolutePath(), qrCodeId);
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Failed to get QR code ID from Firestore", e);
                                listener.onQRGenerated(qrBitmap, qrFile.getAbsolutePath(), null);
                            });
                } else {
                    Log.w(TAG, "Failed to decode QR code bitmap, regenerating");
                    generateQRCode(eventId, context, listener);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading QR code bitmap", e);
                generateQRCode(eventId, context, listener);
            }
        } else {
            Log.d(TAG, "QR code file doesn't exist, generating new one");
            generateQRCode(eventId, context, listener);
        }
    }

    /**
     * Generate QR code bitmap using ZXing
     *
     * @param content Content to encode
     * @param width Width in pixels
     * @param height Height in pixels
     * @return Bitmap of QR code
     * @throws WriterException if encoding fails
     */
    private Bitmap generateQRCodeBitmap(String content, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }

        Log.d(TAG, "QR code bitmap generated: " + width + "x" + height);
        return bitmap;
    }

    /**
     * Save QR code bitmap to device storage
     *
     * @param context Android context
     * @param bitmap Bitmap to save
     * @param eventId Event ID for filename
     * @return File path
     * @throws IOException if save fails
     */
    private String saveQRCodeToFile(Context context, Bitmap bitmap, String eventId) throws IOException {
        File qrCodesDir = new File(context.getFilesDir(), "qr_codes");
        if (!qrCodesDir.exists()) {
            qrCodesDir.mkdirs();
        }

        String filename = "qr_event_" + eventId + ".png";
        File qrFile = new File(qrCodesDir, filename);

        try (FileOutputStream fos = new FileOutputStream(qrFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        }

        Log.d(TAG, "QR code saved to: " + qrFile.getAbsolutePath());
        return qrFile.getAbsolutePath();
    }

    /**
     * Callback for QR code generation
     */
    public interface OnQRGeneratedListener {
        void onQRGenerated(Bitmap qrBitmap, String filePath, String qrCodeId);
        void onError(Exception e);
    }
}

