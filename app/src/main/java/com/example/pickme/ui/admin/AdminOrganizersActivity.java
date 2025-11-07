package com.example.pickme.ui.admin;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pickme.R;

/**
 * AdminOrganizersActivity - Wrapper activity for AdminOrganizersFragment
 * Allows admins to browse and remove organizer profiles
 * Related User Stories: US 03.02.01
 */
public class AdminOrganizersActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_single_fragment);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Remove Organizers");
        }

        // Load fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new AdminOrganizersFragment())
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

