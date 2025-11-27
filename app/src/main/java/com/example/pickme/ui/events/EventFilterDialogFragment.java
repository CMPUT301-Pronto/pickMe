package com.example.pickme.ui.events;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.pickme.R;
import com.example.pickme.utils.Constants;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * EventFilterDialogFragment - Bottom sheet dialog for filtering events
 *
 * Allows entrants to filter events by:
 * - Date range (start and end dates)
 * - Location (from available event locations)
 * - Event type/category
 *
 * Related User Stories: US 01.01.03
 */
public class EventFilterDialogFragment extends BottomSheetDialogFragment {

    public static final String TAG = "EventFilterDialog";

    // Use constants from Constants class
    public static final String TYPE_ALL = Constants.EVENT_TYPE_ALL;

    // UI Components
    private ImageButton btnClose;
    private TextView tvStartDate, tvEndDate;
    private ImageButton btnClearStartDate, btnClearEndDate;
    private AutoCompleteTextView dropdownLocation;
    private ChipGroup chipGroupEventType;
    private Button btnClearFilters, btnApplyFilters;

    // Filter state
    private Long startDateMillis = null;
    private Long endDateMillis = null;
    private String selectedLocation = null;
    private String selectedEventType = TYPE_ALL;

    // Data
    private List<String> availableLocations = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    // Listener
    private OnFilterAppliedListener listener;

    /**
     * Create new instance with available locations
     */
    public static EventFilterDialogFragment newInstance(List<String> locations) {
        EventFilterDialogFragment fragment = new EventFilterDialogFragment();
        fragment.availableLocations = locations != null ? locations : new ArrayList<>();
        return fragment;
    }

    /**
     * Set the current filter state (to restore previous selections)
     */
    public void setCurrentFilters(Long startDate, Long endDate, String location, String eventType) {
        this.startDateMillis = startDate;
        this.endDateMillis = endDate;
        this.selectedLocation = location;
        this.selectedEventType = eventType != null ? eventType : TYPE_ALL;
    }

    public void setOnFilterAppliedListener(OnFilterAppliedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_event_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupDatePickers();
        setupLocationDropdown();
        setupEventTypeChips();
        setupButtons();
        restoreFilterState();
    }

    private void initializeViews(View view) {
        btnClose = view.findViewById(R.id.btnClose);
        tvStartDate = view.findViewById(R.id.tvStartDate);
        tvEndDate = view.findViewById(R.id.tvEndDate);
        btnClearStartDate = view.findViewById(R.id.btnClearStartDate);
        btnClearEndDate = view.findViewById(R.id.btnClearEndDate);
        dropdownLocation = view.findViewById(R.id.dropdownLocation);
        chipGroupEventType = view.findViewById(R.id.chipGroupEventType);
        btnClearFilters = view.findViewById(R.id.btnClearFilters);
        btnApplyFilters = view.findViewById(R.id.btnApplyFilters);
    }

    private void setupDatePickers() {
        // Start date picker
        tvStartDate.setOnClickListener(v -> showDatePicker(true));
        btnClearStartDate.setOnClickListener(v -> {
            startDateMillis = null;
            tvStartDate.setText("Select start date");
            btnClearStartDate.setVisibility(View.GONE);
        });

        // End date picker
        tvEndDate.setOnClickListener(v -> showDatePicker(false));
        btnClearEndDate.setOnClickListener(v -> {
            endDateMillis = null;
            tvEndDate.setText("Select end date");
            btnClearEndDate.setVisibility(View.GONE);
        });
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();

        // If editing existing date, start from that date
        if (isStartDate && startDateMillis != null) {
            calendar.setTimeInMillis(startDateMillis);
        } else if (!isStartDate && endDateMillis != null) {
            calendar.setTimeInMillis(endDateMillis);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);

                    if (isStartDate) {
                        // Set to start of day
                        startDateMillis = selected.getTimeInMillis();
                        tvStartDate.setText(dateFormat.format(new Date(startDateMillis)));
                        btnClearStartDate.setVisibility(View.VISIBLE);
                    } else {
                        // Set to end of day
                        selected.set(Calendar.HOUR_OF_DAY, 23);
                        selected.set(Calendar.MINUTE, 59);
                        selected.set(Calendar.SECOND, 59);
                        endDateMillis = selected.getTimeInMillis();
                        tvEndDate.setText(dateFormat.format(new Date(endDateMillis)));
                        btnClearEndDate.setVisibility(View.VISIBLE);
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Set min date for end date picker
        if (!isStartDate && startDateMillis != null) {
            datePickerDialog.getDatePicker().setMinDate(startDateMillis);
        }

        datePickerDialog.show();
    }

    private void setupLocationDropdown() {
        // Add "All Locations" as first option
        List<String> locationOptions = new ArrayList<>();
        locationOptions.add("All Locations");
        locationOptions.addAll(availableLocations);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                locationOptions
        );
        dropdownLocation.setAdapter(adapter);

        dropdownLocation.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            selectedLocation = "All Locations".equals(selected) ? null : selected;
        });
    }

    private void setupEventTypeChips() {
        chipGroupEventType.removeAllViews();

        for (String type : Constants.EVENT_TYPES) {
            Chip chip = new Chip(requireContext());
            chip.setText(type);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);

            // Check "All" by default
            if (TYPE_ALL.equals(type)) {
                chip.setChecked(true);
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedEventType = type;
                    // Uncheck other chips
                    for (int i = 0; i < chipGroupEventType.getChildCount(); i++) {
                        Chip otherChip = (Chip) chipGroupEventType.getChildAt(i);
                        if (!otherChip.getText().equals(type)) {
                            otherChip.setChecked(false);
                        }
                    }
                }
            });

            chipGroupEventType.addView(chip);
        }
    }

    private void setupButtons() {
        btnClose.setOnClickListener(v -> dismiss());

        btnClearFilters.setOnClickListener(v -> {
            clearAllFilters();
        });

        btnApplyFilters.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFilterApplied(startDateMillis, endDateMillis, selectedLocation, selectedEventType);
            }
            dismiss();
        });
    }

    private void clearAllFilters() {
        // Clear dates
        startDateMillis = null;
        endDateMillis = null;
        tvStartDate.setText("Select start date");
        tvEndDate.setText("Select end date");
        btnClearStartDate.setVisibility(View.GONE);
        btnClearEndDate.setVisibility(View.GONE);

        // Clear location
        selectedLocation = null;
        dropdownLocation.setText("All Locations", false);

        // Reset event type to "All"
        selectedEventType = TYPE_ALL;
        for (int i = 0; i < chipGroupEventType.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupEventType.getChildAt(i);
            chip.setChecked(TYPE_ALL.equals(chip.getText().toString()));
        }
    }

    private void restoreFilterState() {
        // Restore start date
        if (startDateMillis != null) {
            tvStartDate.setText(dateFormat.format(new Date(startDateMillis)));
            btnClearStartDate.setVisibility(View.VISIBLE);
        }

        // Restore end date
        if (endDateMillis != null) {
            tvEndDate.setText(dateFormat.format(new Date(endDateMillis)));
            btnClearEndDate.setVisibility(View.VISIBLE);
        }

        // Restore location
        if (selectedLocation != null) {
            dropdownLocation.setText(selectedLocation, false);
        } else {
            dropdownLocation.setText("All Locations", false);
        }

        // Restore event type
        for (int i = 0; i < chipGroupEventType.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupEventType.getChildAt(i);
            chip.setChecked(selectedEventType.equals(chip.getText().toString()));
        }
    }

    /**
     * Interface for filter applied callback
     */
    public interface OnFilterAppliedListener {
        void onFilterApplied(Long startDate, Long endDate, String location, String eventType);
    }
}