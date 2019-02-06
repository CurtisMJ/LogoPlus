package com.curtismj.logoplus.fsm;

import android.app.ThsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.app.IThsService;
import com.curtismj.logoplus.persist.UIState;

public class ThsLogoMachine extends BaseLogoMachine {

    private ThsManager thsService;
    private  Context context;

    @Override
    protected void blankLights() {
            thsService.suspendThs();
    }

    @Override
    protected void runProgram(String[] program) {
            thsService.runThsProgram(program[3], program[0], program[1], program[2]);
    }

    public ThsLogoMachine(UIState initial, Context _context) {
        super(initial);
        context = _context;
        thsService = (ThsManager) context.getSystemService("ths");
    }

}
