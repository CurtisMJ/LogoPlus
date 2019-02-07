package com.curtismj.logoplus.fsm;

import android.graphics.Color;

import com.curtismj.logoplus.LogoPlusService;
import com.curtismj.logoplus.MicroCodeManager;
import com.curtismj.logoplus.persist.UIState;

public class BaseLogoMachine extends StateMachine {

    public static final int STATE_SCREENON = 0;
    public static final int STATE_SCREENOFF = 1;
    public static final int STATE_NOTIF_UPDATE = 2;
    public static final int STATE_STATE_UPDATE = 3;
    public static final int STATE_VISUALIZER = 4;
    public static final int STATE_VISUALIZER_JUNCTION= 5;

    public static final int EVENT_SCREENON = 0;
    public static final int EVENT_SCREENOFF = 1;
    public static final int EVENT_NOTIF_UPDATE =  2;
    public static final int EVENT_NOTIF_UPDATE_OFF =  3;
    public static final int EVENT_NOTIF_UPDATE_ON =  4;
    public static final int EVENT_STATE_UPDATE =  5;
    public static final int EVENT_STATE_UPDATE_OFF =  6;
    public static final int EVENT_STATE_UPDATE_ON =  7;
    public static final int EVENT_ENTER_VISUALIZER=  8;
    public static final int EVENT_EXIT_VISUALIZER=  9;

    public static final int LED_PASSIVE =  0;
    public static final int LED_NOTIF = 1;
    public static final int LED_BLANK=  2;
    public static final int LED_VIS =  3;

    protected int LEDState;
    protected UIState state;
    protected  int[] latestNotifs = new int[0];

    protected void  runEffect() {
        switch (state.passiveEffect) {
            case LogoPlusService.EFFECT_NONE:
                blankLights();
                break;
            case LogoPlusService.EFFECT_STATIC:
                runProgram(MicroCodeManager.staticProgramBuild(state.passiveColor));
                break;
            case LogoPlusService.EFFECT_PULSE:
                runProgram(MicroCodeManager.pulseProgramBuild(state.effectLength, state.passiveColor));
                break;
            case LogoPlusService.EFFECT_RAINBOW:
                runProgram(MicroCodeManager.rainbowProgramBuild(state.effectLength, false));
                break;
            case LogoPlusService.EFFECT_PINWHEEL:
                runProgram(MicroCodeManager.rainbowProgramBuild(state.effectLength, true));
                break;
        }
    }

    protected void blankLights() {
        // base does nothing
    }

    protected void runProgram(String[] program) {
        // base does nothing
    }

    public BaseLogoMachine(UIState initial) {
        super();

        state = initial;

        this
                .Transition(STATE_SCREENON, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)
                .Transition(STATE_NOTIF_UPDATE, EVENT_NOTIF_UPDATE_ON, STATE_SCREENON)
                .Transition(STATE_SCREENOFF, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)
                .Transition(STATE_NOTIF_UPDATE, EVENT_NOTIF_UPDATE_OFF, STATE_SCREENOFF)

                .Transition(STATE_SCREENON, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)
                .Transition(STATE_STATE_UPDATE, EVENT_STATE_UPDATE_ON, STATE_SCREENON)
                .Transition(STATE_SCREENOFF, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)
                .Transition(STATE_STATE_UPDATE, EVENT_STATE_UPDATE_OFF, STATE_SCREENOFF)

                .Enter(STATE_NOTIF_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        latestNotifs = (int[])arg;
                        sm.Event(otherState == STATE_SCREENON ? EVENT_NOTIF_UPDATE_ON : EVENT_NOTIF_UPDATE_OFF);
                    }
                })

                .Enter(STATE_STATE_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        state = (UIState) arg;
                        sm.Event(otherState == STATE_SCREENON ? EVENT_STATE_UPDATE_ON : EVENT_STATE_UPDATE_OFF);
                    }
                })

                .Transition(STATE_SCREENOFF, EVENT_SCREENON, STATE_SCREENON)
                .Transition(STATE_SCREENON, EVENT_SCREENOFF, STATE_SCREENOFF)

                .Enter(STATE_SCREENON, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (LEDState != LED_PASSIVE) {
                            LEDState = LED_PASSIVE;
                            runEffect();
                        }
                    }
                })
                .Exit(STATE_SCREENON, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (!state.powerSave && otherState == STATE_SCREENOFF) return;
                        if (otherState == EVENT_NOTIF_UPDATE) return;
                        if (LEDState != LED_BLANK) {
                            LEDState = LED_BLANK;
                            blankLights();
                        }
                    }
                })

                .Enter(STATE_SCREENOFF, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (latestNotifs.length > 0 && LEDState != LED_NOTIF) {
                            LEDState = LED_NOTIF;
                            runProgram(MicroCodeManager.notifyProgramBuild(latestNotifs));
                        }
                        else if (!state.powerSave && LEDState != LED_PASSIVE)
                        {
                            LEDState = LED_PASSIVE;
                            runEffect();
                        }
                    }
                })
                .Exit(STATE_SCREENOFF, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (!state.powerSave && otherState == STATE_SCREENON) return;
                        if (LEDState != LED_BLANK) {
                            LEDState = LED_BLANK;
                            blankLights();
                        }
                    }
                });

    }
}
