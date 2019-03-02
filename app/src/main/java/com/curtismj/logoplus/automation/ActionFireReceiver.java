package com.curtismj.logoplus.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.twofortyfouram.locale.sdk.client.receiver.AbstractPluginSettingReceiver;

import androidx.annotation.NonNull;

public class ActionFireReceiver extends AbstractPluginSettingReceiver {

    @Override
    protected boolean isBundleValid(@NonNull Bundle bundle) {
        return false;
    }

    @Override
    protected boolean isAsync() {
        return false;
    }

    @Override
    protected void firePluginSetting(@NonNull Context context, @NonNull Bundle bundle) {

    }
}
