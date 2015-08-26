package com.preferences;

import android.content.Context;
import android.preference.ListPreference;

public class ConditionalListPreference extends ListPreference {

	public ConditionalListPreference(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void setValue(String value) {
	    String mOldValue = getValue();
	    super.setValue(value);
	    if (!value.equals(mOldValue)) {
	        notifyDependencyChange(shouldDisableDependents());
	    }
	}

	@Override
	public boolean shouldDisableDependents() {
	    boolean shouldDisableDependents = super.shouldDisableDependents();
	    String value = getValue();
	    String expected = "1";
	    return shouldDisableDependents || value == null || !value.equals(1);
	}

}
