package com.curtismj.logoplus.fsm;

import android.content.Context;
import android.os.PowerManager;

import com.curtismj.logoplus.MicroCodeManager;
import com.curtismj.logoplus.persist.UIState;

public class BaseLogoMachine extends StateMachine {

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_STATIC= 1;
    public static final int EFFECT_PULSE = 2;
    public static final int EFFECT_RAINBOW = 3;
    public static final int EFFECT_PINWHEEL = 4;

    public static final int STATE_SCREENON = 0;
    public static final int STATE_SCREENOFF = 1;
    public static final int STATE_NOTIF_UPDATE = 2;
    public static final int STATE_STATE_UPDATE = 3;
    public static final int STATE_VISUALIZER = 4;
    public static final int STATE_RINGING= 5;
    public static final int STATE_RESTORE_JUNCTION= 6;
    public static final int STATE_POCKET_JUNCTION= 7;

    public static final int EVENT_SCREENON = 0;
    public static final int EVENT_SCREENOFF = 1;
    public static final int EVENT_NOTIF_UPDATE =  2;
    public static final int EVENT_STATE_UPDATE =  3;
    public static final int EVENT_ENTER_VISUALIZER=  4;
    public static final int EVENT_EXIT_VISUALIZER=  5;
    public static final int EVENT_RING = 6;
    public static final int EVENT_STOP_RING = 7;
    public static final int EVENT_POCKET_MODE = 8;

    public static final int LED_PASSIVE =  0;
    public static final int LED_NOTIF = 1;
    public static final int LED_BLANK=  2;
    public static final int LED_VIS =  3;
    public static final int LED_RING =  4;

    public static final int ARGUMENT_POCKET_MODE = 0;

    protected int LEDState;
    protected UIState state;
    protected  int[] latestNotifs = new int[0];
    protected  int ringColor;

    protected PowerManager pm;
    protected  Context context;

    protected void  runEffect() {
        switch (state.passiveEffect) {
            case EFFECT_NONE:
                blankLights();
                break;
            case EFFECT_STATIC:
                runProgram(MicroCodeManager.staticProgramBuild(state.passiveColor));
                break;
            case EFFECT_PULSE:
                runProgram(MicroCodeManager.pulseProgramBuild(state.effectLength, state.passiveColor));
                break;
            case EFFECT_RAINBOW:
                runProgram(MicroCodeManager.rainbowProgramBuild(state.effectLength, false));
                break;
            case EFFECT_PINWHEEL:
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

    private void backtrackState(int otherState, StateMachine sm)
    {
        switch (otherState)
        {
            case STATE_SCREENON:
                sm.Event(EVENT_SCREENON);
                break;
            case STATE_SCREENOFF:
                sm.Event(EVENT_SCREENOFF);
                break;
            case STATE_RINGING:
                sm.Event(EVENT_RING);
                break;
        }
    }

    public BaseLogoMachine(Context _context, UIState initial) {
        super();

        state = initial;
        context = _context;
        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        this
                // Mid state notif updates (usually silent)
                .Transition(STATE_SCREENON, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)
                .Transition(STATE_SCREENOFF, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)
                .Transition(STATE_RINGING, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)

                // Notif update back to state
                .Transition(STATE_NOTIF_UPDATE, EVENT_SCREENON, STATE_SCREENON)
                .Transition(STATE_NOTIF_UPDATE, EVENT_SCREENOFF, STATE_SCREENOFF)
                .Transition(STATE_NOTIF_UPDATE, EVENT_RING, STATE_RINGING)

                // Mid state global settings updates (usually NOT silent)
                .Transition(STATE_SCREENON, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)
                .Transition(STATE_SCREENOFF, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)
                .Transition(STATE_RINGING, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)

                // Global settings back to state
                .Transition(STATE_STATE_UPDATE, EVENT_SCREENON, STATE_SCREENON)
                .Transition(STATE_STATE_UPDATE, EVENT_SCREENOFF, STATE_SCREENOFF)
                .Transition(STATE_STATE_UPDATE, EVENT_RING, STATE_RINGING)

                .Enter(STATE_NOTIF_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        latestNotifs = (int[])arg;
                        backtrackState(otherState, sm);
                    }
                })

                .Enter(STATE_STATE_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        state = (UIState) arg;
                        backtrackState(otherState, sm);
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
                        if (arg != null && (int)arg == ARGUMENT_POCKET_MODE)
                        {
                            if (LEDState != LED_BLANK) {
                                LEDState = LED_BLANK;
                                blankLights();
                            }
                            return;
                        }

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
                })

                .Transition(STATE_SCREENOFF, EVENT_POCKET_MODE, STATE_POCKET_JUNCTION)
                .Transition(STATE_POCKET_JUNCTION, EVENT_SCREENOFF, STATE_SCREENOFF)

                .Enter(STATE_POCKET_JUNCTION, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        sm.Event(STATE_SCREENOFF, ARGUMENT_POCKET_MODE);
                    }
                })

                .Transition(STATE_SCREENOFF, EVENT_RING, STATE_RINGING)
                .Transition(STATE_SCREENON, EVENT_RING, STATE_RINGING)
                .Transition(STATE_RINGING, EVENT_STOP_RING, STATE_RESTORE_JUNCTION)

                .Enter(STATE_RINGING, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (arg != null)
                        {
                            ringColor = (Integer)arg;
                        }
                        if (LEDState != LED_RING)
                        {
                            LEDState = LED_RING;
                            runProgram(MicroCodeManager.ringProgramBuild(ringColor));
                        }
                    }
                })
                .Exit(STATE_RINGING, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (otherState == EVENT_NOTIF_UPDATE) return;
                        if (LEDState != LED_BLANK) {
                            LEDState = LED_BLANK;
                            blankLights();
                        }
                    }
                })

                .Transition(STATE_RESTORE_JUNCTION, EVENT_SCREENON, STATE_SCREENON)
                .Transition(STATE_RESTORE_JUNCTION, EVENT_SCREENOFF, STATE_SCREENOFF)
                .Enter(STATE_RESTORE_JUNCTION, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        sm.Event(pm.isInteractive() ? EVENT_SCREENON : EVENT_SCREENOFF);
                    }
                });
        
    }
}
