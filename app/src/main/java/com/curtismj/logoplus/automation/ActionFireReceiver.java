package com.curtismj.logoplus.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.curtismj.logoplus.BuildConfig;
import com.curtismj.logoplus.LogoPlusService;
import com.twofortyfouram.locale.sdk.client.receiver.AbstractPluginSettingReceiver;

import androidx.annotation.NonNull;

public class ActionFireReceiver extends AbstractPluginSettingReceiver {

    public static final String KEY_PASSIVE_EFFECT = BuildConfig.APPLICATION_ID + ".effectNo";
    public static final String KEY_PASSIVE_COLOR = BuildConfig.APPLICATION_ID + ".effectColor";
    public static final String KEY_PASSIVE_LEN = BuildConfig.APPLICATION_ID + ".effectLength";
    public static final String KEY_PASSIVE_LOCK = BuildConfig.APPLICATION_ID + ".effectLock";
    public static final String KEY_BRIGHTNESS = BuildConfig.APPLICATION_ID + ".brightness";

    @Override
    protected boolean isBundleValid(@NonNull Bundle bundle) {
        try
        {
            if (bundle.containsKey(KEY_PASSIVE_EFFECT)) Integer.parseInt(bundle.getString(KEY_PASSIVE_EFFECT));
            if (bundle.containsKey(KEY_PASSIVE_COLOR)) Integer.parseInt(bundle.getString(KEY_PASSIVE_COLOR));
            if (bundle.containsKey(KEY_PASSIVE_LEN)) Integer.parseInt(bundle.getString(KEY_PASSIVE_LEN));
            if (bundle.containsKey(KEY_PASSIVE_LOCK)) Integer.parseInt(bundle.getString(KEY_PASSIVE_LOCK));
            if (bundle.containsKey(KEY_BRIGHTNESS)) Integer.parseInt(bundle.getString(KEY_BRIGHTNESS));
        }
        catch (NumberFormatException ex)
        {
            return false;
        }
        return true;
    }

    @Override
    protected boolean isAsync() {
        return false;
    }

    @Override
    protected void firePluginSetting(@NonNull Context context, @NonNull Bundle bundle) {
        Intent fireIntent = new Intent();
        fireIntent.setAction(LogoPlusService.FIRE_AUTOMATION);
        fireIntent.putExtras(bundle);
        context.sendBroadcast(fireIntent);

    }
}
