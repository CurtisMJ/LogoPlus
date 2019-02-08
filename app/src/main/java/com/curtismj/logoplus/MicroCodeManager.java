package com.curtismj.logoplus;

public class MicroCodeManager {
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

        public byte muxMapStart(int addr)
        {
            return dw(0x9C00 | (addr & 0x7F));
        }

        public byte muxLdEnd(int addr)
        {
            return dw(0x9C80 | (addr & 0x7F));
        }

        public byte muxMapNext()
        {
            return dw(0x9D80);
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

        public byte end()
        {
            return dw(0xC000);
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

    public static  String[] staticProgramBuild(int color)
    {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        byte eng1 = prog.dw(0x1C0);
        byte eng2 = prog.dw(0x15);
        byte eng3 = prog.dw(0x2A);

        int channel;
        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        channel = (color>> 16) & 0xFF;
        prog.ramp(1000f, (byte) 0, channel);
        prog.end();

        byte prog2 = prog.muxMapAddr(eng2);
        prog.setPwm(0);
        channel = (color >> 8) & 0xFF;
        prog.ramp(1000f, (byte) 0, channel);
        prog.end();

        byte prog3 = prog.muxMapAddr(eng3);
        prog.setPwm(0);
        channel = (color) & 0xFF;
        prog.ramp(1000f, (byte) 0, channel);
        prog.end();

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }

    public static  String[] pulseProgramBuild(float msecs, int color)
    {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        byte eng1 = prog.dw(0x1C0);
        byte eng2 = prog.dw(0x15);
        byte eng3 = prog.dw(0x2A);

        int channel;
        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        channel = (color>> 16) & 0xFF;
        prog.trigger(false, 0, 1, 1);
        prog.ramp(msecs, (byte) 0, channel);
        prog.trigger(true, 0, 1, 1);
        prog.trigger(false, 0, 1, 1);
        prog.ramp(msecs, (byte) 1, channel);
        prog.trigger(true, 0, 1, 1);
        prog.branch(0, 2);

        byte prog2 = prog.muxMapAddr(eng2);
        prog.setPwm(0);
        channel = (color >> 8) & 0xFF;
        prog.trigger(true, 1, 0, 0);
        prog.ramp(msecs, (byte) 0, channel);
        prog.trigger(false, 1, 0, 0);
        prog.trigger(true, 1, 0, 0);
        prog.ramp(msecs, (byte) 1, channel);
        prog.trigger(false, 1, 0, 0);
        prog.branch(0, 2);

        byte prog3 = prog.muxMapAddr(eng3);
        prog.setPwm(0);
        channel = (color) & 0xFF;
        prog.trigger(true, 1, 0, 0);
        prog.ramp(msecs, (byte) 0, channel);
        prog.trigger(false, 1, 0, 0);
        prog.trigger(true, 1, 0, 0);
        prog.ramp(msecs, (byte) 1, channel);
        prog.trigger(false, 1, 0, 0);
        prog.branch(0, 2);

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
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
        int max = Math.min(colors.length, 6); // LP5523 only has enough memory for 6 unique colors
        byte eng1 = prog.dw(0x1C0);
        byte eng2 = prog.dw(0x15);
        byte eng3 = prog.dw(0x2A);
        int channel;
        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        for (int i = 0; i < max; i++) {
            channel = (colors[i] >> 16) & 0xFF;

            prog.trigger(false, 0, 1, 1);

            if (i != 0) prog.ramp(200f, (byte)1, 255 );
            prog.ramp(600f, (byte)0, channel );

            prog.trigger(true, 0, 1, 1);
        }
        prog.trigger(false, 0, 1, 1);
        prog.ramp(200f, (byte) 1, 255);
        byte delay = prog.wait(400f, 1);
        prog.branch(4, delay - prog1);
        prog.branch(0, 2);

        byte prog2 = prog.muxMapAddr(eng2);
        prog.setPwm(0);
        for (int i = 0; i < max; i++) {
            channel = (colors[i] >> 8) & 0xFF;

            prog.trigger(true, 1, 0, 0);

            if (i != 0) prog.ramp(200f, (byte)1, 255 );
            prog.ramp(600f, (byte)0, channel );

            prog.trigger(false, 1, 0, 0);
        }
        prog.trigger(true, 1, 0, 0);
        prog.ramp(200f, (byte) 1, 255);
        prog.branch(0, 2);

        byte prog3 = prog.muxMapAddr(eng3);
        prog.setPwm(0);

        for (int i = 0; i < max; i++) {
            channel = (colors[i]) & 0xFF;

            prog.trigger(true, 1, 0, 0);

            if (i != 0) prog.ramp(200f, (byte)1, 255 );
            prog.ramp(600f, (byte)0, channel );

            prog.trigger(false, 1, 0, 0);
        }
        prog.trigger(true, 1, 0, 0);
        prog.ramp(200f, (byte) 1, 255);
        prog.branch(0, 2);

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }

    public static  String[] ringProgramBuild(int color)
    {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);

        /*
        111000000 0x1C0     R
        000010101 0x15        G
        000101010 0x2A        B

        Pad 1
        000000001 0x1
        000000010 0x2
        001000000 0x40

        Pad 2
        000010000 0x10
        000100000 0x20
        100000000 0x100

        Pad 3
        000000100 0x4
        000001000 0x8
        010000000 0x80

         */

        byte eng1 = prog.dw(0x1FF);

        // Green
        byte eng1_pad1 = prog.dw(0x1);
        byte eng1_pad2 = prog.dw(0x10);
        byte eng1_pad3 = prog.dw(0x4);

        // Blue
        byte eng2_pad1 = prog.dw(0x2);
        byte eng2_pad2 = prog.dw(0x20);
        byte eng2_pad3 = prog.dw(0x8);

        // Red
        byte eng3_pad1 = prog.dw(0x40);
        byte eng3_pad2 = prog.dw(0x100);
        byte eng3_pad3 = prog.dw(0x80);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color) & 0xFF;

        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        prog.muxMapStart(eng1_pad1);
        prog.muxLdEnd(eng1_pad3);
        prog.trigger(false, 0, 1, 1);

        byte upRamp1 = prog.ramp(100f, (byte)0,  g);
        prog.muxMapNext();
        prog.branch(3, upRamp1 - prog1);
        prog.trigger(true, 0, 1, 1);
        prog.trigger(false, 0, 1, 1);
        prog.muxMapNext();
        byte dwnRamp1 = prog.ramp(100f, (byte)1,  255);
        prog.muxMapNext();
        prog.branch(3, dwnRamp1 - prog1);
        prog.branch(0, upRamp1 - prog1);


        byte prog2 = prog.muxMapStart(eng2_pad1);
        prog.muxLdEnd(eng2_pad3);
        prog.trigger(true, 1, 0, 0);

        byte upRamp2 = prog.ramp(100f, (byte)0,  b);
        prog.muxMapNext();
        prog.branch(3, upRamp2 - prog2);
        prog.trigger(false, 1, 0, 0);
        prog.trigger(true, 1, 0, 0);
        prog.muxMapNext();
        byte dwnRamp2 = prog.ramp(100f, (byte)1,  255);
        prog.muxMapNext();
        prog.branch(3, dwnRamp2 - prog2);
        prog.branch(0, upRamp2 - prog2);


        byte prog3 = prog.muxMapStart(eng3_pad1);
        prog.muxLdEnd(eng3_pad3);
        prog.trigger(true, 1, 0, 0);

        byte upRamp3 = prog.ramp(100f, (byte)0,  r);
        prog.muxMapNext();
        prog.branch(3, upRamp3 - prog3);
        prog.trigger(false, 1, 0, 0);
        prog.trigger(true, 1, 0, 0);
        prog.muxMapNext();
        byte dwnRamp3 = prog.ramp(100f, (byte)1,  255);
        prog.muxMapNext();
        prog.branch(3, dwnRamp3 - prog3);
        prog.branch(0, upRamp3 - prog3);

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }
}
