package com.curtismj.logoplus.fsm;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.curtismj.logoplus.MicroCodeManager;
import com.curtismj.logoplus.persist.UIState;

public class BaseLogoMachine extends StateMachine {

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_STATIC= 1;
    public static final int EFFECT_PULSE = 2;
    public static final int EFFECT_RAINBOW = 3;
    public static final int EFFECT_PINWHEEL = 4;
    public static final int EFFECT_ROLL = 5;

    public static final int STATE_SCREENON = 0;
    public static final int STATE_SCREENOFF = 1;
    public static final int STATE_NOTIF_UPDATE = 2;
    public static final int STATE_STATE_UPDATE = 3;
    public static final int STATE_RINGING= 5;
    public static final int STATE_RESTORE_JUNCTION= 6;
    public static final int STATE_POCKET_JUNCTION= 7;

    public static final int EVENT_SCREENON = 0;
    public static final int EVENT_SCREENOFF = 1;
    public static final int EVENT_NOTIF_UPDATE =  2;
    public static final int EVENT_STATE_UPDATE =  3;
    public static final int EVENT_RING = 6;
    public static final int EVENT_STOP_RING = 7;
    public static final int EVENT_POCKET_MODE = 8;

    public static final int LED_PASSIVE =  0;
    public static final int LED_NOTIF = 1;
    public static final int LED_BLANK=  2;
    public static final int LED_RING =  4;

    protected int LEDState;
    protected UIState state;
    protected  int[] latestNotifs = new int[0];
    protected  int ringColor;
    protected boolean inPocket = false;
    protected String[] currentPassiveProgram;

    protected PowerManager pm;
    protected  Context context;

    public static  boolean ValidateEffectNo(int no)
    {
        return (no >= 0) && (no <= 5);
    }

    private void  runEffect() {
        if (currentPassiveProgram != null)
            runProgram(currentPassiveProgram);
        else
            blankLights();
    }

    protected void blankLights() {
        // base does nothing
    }

    protected void runProgram(String[] program) {
        // base does nothing
    }

    private void updateState(UIState _state)
    {
        state = _state;
        switch (state.passiveEffect) {
            case EFFECT_NONE:
                currentPassiveProgram = null;
                break;
            case EFFECT_STATIC:
                currentPassiveProgram = MicroCodeManager.staticProgramBuild(state.passiveColor);
                break;
            case EFFECT_PULSE:
                currentPassiveProgram = MicroCodeManager.pulseProgramBuild(state.effectLength, state.passiveColor);
                break;
            case EFFECT_RAINBOW:
                currentPassiveProgram = MicroCodeManager.rainbowProgramBuild(state.effectLength, false);
                break;
            case EFFECT_PINWHEEL:
                currentPassiveProgram = MicroCodeManager.rainbowProgramBuild(state.effectLength, true);
                break;
            case EFFECT_ROLL:
                currentPassiveProgram = MicroCodeManager.rollProgramBuild();
                break;
        }
    }

    public BaseLogoMachine(Context _context, final UIState initial) {
        super();

        updateState(initial);
        context = _context;
        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // These states can be interrupted by global state update events
        final int[] updatableStates = new int[]{
          STATE_SCREENON,
          STATE_SCREENOFF,
          STATE_RINGING};

        final int[][] updatableFanOut = new int[][] {
          {EVENT_SCREENON,STATE_SCREENON},
          {EVENT_SCREENOFF,STATE_SCREENOFF},
          {EVENT_RING,STATE_RINGING}
        };

        this
                // Mid state notif updates (usually silent)
                .FanIn(updatableStates, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)

                // Notif update back to state
                .FanOut(STATE_NOTIF_UPDATE, updatableFanOut)

                // Mid state global settings updates (usually NOT silent)
                .FanIn(updatableStates, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)

                // Global settings back to state
                .FanOut(STATE_STATE_UPDATE, updatableFanOut)

                .Enter(STATE_NOTIF_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        latestNotifs = (int[])arg;
                        sm.ReverseFanIn(updatableFanOut, otherState);
                    }
                })

                .Enter(STATE_STATE_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        updateState((UIState) arg);
                        sm.ReverseFanIn(updatableFanOut, otherState);
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
                        inPocket = false;
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
                        if (inPocket)
                        {
                            if (LEDState != LED_BLANK) {
                                LEDState = LED_BLANK;
                                blankLights();
                            }
                            return;
                        }

                        if (latestNotifs.length > 0 ) {
                            if (LEDState != LED_NOTIF) {
                                LEDState = LED_NOTIF;
                                runProgram(MicroCodeManager.notifyProgramBuild(latestNotifs));
                            }
                        }
                        else if (!state.powerSave)
                        {
                            if (LEDState != LED_PASSIVE) {
                                LEDState = LED_PASSIVE;
                                runEffect();
                            }
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
                        inPocket = true;
                        sm.Event(STATE_SCREENOFF);
                    }
                })

                .FanIn(new int[] { STATE_SCREENOFF, STATE_SCREENON }, EVENT_RING, STATE_RINGING)
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
