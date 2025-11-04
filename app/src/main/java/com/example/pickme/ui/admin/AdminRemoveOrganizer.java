package com.example.pickme.ui.admin;


import android.content.Intent;
import android.content.pm.PackageItemInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pickme.R;

public class AdminRemoveOrganizer extends AppCompatActivity {
    private Button btnRemoveOrganizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        btnRemoveOrganizer = findViewById(R.id.btnRemoveOrganizer);

        btnRemoveOrganizer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open the fragment to remove organizers
                btnRemoveOrganizer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Open the new activity for removing organizers
                        Intent intent = new Intent(AdminRemoveOrganizer.this, RemoveOrganizerActivity.class);
                        startActivity(intent);
                    }
                });

            }
        });
    }
}
