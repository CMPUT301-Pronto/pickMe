package com.example.pickme.ui.shared_screens;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pickme.MainActivity;
import com.example.pickme.R;
import com.example.pickme.services.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import com.example.pickme.utils.PasswordUtil;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private MaterialButton loginBtn, registerBtn;
    private ProgressBar progress;

    private final FirebaseFirestore db = FirebaseManager.getFirestore();

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
    }

    private void setLoading(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        loginBtn.setEnabled(!b);
        registerBtn.setEnabled(!b);
    }

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

        // Change "profiles" to "users" if that's your collection
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

            // Optional: sign in anonymously so Firestore rules have request.auth.uid
            FirebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                @Override public void onSuccess(String uid) { navigateToMain(); }
                @Override public void onFailure(Exception e) {
                    Toast.makeText(LoginActivity.this, "Auth error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}