package com.curtismj.logoplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Collection;
import java.util.HashMap;

public class LogoPlusNotificationListener extends NotificationListenerService {
    private SharedPreferences settings;
    private BroadcastReceiver resyncReceiver;
    Intent sendColor;

    @Override
    public void onCreate() {
        super.onCreate();
        notifs = new ArrayMap<>();
        settings = getSharedPreferences(BuildConfig.APPLICATION_ID + ".prefs", Context.MODE_PRIVATE);
        sendColor = new Intent();
        sendColor.setAction(LogoPlusService.SEND_EFFECT);
        IntentFilter intentFilter = new IntentFilter(LogoPlusService.START_BROADCAST);
        resyncReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(LogoPlusService.START_BROADCAST)) {
                    Log.d("debug", "main service starting, syncing state");
                    notifyServiceChange();
                }
            }
        };
        registerReceiver(resyncReceiver, intentFilter);
    }

    ArrayMap<String, Integer> notifs;

    public static int[] toPrimitive(Integer[] IntegerArray) {

        int[] result = new int[IntegerArray.length];
        for (int i = 0; i < IntegerArray.length; i++) {
            result[i] = IntegerArray[i];
        }
        return result;
    }

    private  void notifyServiceChange() {
        Log.d("debug", "update notfis requested");
        Collection<Integer> vals = notifs.values();
        Integer[] colors = vals.toArray(new Integer[vals.size()]);
        sendColor.putExtra("notif", true);
        sendColor.putExtra("colors", toPrimitive(colors));
        sendBroadcast(sendColor);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
            String key = "COLOR:" + sbn.getPackageName();
            synchronized (notifs)
            {
                if (!settings.contains(key) || notifs.containsKey(key)) return;
                notifs.put(key, settings.getInt(key, Color.RED));
                notifyServiceChange();
            }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        String key = "COLOR:" + sbn.getPackageName();
        synchronized (notifs)
        {
            if (notifs.containsKey(key)) notifs.remove(key);
            notifyServiceChange();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(resyncReceiver);
    }
}
