package com.example.pickme.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pickme.R;
import com.example.pickme.ui.notifications.NotificationsViewModel;
// -------------- LLM GENERATED -------------- //
public class NotificationsFragment extends Fragment {

    private TextView textNotifications;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_notifications, container, false);
        textNotifications = root.findViewById(R.id.text_notifications);

        NotificationsViewModel vm = new ViewModelProvider(this).get(NotificationsViewModel.class);
        vm.getText().observe(getViewLifecycleOwner(), textNotifications::setText);
        return root;
    }
}
// -------------- LLM GENERATED -------------- //