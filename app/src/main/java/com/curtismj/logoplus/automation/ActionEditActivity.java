package com.curtismj.logoplus.automation;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.curtismj.logoplus.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.twofortyfouram.locale.sdk.client.ui.activity.AbstractAppCompatPluginActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActionEditActivity extends AbstractAppCompatPluginActivity implements AutomationInstruction.RemoveCallback {

    int instSeq = Integer.MIN_VALUE;
    LinearLayout instsLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action);

        instsLayout = findViewById(R.id.instsLayout);

        FloatingActionButton addButton = findViewById(R.id.addInstructionButton);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                instsLayout.addView(new AutomationInstruction(ActionEditActivity.this, instSeq++, ActionEditActivity.this));
            }
        });
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

    @Override
    public void RemoveMe(AutomationInstruction view) {
        instsLayout.removeView(view);
    }
}
