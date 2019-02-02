package com.curtismj.logoplus.fsm;

import androidx.collection.ArrayMap;

public class StateMachine {
    public interface Callback
    {
        void run(StateMachine sm, int otherState);
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
        state = initialState;
        if (enterCallbacks.containsKey(state)) enterCallbacks.get(state).run(this, -1);
        return this;
    }

    public StateMachine Event(int event)
    {
        if (!transitions.containsKey(state)) return this;
        ArrayMap<Integer, Integer> trans = transitions.get(state);
        if (!trans.containsKey(event)) return this;
        int oldState = state;
        state = trans.get(event);
        if (oldState != state)
        {
            if (exitCallbacks.containsKey(oldState)) exitCallbacks.get(oldState).run(this, state);
            if (enterCallbacks.containsKey(state)) enterCallbacks.get(state).run(this, oldState);
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
}
