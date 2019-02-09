package com.curtismj.logoplus.persist;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class RingColor {
    @PrimaryKey
    @NonNull
    public String number;

    public int color;
    public String friendlyName;

    public  RingColor(String number, int color, String friendlyName)
    {
        this.number = number;
        this.color = color;
        this.friendlyName = friendlyName;
    }
}
