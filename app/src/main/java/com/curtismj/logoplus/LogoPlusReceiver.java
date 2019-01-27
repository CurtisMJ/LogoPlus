package com.curtismj.logoplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class LogoPlusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if(Intent.ACTION_BOOT_COMPLETED.equals(action))
        {
            SharedPreferences prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID + ".prefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("ServiceEnabled", false)) {
                Intent serviceStartIntent = new Intent(context, LogoPlusService.class);
                context.startService(serviceStartIntent);
            }
        }
    }
}
