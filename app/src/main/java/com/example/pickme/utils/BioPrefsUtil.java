package com.example.pickme.utils;
import android.content.Context;
import android.util.Base64;
public class BioPrefsUtil {
    private BioPrefsUtil() {}

    private static final String PREFS = "PickMePrefs";
    private static final String KEY_ENABLED = "bio_enabled";
    private static final String KEY_IV = "bio_iv";
    private static final String KEY_BLOB = "bio_blob";

    public static void save(Context ctx, boolean enabled, byte[] iv, byte[] blob) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_IV, iv != null ? Base64.encodeToString(iv, Base64.NO_WRAP) : null)
                .putString(KEY_BLOB, blob != null ? Base64.encodeToString(blob, Base64.NO_WRAP) : null)
                .apply();
    }

    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static byte[] getIv(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_IV, null);
        return s != null ? Base64.decode(s, Base64.NO_WRAP) : null;
    }

    public static byte[] getBlob(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BLOB, null);
        return s != null ? Base64.decode(s, Base64.NO_WRAP) : null;
    }

    public static void disable(Context ctx) {
        save(ctx, false, null, null);
    }
}
