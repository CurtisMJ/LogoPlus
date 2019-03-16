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

    String effectNo = "";
    int passColor = Color.GREEN;
    int sequence;

    private void initView(final Context context) {
        View view = inflate(context, R.layout.automation_instruction, null);
        final ViewFlipper flipper = view.findViewById(R.id.instSwitcher);
        Spinner instSpinner = view.findViewById(R.id.actionTypeSpinner);
        instSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                flipper.setDisplayedChild(position);
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
        final TextView passiveNum = view.findViewById(R.id.passiveNum);
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
        final TextView colorText = view.findViewById(R.id.passiveColor);
        colorText.setText(Integer.toString(passColor));
        colorPicker.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.colorPickDialog(context, passColor, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        passColor = color;
                        colorText.setText(Integer.toString(passColor));
                    }
                }, null);
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

        addView(view);
    }
}
