package com.example.pickme.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * THIS JAVADOC WAS ASSISTED BY AI
 * ViewModel for the Notifications screen.
 * <p>
 * This class is responsible for preparing and managing the data for the NotificationsFragment.
 * It retains its data across configuration changes, preventing data loss when the device is rotated.
 * The ViewModel exposes UI-related data via LiveData, which the UI can observe for changes.
 * Covers user stories that utilize the notification viewing fragment
 * US: 01.04.01,01.04.02
 */
public class NotificationsViewModel extends ViewModel {
    /**
     * Holds the text to be displayed on the notifications screen.
     * It is MutableLiveData so its value can be changed within this ViewModel.
     * The Fragment observes the public, immutable LiveData version of this object.
     */
    private final MutableLiveData<String> mText;
    /**
     * Initializes the ViewModel and sets the initial value for the text.
     */
    public NotificationsViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is notifications fragment");
    }
    /**
     * Returns the LiveData object containing the text to be displayed.
     * UI components can observe this data for changes. 
     *
     * @return A LiveData object holding the notification text.
     */
    public LiveData<String> getText() {
        return mText;
    }
}