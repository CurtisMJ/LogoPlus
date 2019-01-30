package com.curtismj.logoplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.curtismj.logoplus.persist.LogoDatabase;
import com.curtismj.logoplus.persist.UIState;

public class LogoPlusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if(Intent.ACTION_BOOT_COMPLETED.equals(action))
        {
            UIState state = LogoDatabase.getInstance(context.getApplicationContext()).logoDao().getUIState();
            if (state != null && state.serviceEnabled) {
                Intent serviceStartIntent = new Intent(context, LogoPlusService.class);
                context.startService(serviceStartIntent);
            }
        }
    }
}
