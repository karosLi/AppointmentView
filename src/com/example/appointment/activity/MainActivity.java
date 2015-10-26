package com.example.appointment.activity;

import com.example.appointment.AppointmentFragment;
import com.example.appointment.R;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

public class MainActivity extends Activity {
	
	private FragmentManager mFrManager;
	private FragmentTransaction mFrTransaction;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		
		mFrManager = getFragmentManager();
		mFrTransaction = mFrManager.beginTransaction();
		AppointmentFragment appFragment = new AppointmentFragment(System.currentTimeMillis(), 4);
		mFrTransaction.replace(R.id.main_pane, appFragment);
		mFrTransaction.commit();
	}
}
