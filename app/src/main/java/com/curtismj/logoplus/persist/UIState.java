package com.curtismj.logoplus.persist;

import android.graphics.Color;

import com.curtismj.logoplus.LogoPlusService;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class UIState {
    @PrimaryKey
    public  int id;

    public boolean showSystemApps;
    public boolean serviceEnabled;
    public int currentView;
    public int brightness;
    public int passiveEffect;
    public int passiveColor;
    public float effectLength;
    public  boolean powerSave;
    public boolean ringAnimation;

    public UIState()
    {
        id = 1;
        showSystemApps = false;
        serviceEnabled = false;
        currentView = 0;
        brightness = 128;
        passiveEffect = LogoPlusService.EFFECT_NONE;
        passiveColor = Color.GREEN;
        effectLength = 6000f;
        powerSave = true;
        ringAnimation = false;
    }
}
