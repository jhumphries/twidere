/*
 * Copyright (C) 2012 SP-time. All right reserved.
 *
 */

package org.mariotaku.twidere.receiver;

import org.mariotaku.twidere.Constants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import edu.ucdavis.earlybird.UCDService;

/**
 * 
 * @author mariotaku
 * 
 */
public class BootCompletedReceiver extends BroadcastReceiver implements Constants {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		final SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
				Context.MODE_PRIVATE);
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			context.startService(new Intent(INTENT_ACTION_SERVICE));
			if (preferences.getBoolean(PREFERENCE_KEY_UCD_DATA_PROFILING, false)) {
				context.startService(new Intent(context, UCDService.class));
			}
		}

	}
}
