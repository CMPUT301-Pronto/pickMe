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
 * AdminNotificationLogsFragment - View notification logs
 * Related User Stories: US 03.08.01
 */
public class AdminNotificationLogsFragment extends Fragment {

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
        tvEmpty.setText("Notification logs - Coming soon");
    }
}

