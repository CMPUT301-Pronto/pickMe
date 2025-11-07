package com.example.pickme.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pickme.R;

/**
 * AdminImagesFragment - Browse and manage event images
 * Related User Stories: US 03.06.01, US 03.03.01
 */
public class AdminImagesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Show placeholder
        view.findViewById(R.id.emptyState).setVisibility(View.VISIBLE);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyMessage);
        tvEmpty.setText("Image management - Coming soon");
    }
}

