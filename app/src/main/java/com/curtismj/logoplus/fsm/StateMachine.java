package com.curtismj.logoplus.fsm;

import android.util.Log;

import androidx.collection.ArrayMap;

public class StateMachine {
    public interface Callback
    {
        void run(StateMachine sm, int otherState, Object arg);
    }

    private int state;
    // <StateFrom, <Event, StateTo>>
    private ArrayMap<Integer, ArrayMap<Integer, Integer>> transitions;
    private ArrayMap<Integer, Callback> exitCallbacks;
    private ArrayMap<Integer, Callback> enterCallbacks;

    public  StateMachine()
    {
        transitions = new ArrayMap<>();
        exitCallbacks = new ArrayMap<>();
        enterCallbacks = new ArrayMap<>();
    }

    public StateMachine StartAt(int initialState)
    {
        Log.d("debug", "SM: Start at " + initialState);
        state = initialState;
        if (enterCallbacks.containsKey(state)) enterCallbacks.get(state).run(this, -1, null);
        return this;
    }

    public StateMachine Event(int event)
    {
        return Event(event, null);
    }

    public StateMachine Event(int event, Object arg)
    {
        Log.d("debug", "SM: Event " + event);
        if (!transitions.containsKey(state)) return this;
        ArrayMap<Integer, Integer> trans = transitions.get(state);
        if (!trans.containsKey(event)) return this;
        int oldState = state;
        state = trans.get(event);
        if (oldState != state)
        {
            Log.d("debug", "SM: State change  " + oldState + " -> " + state);
            Log.d("debug", "SM: Exit state " + oldState);
            if (exitCallbacks.containsKey(oldState)) exitCallbacks.get(oldState).run(this, state, arg);
            Log.d("debug", "SM: Enter state " + state);
            if (enterCallbacks.containsKey(state)) enterCallbacks.get(state).run(this, oldState, arg);
        }
        return this;
    }

    public StateMachine Transition(int oldState, int event, int newState)
    {
        if (!transitions.containsKey(oldState)) transitions.put(oldState, new ArrayMap<Integer, Integer>());
        transitions.get(oldState).put(event, newState);
        return this;
    }

    public StateMachine Exit(int state, Callback callback)
    {
        exitCallbacks.put(state, callback);
        return this;
    }
    public StateMachine Enter(int state, Callback callback)
    {
        enterCallbacks.put(state, callback);
        return this;
    }

    public StateMachine FanOut(int root, int[][] fanPairs)
    {
        // Allows defining multiple events to fan out to multiple states
        // from a single root state

        for (int i = 0; i < fanPairs.length; i++)
        {
            Transition(root, fanPairs[i][0], fanPairs[i][1]);
        }

        return this;
    }

    public StateMachine FanIn(int[] states, int event, int convergeState)
    {
        // Allows defining multiple events to converge onto a single state
        // in response to a single event

        for (int i = 0; i < states.length; i++)
        {
            Transition(states[i], event, convergeState);
        }

        return this;
    }

    public StateMachine ReverseFanIn(int[][] fanout, int oldState)
    {
        for (int i = 0; i < fanout.length; i++)
        {
            if (fanout[i][1] == oldState)
            {
                return Event(fanout[i][0]);
            }
        }
        return this;
    }

    public void cleanup()
    {

    }
}
