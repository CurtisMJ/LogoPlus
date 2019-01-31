package com.curtismj.logoplus;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Log;

import com.curtismj.logoplus.persist.AppNotification;
import com.curtismj.logoplus.persist.LogoDao;
import com.curtismj.logoplus.persist.LogoDatabase;


public class LogoPlusNotificationListener extends NotificationListenerService implements  ServiceConnection {
    private BroadcastReceiver resyncReceiver;
    private Messenger mMessenger;
    private volatile boolean mBound = false;
    private volatile boolean everBound = false;
    public static  final  String START_BROADCAST = BuildConfig.APPLICATION_ID + ".ListenerServiceAlive";
    private LogoDatabase db;
    private LogoDao dao;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    public static final int SERVICE_START = 0;
    public static final int NOTIF_POSTED = 1;
    public static final int NOTIF_REMOVED = 2;

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

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SERVICE_START:
                    db = LogoDatabase.getInstance(getApplicationContext());
                    dao = db.logoDao();
                    break;

                case NOTIF_POSTED:
                    String pkg = (String) msg.obj;
                    Log.d("debug", "notif" + pkg);
                    AppNotification notif = dao.getAppNotification(pkg).onErrorReturnItem(new AppNotification()).blockingGet();
                    if (notif.color != null) {
                        notifs.put(notif.packageName, notif.color);
                        notifyServiceChange();
                    }
                    break;

                case NOTIF_REMOVED:
                    String key = (String) msg.obj;
                    if (notifs.containsKey(key)) {
                        notifs.remove(key);
                        notifyServiceChange();
                    }
                    break;
            }
        }
    }


    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d("debug", "notification listener is starting");
        notifs = new ArrayMap<>();

        IntentFilter intentFilter = new IntentFilter(LogoPlusService.START_BROADCAST);
        resyncReceiver = new ResyncReceiver(this);
        registerReceiver(resyncReceiver, intentFilter);
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(START_BROADCAST);
        sendBroadcast(broadCastIntent);

        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
        Message msg = mServiceHandler.obtainMessage(SERVICE_START);
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        mServiceLooper.quitSafely();
        Log.d("debug", "notification listener is stopping");
        unregisterReceiver(resyncReceiver);
        notifs.clear();
        notifyServiceChange();
        if (everBound) unbindService(this);
        db = null;
        dao = null;
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
        mServiceHandler = new ServiceHandler(mServiceLooper);
        Message msg = mServiceHandler.obtainMessage(NOTIF_POSTED, sbn.getPackageName());
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        mServiceHandler = new ServiceHandler(mServiceLooper);
        Message msg = mServiceHandler.obtainMessage(NOTIF_REMOVED, sbn.getPackageName());
        mServiceHandler.sendMessage(msg);
    }

}
