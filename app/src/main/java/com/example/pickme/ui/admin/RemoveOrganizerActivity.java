package com.example.pickme.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import com.example.pickme.R;

public class RemoveOrganizerActivity extends AppCompatActivity {
    private ListView listViewOrganizers;
    private ArrayAdapter<String> adapter;
    private List<String> organizerNames = new ArrayList<>();
    private List<String> organizerIds = new ArrayList<>();
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_remove_organizer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Remove Organizer");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        listViewOrganizers = findViewById(R.id.listViewOrganizers);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, organizerNames);
        listViewOrganizers.setAdapter(adapter);

        usersRef = FirebaseDatabase.getInstance().getReference("profiles");
        loadOrganizers();

        listViewOrganizers.setOnItemClickListener((parent, view, position, id) -> {
            String organizerId = organizerIds.get(position);
            showDeleteConfirmationDialog(organizerId, organizerNames.get(position));

        });


    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void loadOrganizers() {
        usersRef.orderByChild("role").equalTo("organizer").get()
                .addOnSuccessListener(snapshot -> {
                    organizerNames.clear();
                    organizerIds.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        organizerNames.add(child.child("name").getValue(String.class));
                        organizerIds.add(child.getKey());
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showDeleteConfirmationDialog(String id, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Organizer")
                .setMessage("Are you sure you want to delete " + name + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteOrganizer(id))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOrganizer(String id) {
        usersRef.child(id).removeValue()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Organizer deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());

    }

}

