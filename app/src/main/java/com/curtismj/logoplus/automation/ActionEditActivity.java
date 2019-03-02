package com.curtismj.logoplus.automation;

import android.os.Bundle;
import android.app.Activity;

import com.curtismj.logoplus.R;
import com.twofortyfouram.locale.sdk.client.ui.activity.AbstractAppCompatPluginActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActionEditActivity extends AbstractAppCompatPluginActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action);
    }

    @Override
    public boolean isBundleValid(@NonNull Bundle bundle) {
        return false;
    }

    @Override
    public void onPostCreateWithPreviousResult(@NonNull Bundle bundle, @NonNull String s) {

    }

    @Nullable
    @Override
    public Bundle getResultBundle() {
        return null;
    }

    @NonNull
    @Override
    public String getResultBlurb(@NonNull Bundle bundle) {
        return null;
    }
}
