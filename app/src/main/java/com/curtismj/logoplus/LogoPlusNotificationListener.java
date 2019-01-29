package com.curtismj.logoplus;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Log;


public class LogoPlusNotificationListener extends NotificationListenerService implements  ServiceConnection {
    private SharedPreferences settings;
    private BroadcastReceiver resyncReceiver;
    private Messenger mMessenger;
    private volatile boolean mBound = false;
    private volatile boolean everBound = false;
    public static  final  String START_BROADCAST = BuildConfig.APPLICATION_ID + ".ListenerServiceAlive";

    public void onServiceConnected(ComponentName className, IBinder service) {
        Log.d("debug", "remote service connecting");
        mMessenger = new Messenger(service);
        mBound = true;
        notifyServiceChange();
    }

    public void onServiceDisconnected(ComponentName className) {
        Log.d("debug", "remote service disconnecting");
        mMessenger = null;
        mBound = false;
    }

    private static class ResyncReceiver extends BroadcastReceiver {
        LogoPlusNotificationListener callback;

        public ResyncReceiver(LogoPlusNotificationListener parent) {
            callback = parent;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(LogoPlusService.START_BROADCAST)) {
                Log.d("debug", "rebind");
                if (!callback.mBound) {
                    callback.everBound = true;
                    callback.bindService(new Intent(callback, LogoPlusService.class), callback, 0);
                }
            }
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d("debug", "notification listener is starting");
        notifs = new ArrayMap<>();
        settings = getSharedPreferences(BuildConfig.APPLICATION_ID + ".prefs", Context.MODE_PRIVATE);
        IntentFilter intentFilter = new IntentFilter(LogoPlusService.START_BROADCAST);
        resyncReceiver = new ResyncReceiver(this);
        registerReceiver(resyncReceiver, intentFilter);
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(START_BROADCAST);
        sendBroadcast(broadCastIntent);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d("debug", "notification listener is stopping");
        unregisterReceiver(resyncReceiver);
        notifs.clear();
        notifyServiceChange();
        if (everBound) unbindService(this);
    }

    ArrayMap<String, Integer> notifs;

    private  void notifyServiceChange() {
        Log.d("debug", "update notfis requested");
        if (mBound) {
            int[] colors = new int[notifs.size()];
            for (int i = 0; i < colors.length; i++)
            {
                colors[i] = notifs.valueAt(i);
            }
            Message msg =  Message.obtain(null, LogoPlusService.NOTIF_PUSH, 0, 0);
            Bundle bundle = new Bundle();
            bundle.putBoolean("notif", true);
            bundle.putIntArray("colors", colors);
            msg.setData(bundle);
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
            String key = "COLOR:" + sbn.getPackageName();
            Log.d("debug", "notif" + key);
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

}
