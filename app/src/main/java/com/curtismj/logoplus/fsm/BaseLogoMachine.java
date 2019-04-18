package com.curtismj.logoplus.fsm;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.curtismj.logoplus.MicroCodeManager;
import com.curtismj.logoplus.persist.UIState;

public class BaseLogoMachine extends StateMachine {

    private interface ProgramBuilder
    {
        String[] build();
    }

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
    public static final int STATE_CHARGE_UPDATE = 8;

    public static final int EVENT_SCREENON = 0;
    public static final int EVENT_SCREENOFF = 1;
    public static final int EVENT_NOTIF_UPDATE =  2;
    public static final int EVENT_STATE_UPDATE =  3;
    public static final int EVENT_RING = 6;
    public static final int EVENT_STOP_RING = 7;
    public static final int EVENT_POCKET_MODE = 8;
    public static final int EVENT_CHARGE_UPDATE =  9;

    public static final int LED_PASSIVE =  0;
    public static final int LED_NOTIF = 1;
    public static final int LED_BLANK=  2;
    public static final int LED_RING =  3;
    public static final int LED_INVALIDATED =  4;
    public static final int LED_STALE =  5;

    protected int LEDState;
    protected UIState state;
    protected  int[] latestNotifs = new int[0];
    protected  int ringColor;
    protected  int chargeLevel = -1;
    protected boolean inPocket = false;
    protected String[] currentPassiveProgram;

    protected PowerManager pm;
    protected  Context context;

    public static  boolean ValidateEffectNo(int no)
    {
        return (no >= 0) && (no <= 5);
    }

    private void stateSwitch(int targetState, ProgramBuilder builder)
    {
        if (LEDState != targetState) {
            if (LEDState != LED_STALE) _blankLights();
            LEDState = targetState;
            String[] newProgram = builder.build();
            if (newProgram != null)
                runProgram(newProgram);
        }
    }

    protected void blankLights() {
        // base does nothing
    }

    private void _blankLights() {
        if (LEDState != LED_BLANK) {
            LEDState = LED_BLANK;
            blankLights();
        }
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

        if (!state.batteryAnimation) chargeLevel = -1;
    }

    public BaseLogoMachine(Context _context, final UIState initial) {
        super();

        updateState(initial);
        context = _context;
        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // These states can settled in by the SM, and therefore can be interrupted by global state update events
        final int[] idleFanIn = new int[]{
          STATE_SCREENON,
          STATE_SCREENOFF,
          STATE_RINGING};

        final int[][] idleFanOut = new int[][] {
          {EVENT_SCREENON,STATE_SCREENON},
          {EVENT_SCREENOFF,STATE_SCREENOFF},
          {EVENT_RING,STATE_RINGING}
        };

        final ProgramBuilder currentPassiveBuilder = new ProgramBuilder() {
            @Override
            public String[] build() {
                return (chargeLevel > -1 ? MicroCodeManager.batteryProgramBuild(chargeLevel) : currentPassiveProgram);
            }
        };

        final ProgramBuilder notifsBuilder = new ProgramBuilder() {
            @Override
            public String[] build() {
                return MicroCodeManager.notifyProgramBuild(latestNotifs);
            }
        };

        final ProgramBuilder ringBuilder = new ProgramBuilder() {
            @Override
            public String[] build() {
                return MicroCodeManager.ringProgramBuild(ringColor);
            }
        };

        this
                // Mid state notif updates (usually silent)
                .FanIn(idleFanIn, EVENT_NOTIF_UPDATE, STATE_NOTIF_UPDATE)
                // Notif update back to state
                .FanOut(STATE_NOTIF_UPDATE, idleFanOut)
                .Enter(STATE_NOTIF_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        latestNotifs = (int[])arg;
                        if (LEDState == LED_NOTIF) LEDState = LED_INVALIDATED;
                        sm.ReverseFanIn(idleFanOut, otherState);
                    }
                })

                // Mid state global settings updates (usually NOT silent)
                .FanIn(idleFanIn, EVENT_STATE_UPDATE, STATE_STATE_UPDATE)
                // Global settings back to state
                .FanOut(STATE_STATE_UPDATE, idleFanOut)
                .Enter(STATE_STATE_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        updateState((UIState) arg);
                        LEDState = LED_STALE;
                        sm.ReverseFanIn(idleFanOut, otherState);
                    }
                })

                // Mid state charge level updates (selectively silent)
                .FanIn(idleFanIn, EVENT_CHARGE_UPDATE, STATE_CHARGE_UPDATE)
                // Charge level update back to state
                .FanOut(STATE_CHARGE_UPDATE, idleFanOut)
                .Enter(STATE_CHARGE_UPDATE, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        chargeLevel = (Integer)arg;
                        if (LEDState == LED_PASSIVE) LEDState = LED_STALE;
                        sm.ReverseFanIn(idleFanOut, otherState);
                    }
                })

                .Transition(STATE_SCREENOFF, EVENT_SCREENON, STATE_SCREENON)
                .Transition(STATE_SCREENON, EVENT_SCREENOFF, STATE_SCREENOFF)

                .Enter(STATE_SCREENON, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        stateSwitch(LED_PASSIVE, currentPassiveBuilder);
                        inPocket = false;
                    }
                })

                .Enter(STATE_SCREENOFF, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (inPocket) return;

                        if (latestNotifs.length > 0 )
                        {
                            stateSwitch(LED_NOTIF, notifsBuilder);
                        }
                        else if (!state.powerSave || chargeLevel > -1)
                        {
                            stateSwitch(LED_PASSIVE, currentPassiveBuilder);
                        }
                        else
                            _blankLights();
                    }
                })

                .Transition(STATE_SCREENOFF, EVENT_POCKET_MODE, STATE_POCKET_JUNCTION)
                .Transition(STATE_POCKET_JUNCTION, EVENT_SCREENOFF, STATE_SCREENOFF)

                .Enter(STATE_POCKET_JUNCTION, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        inPocket = true;
                        _blankLights();
                        sm.Event(STATE_SCREENOFF);
                    }
                })

                .FanIn(new int[] { STATE_SCREENOFF, STATE_SCREENON }, EVENT_RING, STATE_RINGING)
                .Transition(STATE_RINGING, EVENT_STOP_RING, STATE_RESTORE_JUNCTION)

                .Enter(STATE_RINGING, new Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (arg != null)
                            ringColor = (Integer)arg;
                        stateSwitch(LED_RING, ringBuilder);
                    }
                })

                .FanOut(STATE_RESTORE_JUNCTION,new int[][] {
                        {EVENT_SCREENON,STATE_SCREENON},
                        {EVENT_SCREENOFF,STATE_SCREENOFF}})
                .Enter(STATE_RESTORE_JUNCTION, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        sm.Event(pm.isInteractive() ? EVENT_SCREENON : EVENT_SCREENOFF);
                    }
                });
        
    }
}
