package com.curtismj.logoplus;

import android.os.PowerManager;

class MicroCodeManager {
    public static class LP55xProgram
    {
        public byte currAddr;
        public short[] program;
        public static final int LP5523_MEMORY = 96;

        public LP55xProgram(int memory)
        {
            currAddr = 0;
            program = new short[memory];
        }

        public byte dw(int bits)
        {
            program[currAddr] = (short)(bits & 0xFFFF);
            return  currAddr++;
        }

        public byte ramp(float msecs, byte dir, int steps)
        {
            float chan = (float)steps;
            float stepTime0 = Math.round((msecs / 0.488f) / chan);
            float stepTime1 = Math.round( (msecs / 15.625f) / chan);
            float time0 = chan * stepTime0 * 0.488f;
            float time1 = chan * stepTime1 * 15.625f;
            boolean preScale = Math.abs(time0 - msecs) > Math.abs(time1 - msecs);
            byte finalStepTime =  preScale ? (byte)stepTime1 : (byte)stepTime0;
            if (finalStepTime > 31)
            {
                preScale  = !preScale;
                finalStepTime =  preScale ? (byte)stepTime1 : (byte)stepTime0;
            }
            int inst = ((finalStepTime & 0x1F) << 9) | (steps & 0xff);
            if (preScale)  inst |=  0x4000;
            if (dir == 1) inst |= 0x100;
            byte ret =  dw(inst);
            if (dir == 3)
            {
                inst |= 0x100;
                dw(inst);
            }
            return ret;
        }

        public byte muxMapAddr(int addr)
        {
            return dw(0x9F80 | (addr & 0x7F));
        }

        public byte setPwm(int val)
        {
            return dw(0x4000 | (val & 0xFF));
        }

        public byte trigger(boolean wait, int e1, int e2, int e3)
        {
            return dw(0xE000 | (((e1 | (e2 << 1) | (e3 << 2)) & 0x3F) << (wait ? 7 : 1)));
        }

        public byte wait(float msecs, int repeat)
        {
            float steps0 = Math.round(msecs / 0.488f);
            float steps1 = Math.round(msecs / 15.625f);
            float time0 = steps0 * 0.488f;
            float time1 = steps1 * 15.625f;
            boolean preScale = Math.abs(time0 - msecs) > Math.abs(time1 - msecs);
            byte finalStepTime =  preScale ? (byte)steps1 : (byte)steps0;
            if (finalStepTime > 31)
            {
                preScale  = !preScale;
                finalStepTime =  preScale ? (byte)steps1 : (byte)steps0;
            }
            int inst = (finalStepTime & 0x1F) << 9;
            if (preScale)  inst |=  0x4000;
            byte addr = dw(inst);
            repeat--;
            for (int i = 0; i < repeat; i++)
            {
                dw(inst);
            }
            return addr;
        }

        public byte branch(int loops, int addr)
        {
            return dw(0xA000 | ((loops & 0x3F) << 7) | (addr & 0x7F));
        }

        public  String dump()
        {
            String fin = "";
            for (int i = 0; i < program.length; i++)
            {
                fin += String.format("%04X", program[i]);
            }
            return  fin;
        }
    }

    public static String[] rainbowProgramBuild(float msecs, boolean pinwheel)
    {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        byte eng1;
        byte eng2;
        byte eng3;

        if (!pinwheel)
        {
            eng1 = prog.dw(0x1C0);
            eng2 = prog.dw(0x15);
            eng3 = prog.dw(0x2A);
        }
        else
        {
            eng1 = prog.dw(0x58);
            eng2 = prog.dw(0xA1);
            eng3 = prog.dw(0x106);
        }

        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        prog.ramp(msecs, (byte) 0, 255);
        prog.ramp(msecs, (byte) 1, 255);
        prog.ramp(msecs, (byte) 1, 255);
        prog.branch(0, 2);

        byte prog2 = prog.muxMapAddr(eng2);
        prog.setPwm(0);
        prog.ramp(msecs, (byte) 1, 255);
        prog.ramp(msecs, (byte) 0, 255);
        prog.ramp(msecs, (byte) 1, 255);
        prog.branch(0, 2);

        byte prog3 = prog.muxMapAddr(eng3);
        prog.setPwm(0);
        prog.ramp(msecs, (byte) 1, 255);
        prog.ramp(msecs, (byte) 1, 255);
        prog.ramp(msecs, (byte) 0, 255);
        prog.branch(0, 2);

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }

    public static String[] notifyProgramBuild(int[] colors) {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        int max = Math.min(colors.length, 4); // LP5523 only has enough memory for 4 unique colors
        byte eng1 = prog.dw(0x1C0);
        byte eng2 = prog.dw(0x15);
        byte eng3 = prog.dw(0x2A);
        int channel;
        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        for (int i = 0; i < max; i++) {
            channel = (colors[i] >> 16) & 0xFF;

            prog.trigger(false, 0, 1, 1);

            if (channel > 0) prog.ramp(600f, (byte) 0, channel);
            else prog.wait(300f, 2);

            prog.trigger(true, 0, 1, 1);
            prog.trigger(false, 0, 1, 1);

            if (channel > 0) prog.ramp(600f, (byte) 1, channel);
            else prog.wait(300f, 2);
            prog.trigger(true, 0, 1, 1);
        }
        byte delay = prog.wait(400f, 1);
        prog.branch(3, delay - prog1);
        prog.branch(0, 2);

        byte prog2 = prog.muxMapAddr(eng2);
        prog.setPwm(0);
        for (int i = 0; i < max; i++) {
            channel = (colors[i] >> 8) & 0xFF;
            prog.trigger(true, 1, 0, 0);

            if (channel > 0) prog.ramp(600f, (byte) 0, channel);
            else prog.wait(300f, 2);

            prog.trigger(false, 1, 0, 0);
            prog.trigger(true, 1, 0, 0);

            if (channel > 0) prog.ramp(600f, (byte) 1, channel);
            else prog.wait(300f, 2);
            prog.trigger(false, 1, 0, 0);
        }
        prog.branch(0, 2);

        byte prog3 = prog.muxMapAddr(eng3);
        prog.setPwm(0);
        for (int i = 0; i < max; i++) {
            channel = (colors[i]) & 0xFF;
            prog.trigger(true, 1, 0, 0);

            if (channel > 0) prog.ramp(600f, (byte) 0, channel);
            else prog.wait(300f, 2);

            prog.trigger(false, 1, 0, 0);
            prog.trigger(true, 1, 0, 0);

            if (channel > 0) prog.ramp(600f, (byte) 1, channel);
            else prog.wait(300f, 2);
            prog.trigger(false, 1, 0, 0);
        }
        prog.branch(0, 2);

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }
}