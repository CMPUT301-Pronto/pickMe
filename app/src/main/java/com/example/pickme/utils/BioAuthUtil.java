package com.example.pickme.utils;

import android.os.Build;
import androidx.annotation.RequiresApi;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
public class BioAuthUtil {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "pickme_bio_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private BioAuthUtil() {}

    @RequiresApi(Build.VERSION_CODES.M)
    public static void ensureKeyExists() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // Require biometric each use:
                    .setUserAuthenticationRequired(true)
                    // Strong biometrics preferred; device credential fallback optional:
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .build();
            kg.init(spec);
            kg.generateKey();
        }
    }

    public static Cipher getEncryptCipher() throws Exception {
        SecretKey key = getSecretKey();
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        c.init(Cipher.ENCRYPT_MODE, key);
        return c; // call c.getIV() after init
    }

    public static Cipher getDecryptCipher(byte[] iv) throws Exception {
        SecretKey key = getSecretKey();
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        c.init(Cipher.DECRYPT_MODE, key, spec);
        return c;
    }

    private static SecretKey getSecretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        return ((SecretKey) ks.getKey(KEY_ALIAS, null));
    }
}
