package com.example.pickme.ui.shared_screens;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.os.Build;
import java.nio.charset.StandardCharsets;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pickme.MainActivity;
import com.example.pickme.R;
import com.example.pickme.services.FirebaseManager;
import com.example.pickme.utils.BioAuthUtil;
import com.example.pickme.utils.BioPrefsUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import com.example.pickme.utils.PasswordUtil;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
/**
 * JAVADOCS LLM GENERATED
 *
 * # LoginActivity
 *
 * Email+password login screen with an optional biometric “quick login” flow.
 * This screen verifies credentials against a stored PBKDF2 password hash in Firestore
 * (collection: <code>profiles</code>), then ensures an anonymous Firebase session
 * for app access. If biometric is enabled, users can unlock without re-typing credentials.
 *
 * ## Role in architecture
 * - UI/Presentation for authentication.
 * - Reads from Firestore for credential verification; does not write.
 * - Hands off session establishment to {@link FirebaseManager#signInAnonymously}.
 *
 * ## Key behaviors
 * - Validates email format and basic password rules.
 * - Verifies password via {@link PasswordUtil#verify(char[], String, String)}.
 * - Offers to encrypt and store a login token for biometric unlock (AES key in Android Keystore).
 * - Supports a separate biometric unlock path when previously enabled.
 *
 * ## Outstanding items / notes
 * - Consider throttling/lockout on repeated failures.
 * - Consider migrating credentials to a dedicated <code>users</code> collection if profiles diverge.
 * - Network errors currently surfaced via Toast; consider a persistent UI surface.
 *
 * @since 1.0
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private MaterialButton loginBtn, registerBtn;
    private ProgressBar progress;

    private final FirebaseFirestore db = FirebaseManager.getFirestore();

    /**
     * Android lifecycle entry point. Wires up UI controls, click handlers,
     * and configures the biometric login CTA visibility based on {@link BioPrefsUtil}.
     *
     * @param savedInstanceState saved state if the activity is re-created
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailLayout   = findViewById(R.id.emailLayout);
        passwordLayout= findViewById(R.id.passwordLayout);
        emailInput    = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn      = findViewById(R.id.loginBtn);
        registerBtn   = findViewById(R.id.registerBtn);
        progress      = findViewById(R.id.progress);

        loginBtn.setOnClickListener(v -> login());
        registerBtn.setOnClickListener(v ->
                startActivity(new Intent(this, com.example.pickme.ui.profile.CreateProfileActivity.class))
        );
        MaterialButton btnBiometricLogin = findViewById(R.id.btnBiometricLogin);
        btnBiometricLogin.setVisibility(BioPrefsUtil.isEnabled(this) ? View.VISIBLE : View.GONE);
        btnBiometricLogin.setOnClickListener(v -> biometricLogin());

    }
    /** Toggle progress UI and prevent double-submits while network work is in flight. */
    private void setLoading(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        loginBtn.setEnabled(!b);
        registerBtn.setEnabled(!b);
    }
    /**
     * After a successful password login, optionally offer to enable biometric quick login:
     * - Generates/ensures a Keystore key.
     * - Encrypts a short token (anonymous UID) with user presence.
     * - Stores IV + ciphertext via {@link BioPrefsUtil}.
     * Falls back to main navigation if biometric is unavailable or user cancels.
     */
    private void offerEnableBiometricsThenNavigate(String token) {
        BiometricManager bm = BiometricManager.from(this);
        int status = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (status != BiometricManager.BIOMETRIC_SUCCESS || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            navigateToMain();
            return;
        }
        try {
            BioAuthUtil.ensureKeyExists();
            final javax.crypto.Cipher enc = BioAuthUtil.getEncryptCipher();

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable biometric login")
                    .setSubtitle("Use your fingerprint/face next time")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setConfirmationRequired(false)
                    .build();

            BiometricPrompt prompt = new BiometricPrompt(
                    this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            try {
                                javax.crypto.Cipher c = result.getCryptoObject().getCipher();
                                byte[] blob = c.doFinal(token.getBytes(StandardCharsets.UTF_8));
                                byte[] iv = c.getIV();
                                BioPrefsUtil.save(getApplicationContext(), true, iv, blob);
                            } catch (Exception ignored) {
                                BioPrefsUtil.disable(getApplicationContext());
                            }
                            navigateToMain();
                        }
                        @Override public void onAuthenticationError(int code, CharSequence err) {
                            navigateToMain();
                        }
                    });

            prompt.authenticate(info, new BiometricPrompt.CryptoObject(enc));
        } catch (Exception e) {
            navigateToMain();
        }
    }

    /**
     * Main email+password login flow:
     * <ol>
     *   <li>Validate inputs (email format and basic password length).</li>
     *   <li>Fetch profile document by email (collection: <code>profiles</code>).</li>
     *   <li>Verify PBKDF2 hash with {@link PasswordUtil}.</li>
     *   <li>Establish anonymous Firebase session and optionally offer biometric setup.</li>
     * </ol>
     * Error cases are surfaced inline via TextInputLayout errors or Toasts.
     */
    private void login() {
        String email = text(emailInput);
        String pass  = text(passwordInput);

        emailLayout.setError(null);
        passwordLayout.setError(null);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Enter a valid email");
            return;
        }
        if (pass.length() < 6) {
            passwordLayout.setError("Password too short");
            return;
        }

        setLoading(true);

        // using profiles collection for credential lookip
        Query q = db.collection("profiles").whereEqualTo("email", email).limit(1);
        q.get().addOnCompleteListener(task -> {
            setLoading(false);
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                emailLayout.setError("No account found for that email");
                return;
            }

            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
            String salt = doc.getString("passwordSalt");
            String hash = doc.getString("passwordHash");

            if (salt == null || hash == null) {
                Toast.makeText(this, "Account missing password. Please create profile again.", Toast.LENGTH_LONG).show();
                return;
            }

            boolean ok = PasswordUtil.verify(pass.toCharArray(), salt, hash);
            if (!ok) {
                passwordLayout.setError("Incorrect password");
                return;
            }


            FirebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                @Override public void onSuccess(String uid) {
                    String token = uid != null ? uid : "OK";
                    // if biomertrics already enabled, just go
                    if (BioPrefsUtil.isEnabled(LoginActivity.this)) {
                        navigateToMain();
                        return;
                    }
                    offerEnableBiometricsThenNavigate(token);
                }
                @Override public void onFailure(Exception e) {
                    Toast.makeText(LoginActivity.this, "Auth error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** Helper to trim text from a TextInputEditText safely. */
    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
    /** Navigate to the main activity and finish this one. */
    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
    /**
     * Biometric quick-login path:
     * <ul>
     *   <li>Reads IV+ciphertext from {@link BioPrefsUtil}.</li>
     *   <li>Prompts for biometric auth and attempts AES decryption.</li>
     *   <li>Ensures an anonymous Firebase session exists, then navigates.</li>
     * </ul>
     * If anything is missing/invalid, biometric is disabled and the user is prompted to re-enable it later.
     */
    private void biometricLogin() {
        if (!BioPrefsUtil.isEnabled(this)) {
            Toast.makeText(this, "Biometric login not enabled on this device yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] iv = BioPrefsUtil.getIv(this);
        byte[] blob = BioPrefsUtil.getBlob(this);
        if (iv == null || blob == null) {
            BioPrefsUtil.disable(this);
            Toast.makeText(this, "Biometric data missing. Please re-enable in Profile.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            final javax.crypto.Cipher dec = BioAuthUtil.getDecryptCipher(iv);

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock with biometrics")
                    .setSubtitle("Confirm your identity")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setConfirmationRequired(false)
                    .build();

            BiometricPrompt prompt = new BiometricPrompt(
                    this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            try {
                                javax.crypto.Cipher c = result.getCryptoObject().getCipher();
                                byte[] plain = c.doFinal(blob);
                                String token = new String(plain); // optional: validate

                                // Ensure Firebase has a user (anonymous) then continue
                                if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                                    FirebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                                        @Override public void onSuccess(String uid) { navigateToMain(); }
                                        @Override public void onFailure(Exception e) {
                                            Toast.makeText(LoginActivity.this, "Auth error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                } else {
                                    navigateToMain();
                                }
                            } catch (Exception e) {
                                BioPrefsUtil.disable(getApplicationContext());
                                Toast.makeText(LoginActivity.this, "Biometric decode failed. Please re-enable in Profile.", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onAuthenticationError(int code, CharSequence err) {
                            // user cancelled or error → do nothing or show message
                        }
                        @Override public void onAuthenticationFailed() { /* no-op */ }
                    });

            prompt.authenticate(info, new BiometricPrompt.CryptoObject(dec));

        } catch (Exception e) {
            BioPrefsUtil.disable(this);
            Toast.makeText(this, "Biometric setup error. Please re-enable in Profile.", Toast.LENGTH_SHORT).show();
        }
    }
}