/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.appointment;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

/**
 * This is the base class for Day and Week Activities.
 */
public class AppointmentFragment extends Fragment implements AppointmentView.MessageHandler {

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    EventLoader mEventLoader;
    
    Time mSelectedDay = new Time();
    
    private final static String[] ALLCLERKS = new String[] {"Karos", "Colin", "Mechelle", "Tom", "Jay", "Will", "Benly", "Alisa", "Noah", "Jackie", "Rita"};

    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            if (!AppointmentFragment.this.isAdded()) {
                return;
            }
            String tz = CalendarUtils.getTimeZone(getActivity(), mTZUpdater);
            mSelectedDay.timezone = tz;
            mSelectedDay.normalize(true);
        }
    };
    
    private AppointmentView mDayView;

    private int mNumShownCols;
    
    public AppointmentFragment() {
        mSelectedDay.setToNow();
    }

    public AppointmentFragment(long timeMillis, int numColsInPage) {
        mNumShownCols = numColsInPage;
        if (timeMillis == 0) {
            mSelectedDay.setToNow();
        } else {
            mSelectedDay.set(timeMillis);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Context context = getActivity();
        mEventLoader = new EventLoader(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	ViewGroup v = (ViewGroup)inflater.inflate(R.layout.appointment_fragment, null);

        mTZUpdater.run();
        AppointmentView view = new AppointmentView(getActivity(), ALLCLERKS, mNumShownCols, mEventLoader, this);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.setSelected(mSelectedDay, "KAROS",  false, false);
        view.restartCurrentTimeUpdates();
        view.requestFocus();
        mDayView = view;
        v.addView(view);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mEventLoader.startBackgroundThread();
        mTZUpdater.run();
        eventsChanged();
        mDayView.handleOnResume();
        mDayView.restartCurrentTimeUpdates();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        long time = getSelectedTimeInMillis();
        if (time != -1) {
            outState.putLong(BUNDLE_KEY_RESTORE_TIME, time);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mDayView.cleanup();
        mEventLoader.stopBackgroundThread();
    }

    private void goTo(Time goToTime, boolean ignoreTime, boolean animateToday) {
    	mSelectedDay.set(goToTime);
    	
    	if (mDayView == null) {
    		return;
    	}
    	
    	mDayView.setSelected(goToTime, "KAROS", ignoreTime, animateToday);
    	mDayView.reloadEvents();
    	mDayView.requestFocus();
    	mDayView.restartCurrentTimeUpdates();
    }
    
    /**
     * Returns the selected time in milliseconds. The milliseconds are measured
     * in UTC milliseconds from the epoch and uniquely specifies any selectable
     * time.
     *
     * @return the selected time in milliseconds
     */
    public long getSelectedTimeInMillis() {
    	if (mDayView == null)
    		return -1;
    	
    	return mDayView.getSelectedTimeInMillis(); 
    }

    public void eventsChanged() {
    	if (mDayView == null)
    		return;
    	
    	mDayView.reloadEvents();
    }

	@Override
	public void handleMessage(EventMessage eventMsg) {
		switch(eventMsg.type) {
			case EventMessage.TYPE_NEW:
				break;
				
			case EventMessage.TYPE_VIEW:
				
				break;
				
			default:
					
		}
	}
}
