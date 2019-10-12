package com.curtismj.logoplus.automation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.curtismj.logoplus.CommonUtils;
import com.curtismj.logoplus.R;
import com.curtismj.logoplus.fsm.BaseLogoMachine;

public class AutomationInstruction extends FrameLayout {
    public static final int TYPE_PASSIVE_EFFECT = 0;
    public static final int TYPE_PASSIVE_COLOR = 1;
    public static final int TYPE_PASSIVE_LEN = 2;
    public static final int TYPE_PASSIVE_LOCK = 3;
    public static final int TYPE_BRIGHTNESS = 4;

    public interface RemoveCallback
    {
        void RemoveMe(AutomationInstruction view);
    }

    RemoveCallback callback;

    public AutomationInstruction(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    public AutomationInstruction(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public AutomationInstruction(Context context, int seq, RemoveCallback removeMe) {
        super(context);
        sequence = seq;
        callback = removeMe;
        initView(context);
    }

    String effectNo = "", effectLen = "", effectLock = "", bright = "";
    int actionType = 0;
    String passColor = Integer.toString(Color.GREEN);
    int sequence;

    private TextView passiveNum;
    private TextView colorText;
    private TextView passiveLen;
    private TextView passiveLock;
    private TextView brightness;
    private Spinner instSpinner;

    public void sync()
    {
        passiveNum.setText(effectNo);
        colorText.setText(passColor);
        passiveLen.setText(effectLen);
        passiveLock.setText(effectLock);
        brightness.setText(bright);
        instSpinner.setSelection(actionType);
    }

    private void initView(final Context context) {
        View view = inflate(context, R.layout.automation_instruction, null);
        final ViewFlipper flipper = view.findViewById(R.id.instSwitcher);
        instSpinner = view.findViewById(R.id.actionTypeSpinner);
        instSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                actionType = position;
                flipper.setDisplayedChild(actionType);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        Button removeBtn = view.findViewById(R.id.removeButton);
        removeBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.RemoveMe(AutomationInstruction.this);
            }
        });

        // Effect view special logic
        passiveNum = view.findViewById(R.id.passiveNum);
        passiveNum.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                effectNo = s.toString();
            }
        });

        // Color view special logic
        final View colorPicker = view.findViewById(R.id.colorPickerAuto);
        colorText = view.findViewById(R.id.passiveColor);
        colorText.setText(passColor);
        colorPicker.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int initColor = Color.GREEN;
                try {
                    initColor= Integer.parseInt(passColor);
                }
                catch (NumberFormatException ex)
                {
                }
                CommonUtils.colorPickDialog(context, initColor, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        passColor = Integer.toString(color);
                        colorText.setText(passColor);
                    }
                }, null, null, null);
            }
        });
        colorText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int color = Integer.parseInt(s.toString());
                    colorPicker.setBackgroundColor(color);
                }
                catch (NumberFormatException ex)
                {
                    colorPicker.setBackgroundColor(Color.BLACK);
                }
            }
        });

        // Effect length special logic
        passiveLen = view.findViewById(R.id.passiveLen);
        passiveLen.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                effectLen = s.toString();
            }
        });

        // Effect lock special logic
        passiveLock = view.findViewById(R.id.passiveUnlocked);
        passiveLock.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                effectLock = s.toString();
            }
        });

        // Brightness special logic
        brightness = view.findViewById(R.id.autoBright);
        brightness.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                bright = s.toString();
            }
        });

        addView(view);
    }
}
