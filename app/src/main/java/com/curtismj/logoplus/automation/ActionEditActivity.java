package com.curtismj.logoplus.automation;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.curtismj.logoplus.BuildConfig;
import com.curtismj.logoplus.R;
import com.curtismj.logoplus.fsm.BaseLogoMachine;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.twofortyfouram.locale.sdk.client.ui.activity.AbstractAppCompatPluginActivity;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.chainfire.libsuperuser.Shell;

public class ActionEditActivity extends AbstractAppCompatPluginActivity implements AutomationInstruction.RemoveCallback {

    int instSeq = 0;
    LinearLayout instsLayout;
    List<AutomationInstruction> internalList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action);

        instsLayout = findViewById(R.id.instsLayout);

        FloatingActionButton addButton = findViewById(R.id.addInstructionButton);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutomationInstruction view = new AutomationInstruction(ActionEditActivity.this, instSeq++, ActionEditActivity.this);
                addInst(view);
            }
        });
    }

    private void addInst(AutomationInstruction view) {
        internalList.add(view);
        instsLayout.addView(view);
    }

    @Override
    protected void onDestroy() {
        internalList.clear();
        super.onDestroy();
    }

    @Override
    public boolean isBundleValid(@NonNull Bundle bundle) {
        return true;
    }

    @Override
    public void onPostCreateWithPreviousResult(@NonNull Bundle bundle, @NonNull String s) {
        AutomationInstruction inst;
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_EFFECT))
        {
            inst = new AutomationInstruction(this, instSeq++, this);
            inst.effectNo = bundle.getString(ActionFireReceiver.KEY_PASSIVE_EFFECT);
            inst.actionType = AutomationInstruction.TYPE_PASSIVE_EFFECT;
            inst.sync();
            addInst(inst);
        }
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_COLOR))
        {
            inst = new AutomationInstruction(this, instSeq++, this);
            inst.passColor = bundle.getString(ActionFireReceiver.KEY_PASSIVE_COLOR);
            inst.actionType = AutomationInstruction.TYPE_PASSIVE_COLOR;
            inst.sync();
            addInst(inst);
        }
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_LEN))
        {
            inst = new AutomationInstruction(this, instSeq++, this);
            inst.effectLen = bundle.getString(ActionFireReceiver.KEY_PASSIVE_LEN);
            inst.actionType = AutomationInstruction.TYPE_PASSIVE_LEN;
            inst.sync();
            addInst(inst);
        }
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_LOCK))
        {
            inst = new AutomationInstruction(this, instSeq++, this);
            inst.effectLock = bundle.getString(ActionFireReceiver.KEY_PASSIVE_LOCK);
            inst.actionType = AutomationInstruction.TYPE_PASSIVE_LOCK;
            inst.sync();
            addInst(inst);
        }
        if (bundle.containsKey(ActionFireReceiver.KEY_BRIGHTNESS))
        {
            inst = new AutomationInstruction(this, instSeq++, this);
            inst.bright = bundle.getString(ActionFireReceiver.KEY_BRIGHTNESS);
            inst.actionType = AutomationInstruction.TYPE_BRIGHTNESS;
            inst.sync();
            addInst(inst);
        }
    }



    @Nullable
    @Override
    public Bundle getResultBundle() {
        Bundle res = new Bundle();

        String effectNo = "",
        effectColor = "",
        effectLen = "",
        effectLock = "",
        brightness = "";

        int effectNoSeq = Integer.MIN_VALUE,
        effectColorSeq = Integer.MIN_VALUE,
        effectLenSeq = Integer.MIN_VALUE,
        effectLockSeq = Integer.MIN_VALUE,
        brightnessSeq = Integer.MIN_VALUE;
        for (AutomationInstruction inst : internalList)
        {
            switch (inst.actionType)
            {
                case AutomationInstruction.TYPE_PASSIVE_EFFECT:
                    if (inst.sequence > effectNoSeq)
                    {
                        effectNoSeq = inst.sequence;
                        effectNo = inst.effectNo;
                    }
                    break;

                case AutomationInstruction.TYPE_PASSIVE_COLOR:
                    if (inst.sequence > effectColorSeq)
                    {
                        effectColorSeq = inst.sequence;
                        effectColor = inst.passColor;
                    }
                    break;

                case AutomationInstruction.TYPE_PASSIVE_LEN:
                    if (inst.sequence > effectLenSeq)
                    {
                        effectLenSeq = inst.sequence;
                        effectLen = inst.effectLen;
                    }
                    break;

                case AutomationInstruction.TYPE_PASSIVE_LOCK:
                    if (inst.sequence > effectLockSeq)
                    {
                        effectLockSeq = inst.sequence;
                        effectLock = inst.effectLock;
                    }
                    break;

                case AutomationInstruction.TYPE_BRIGHTNESS:
                    if (inst.sequence > brightnessSeq)
                    {
                        brightnessSeq = inst.sequence;
                        brightness = inst.bright;
                    }
                    break;
            }
        }

        List<String> replaceKeys = new ArrayList<>();

        if (effectNoSeq != Integer.MIN_VALUE)
        {
            replaceKeys.add(ActionFireReceiver.KEY_PASSIVE_EFFECT);
            res.putString(ActionFireReceiver.KEY_PASSIVE_EFFECT, effectNo);
        }

        if (effectColorSeq != Integer.MIN_VALUE)
        {
            replaceKeys.add(ActionFireReceiver.KEY_PASSIVE_COLOR);
            res.putString(ActionFireReceiver.KEY_PASSIVE_COLOR, effectColor);
        }

        if (effectLenSeq != Integer.MIN_VALUE)
        {
            replaceKeys.add(ActionFireReceiver.KEY_PASSIVE_LEN);
            res.putString(ActionFireReceiver.KEY_PASSIVE_LEN, effectLen);
        }

        if (effectLockSeq != Integer.MIN_VALUE)
        {
            replaceKeys.add(ActionFireReceiver.KEY_PASSIVE_LOCK);
            res.putString(ActionFireReceiver.KEY_PASSIVE_LOCK, effectLock);
        }

        if (brightnessSeq != Integer.MIN_VALUE)
        {
            replaceKeys.add(ActionFireReceiver.KEY_BRIGHTNESS);
            res.putString(ActionFireReceiver.KEY_BRIGHTNESS, brightness);
        }

        if ( TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement( this ) )
            TaskerPlugin.Setting.setVariableReplaceKeys( res, replaceKeys.toArray(new String[0]) );

        return res;
    }

    @NonNull
    @Override
    public String getResultBlurb(@NonNull Bundle bundle) {
        String blurb = "";

        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_EFFECT)) blurb += "Passive Effect: " + bundle.getString(ActionFireReceiver.KEY_PASSIVE_EFFECT) + "\n";
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_COLOR)) blurb += "Passive Color: " + bundle.getString(ActionFireReceiver.KEY_PASSIVE_COLOR) + "\n";
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_LEN)) blurb += "Passive Length: " + bundle.getString(ActionFireReceiver.KEY_PASSIVE_LEN) + "\n";
        if (bundle.containsKey(ActionFireReceiver.KEY_PASSIVE_LOCK)) blurb += "Passive while unlocked: " + bundle.getString(ActionFireReceiver.KEY_PASSIVE_LOCK) + "\n";
        if (bundle.containsKey(ActionFireReceiver.KEY_BRIGHTNESS)) blurb += "Global Brightness: " + bundle.getString(ActionFireReceiver.KEY_BRIGHTNESS) + "\n";

        if (blurb.trim().equals("")) blurb = "Do nothing";
        return blurb;
    }

    @Override
    public void RemoveMe(AutomationInstruction view) {
        internalList.remove(view);
        instsLayout.removeView(view);
    }
}
