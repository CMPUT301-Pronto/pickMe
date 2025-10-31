package com.example.pickme.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.example.pickme.models.Event;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CsvExporter - Utility for exporting entrant lists to CSV format
 *
 * Generates CSV files with entrant information and provides share functionality.
 * Supports exporting:
 * - Waiting list entrants
 * - Selected entrants (response pending)
 * - Confirmed entrants (in-event list)
 *
 * CSV Format:
 * Name,Email,Phone,Join Date,Status
 *
 * Related User Stories: US 02.06.05
 */
public class CsvExporter {

    private static final String TAG = "CsvExporter";
    private static final String CSV_HEADER = "Name,Email,Phone,Join Date,Status\n";

    private Context context;
    private ProfileRepository profileRepository;
    private SimpleDateFormat dateFormat;

    public CsvExporter(@NonNull Context context) {
        this.context = context;
        this.profileRepository = new ProfileRepository();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    /**
     * Export list of entrants to CSV file
     *
     * @param event Event object
     * @param entrantIds List of user IDs to export
     * @param listType Type of list (e.g., "waiting", "selected", "confirmed")
     * @param listener Callback with file URI or error
     */
    public void exportEntrantList(@NonNull Event event,
                                  @NonNull List<String> entrantIds,
                                  @NonNull String listType,
                                  @NonNull OnExportCompleteListener listener) {
        if (entrantIds.isEmpty()) {
            listener.onError(new Exception("No entrants to export"));
            return;
        }

        Log.d(TAG, "Exporting " + entrantIds.size() + " entrants for event: " + event.getName());

        // Fetch profiles for all entrants
        fetchProfiles(entrantIds, new OnProfilesFetchedListener() {
            @Override
            public void onProfilesFetched(List<Profile> profiles) {
                try {
                    // Generate CSV file
                    File csvFile = generateCsvFile(event, profiles, listType);

                    // Get shareable URI
                    Uri fileUri = FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".fileprovider",
                            csvFile
                    );

                    Log.d(TAG, "CSV file generated: " + csvFile.getAbsolutePath());
                    listener.onExportComplete(fileUri, csvFile.getAbsolutePath());

                } catch (IOException e) {
                    Log.e(TAG, "Failed to generate CSV file", e);
                    listener.onError(e);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to fetch profiles", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Fetch profiles for list of user IDs
     */
    private void fetchProfiles(@NonNull List<String> userIds,
                              @NonNull OnProfilesFetchedListener listener) {
        List<Profile> profiles = new ArrayList<>();
        int[] fetchedCount = {0};

        for (String userId : userIds) {
            profileRepository.getProfile(userId, new ProfileRepository.OnProfileLoadedListener() {
                @Override
                public void onProfileLoaded(Profile profile) {
                    synchronized (profiles) {
                        profiles.add(profile);
                        fetchedCount[0]++;

                        if (fetchedCount[0] == userIds.size()) {
                            listener.onProfilesFetched(profiles);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    synchronized (profiles) {
                        Log.w(TAG, "Failed to fetch profile: " + userId, e);
                        fetchedCount[0]++;

                        if (fetchedCount[0] == userIds.size()) {
                            listener.onProfilesFetched(profiles);
                        }
                    }
                }
            });
        }
    }

    /**
     * Generate CSV file from profiles
     */
    private File generateCsvFile(@NonNull Event event,
                                 @NonNull List<Profile> profiles,
                                 @NonNull String listType) throws IOException {
        // Create exports directory
        File exportsDir = new File(context.getFilesDir(), "exports");
        if (!exportsDir.exists()) {
            exportsDir.mkdirs();
        }

        // Generate filename
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String filename = sanitizeFilename(event.getName()) + "_" + listType + "_" + timestamp + ".csv";
        File csvFile = new File(exportsDir, filename);

        // Write CSV content
        try (FileWriter writer = new FileWriter(csvFile)) {
            // Write header
            writer.append(CSV_HEADER);

            // Write data rows
            for (Profile profile : profiles) {
                writer.append(escapeCsvValue(profile.getName() != null ? profile.getName() : "N/A"))
                      .append(",")
                      .append(escapeCsvValue(profile.getEmail() != null ? profile.getEmail() : "N/A"))
                      .append(",")
                      .append(escapeCsvValue(profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "N/A"))
                      .append(",")
                      .append(dateFormat.format(new Date()))
                      .append(",")
                      .append(listType)
                      .append("\n");
            }

            writer.flush();
        }

        return csvFile;
    }

    /**
     * Escape CSV value (handle commas, quotes, newlines)
     */
    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }

        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }

    /**
     * Sanitize filename (remove invalid characters)
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    /**
     * Create share intent for CSV file
     *
     * @param fileUri URI of CSV file
     * @param context Context to start intent
     */
    public static void shareCSV(@NonNull Uri fileUri, @NonNull Context context) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, "Share Entrant List");
        context.startActivity(chooser);
    }

    /**
     * Callback for export completion
     */
    public interface OnExportCompleteListener {
        void onExportComplete(Uri fileUri, String filePath);
        void onError(Exception e);
    }

    /**
     * Internal callback for profile fetching
     */
    private interface OnProfilesFetchedListener {
        void onProfilesFetched(List<Profile> profiles);
        void onError(Exception e);
    }
}

