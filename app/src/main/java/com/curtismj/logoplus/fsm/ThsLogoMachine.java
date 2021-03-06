package com.curtismj.logoplus.fsm;

import android.app.ThsManager;
import android.content.Context;
import com.curtismj.logoplus.persist.UIState;

public class ThsLogoMachine extends BaseLogoMachine {

    private ThsManager thsService;

    @Override
    protected void blankLights() {
            thsService.suspendThs();
    }

    @Override
    protected void runProgram(String[] program) {
            thsService.runThsProgram(program[3], program[0], program[1], program[2]);
    }

    public ThsLogoMachine(UIState initial, Context _context) {
        super(_context, initial);
        thsService = (ThsManager) context.getSystemService("ths");
    }

}
