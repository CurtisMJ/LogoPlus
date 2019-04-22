package com.curtismj.logoplus;

public class MicroCodeManager {
    public static class LP55xProgram {
        public byte currAddr;
        public short[] program;
        public static final int LP5523_MEMORY = 96;

        public LP55xProgram(int memory) {
            currAddr = 0;
            program = new short[memory];
        }

        public byte dw(int bits) {
            program[currAddr] = (short) (bits & 0xFFFF);
            return currAddr++;
        }

        public byte ramp(float msecs, byte dir, int steps) {
            float chan = (float) steps;
            float stepTime0 = Math.round((msecs / 0.488f) / chan);
            float stepTime1 = Math.round((msecs / 15.625f) / chan);
            float time0 = chan * stepTime0 * 0.488f;
            float time1 = chan * stepTime1 * 15.625f;
            boolean preScale = Math.abs(time0 - msecs) > Math.abs(time1 - msecs);
            byte finalStepTime = preScale ? (byte) stepTime1 : (byte) stepTime0;
            if (finalStepTime > 31) {
                preScale = !preScale;
                finalStepTime = preScale ? (byte) stepTime1 : (byte) stepTime0;
            }
            int inst = ((finalStepTime & 0x1F) << 9) | (steps & 0xff);
            if (preScale) inst |= 0x4000;
            if (dir == 1) inst |= 0x100;
            byte ret = dw(inst);
            if (dir == 3) {
                inst |= 0x100;
                dw(inst);
            }
            return ret;
        }

        public byte muxMapAddr(int addr) {
            return dw(0x9F80 | (addr & 0x7F));
        }

        public byte muxMapStart(int addr) {
            return dw(0x9C00 | (addr & 0x7F));
        }

        public byte muxLdEnd(int addr) {
            return dw(0x9C80 | (addr & 0x7F));
        }

        public byte muxMapNext() {
            return dw(0x9D80);
        }

        public byte setPwm(int val) {
            return dw(0x4000 | (val & 0xFF));
        }

        public byte setPwmVar(int var) {
            return dw(0x8460 | (var & 0x3));
        }

        public byte ld(int var, int val) {
            return dw(0x9000 | ((var & 0x3) << 10) | (val & 0xFF));
        }

        public byte add(int var, int val) {
            return dw(0x9100 | ((var & 0x3) << 10) | (val & 0xFF));
        }

        public byte sub(int var, int val) {
            return dw(0x9200 | ((var & 0x3) << 10) | (val & 0xFF));
        }

        public byte je(int skip, int var1, int var2) {
            return dw(0x8E00 | ((skip & 0x1F) << 4) | ((var1 & 0x3) << 2) | (var2 & 0x3));
        }

        public byte trigger(boolean wait, int e1, int e2, int e3) {
            return dw(0xE000 | (((e1 | (e2 << 1) | (e3 << 2)) & 0x3F) << (wait ? 7 : 1)));
        }

        public byte wait(float msecs, int repeat) {
            float steps0 = Math.round(msecs / 0.488f);
            float steps1 = Math.round(msecs / 15.625f);
            float time0 = steps0 * 0.488f;
            float time1 = steps1 * 15.625f;
            boolean preScale = Math.abs(time0 - msecs) > Math.abs(time1 - msecs);
            byte finalStepTime = preScale ? (byte) steps1 : (byte) steps0;
            if (finalStepTime > 31) {
                preScale = !preScale;
                finalStepTime = preScale ? (byte) steps1 : (byte) steps0;
            }
            int inst = (finalStepTime & 0x1F) << 9;
            if (preScale) inst |= 0x4000;
            byte addr = dw(inst);
            repeat--;
            for (int i = 0; i < repeat; i++) {
                dw(inst);
            }
            return addr;
        }

        public byte branch(int loops, int addr) {
            return dw(0xA000 | ((loops & 0x3F) << 7) | (addr & 0x7F));
        }

        public byte end() {
            return dw(0xC000);
        }

        public String dump() {
            StringBuilder build = new StringBuilder(program.length * 4);
            for (int i = 0; i < program.length; i++) {
                build.append(String.format("%04X", program[i]));
            }
            return build.toString();
        }
    }

    public static String[] staticProgramBuild(int color) {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        byte eng1 = prog.dw(0x1C0);
        byte eng2 = prog.dw(0x15);
        byte eng3 = prog.dw(0x2A);

        int channel;
        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        channel = (color >> 16) & 0xFF;
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

    public static String[] pulseProgramBuild(float msecs, int color) {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        byte eng1 = prog.dw(0x1C0);
        byte eng2 = prog.dw(0x15);
        byte eng3 = prog.dw(0x2A);

        int channel;
        byte prog1 = prog.muxMapAddr(eng1);
        prog.setPwm(0);
        channel = (color >> 16) & 0xFF;
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

    public static String[] rainbowProgramBuild(float msecs, boolean pinwheel) {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);
        byte eng1;
        byte eng2;
        byte eng3;

        if (!pinwheel) {
            eng1 = prog.dw(0x1C0);
            eng2 = prog.dw(0x15);
            eng3 = prog.dw(0x2A);
        } else {
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

            if (i != 0) prog.ramp(200f, (byte) 1, 255);
            prog.ramp(600f, (byte) 0, channel);

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

            if (i != 0) prog.ramp(200f, (byte) 1, 255);
            prog.ramp(600f, (byte) 0, channel);

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

            if (i != 0) prog.ramp(200f, (byte) 1, 255);
            prog.ramp(600f, (byte) 0, channel);

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

    public static String[] ringProgramBuild(int color) {
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

        byte upRamp1 = prog.ramp(100f, (byte) 0, g);
        prog.muxMapNext();
        prog.branch(3, upRamp1 - prog1);
        prog.trigger(true, 0, 1, 1);
        prog.trigger(false, 0, 1, 1);
        prog.muxMapNext();
        byte dwnRamp1 = prog.ramp(100f, (byte) 1, 255);
        prog.muxMapNext();
        prog.branch(3, dwnRamp1 - prog1);
        prog.branch(0, upRamp1 - prog1);


        byte prog2 = prog.muxMapStart(eng2_pad1);
        prog.muxLdEnd(eng2_pad3);
        prog.trigger(true, 1, 0, 0);

        byte upRamp2 = prog.ramp(100f, (byte) 0, b);
        prog.muxMapNext();
        prog.branch(3, upRamp2 - prog2);
        prog.trigger(false, 1, 0, 0);
        prog.trigger(true, 1, 0, 0);
        prog.muxMapNext();
        byte dwnRamp2 = prog.ramp(100f, (byte) 1, 255);
        prog.muxMapNext();
        prog.branch(3, dwnRamp2 - prog2);
        prog.branch(0, upRamp2 - prog2);


        byte prog3 = prog.muxMapStart(eng3_pad1);
        prog.muxLdEnd(eng3_pad3);
        prog.trigger(true, 1, 0, 0);

        byte upRamp3 = prog.ramp(100f, (byte) 0, r);
        prog.muxMapNext();
        prog.branch(3, upRamp3 - prog3);
        prog.trigger(false, 1, 0, 0);
        prog.trigger(true, 1, 0, 0);
        prog.muxMapNext();
        byte dwnRamp3 = prog.ramp(100f, (byte) 1, 255);
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

    public static String[] rollProgramBuild() {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);

        byte rpad1 = prog.dw(0x40);
        byte rpad2and3 = prog.dw(0x180);
        byte gpad1 = prog.dw(0x1);
        byte gpad2and3 = prog.dw(0x14);
        byte bpad1 = prog.dw(0x2);
        byte bpad2and3 = prog.dw(0x28);

        byte prog1 = prog.ld(2, 255);
        prog.muxMapAddr(gpad1);
        prog.setPwm(0);
        prog.muxMapAddr(bpad1);
        prog.setPwm(0);
        prog.muxMapAddr(rpad1);
        prog.setPwm(0);
        prog.ramp(1200f, (byte) 0, 255);

        byte p1reset = prog.ld(0, 255);
        prog.ld(1, 0);

        byte p1loop1 = prog.muxMapAddr(rpad1);
        prog.setPwmVar(0);
        prog.muxMapAddr(gpad1);
        prog.setPwmVar(1);
        prog.sub(0, 1);
        prog.add(1, 1);
        prog.je(1, 1, 2);
        prog.branch(0, p1loop1 - prog1);

        byte p1loop2 = prog.muxMapAddr(gpad1);
        prog.setPwmVar(1);
        prog.muxMapAddr(bpad1);
        prog.setPwmVar(0);
        prog.sub(1, 1);
        prog.add(0, 1);
        prog.je(1, 0, 2);
        prog.branch(0, p1loop2 - prog1);

        byte p1loop3 = prog.muxMapAddr(bpad1);
        prog.setPwmVar(0);
        prog.muxMapAddr(rpad1);
        prog.setPwmVar(1);
        prog.sub(0, 1);
        prog.add(1, 1);
        prog.je(1, 1, 2);
        prog.branch(0, p1loop3 - prog1);
        prog.branch(0, p1reset - prog1);



        byte prog2 = prog.muxMapAddr(gpad2and3);
        prog.setPwm(0);
        prog.muxMapAddr(bpad2and3);
        prog.setPwm(0);
        prog.muxMapAddr(rpad2and3);
        prog.setPwm(0);
        prog.ramp(1200f, (byte) 0, 255);
        prog.wait(400f, 2);

        byte p2reset = prog.ld(0, 255);
        prog.ld(1, 0);

        byte p2loop1 = prog.muxMapAddr(rpad2and3);
        prog.setPwmVar(0);
        prog.muxMapAddr(gpad2and3);
        prog.setPwmVar(1);
        prog.sub(0, 1);
        prog.add(1, 1);
        prog.je(1, 1, 2);
        prog.branch(0,  p2loop1 - prog2);

        byte p2loop2 = prog.muxMapAddr(gpad2and3);
        prog.setPwmVar(1);
        prog.muxMapAddr(bpad2and3);
        prog.setPwmVar(0);
        prog.sub(1, 1);
        prog.add(0, 1);
        prog.je(1, 0, 2);
        prog.branch(0,  p2loop2 - prog2);

        byte p2loop3 = prog.muxMapAddr(bpad2and3);
        prog.setPwmVar(0);
        prog.muxMapAddr(rpad2and3);
        prog.setPwmVar(1);
        prog.sub(0, 1);
        prog.add(1, 1);
        prog.je(1, 1, 2);
        prog.branch(0,  p2loop3 - prog2);
        prog.branch(0,  p2reset - prog2);



        byte prog3 = prog.end();


        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }

    public static String[] batteryProgramBuild(float chargeLevel, boolean fadein) {
        LP55xProgram prog = new LP55xProgram(LP55xProgram.LP5523_MEMORY);

        byte red = prog.dw(0x1C0);
        byte green = prog.dw(0x15);
        byte blue = prog.dw(0x2A);

        // Blue
        byte bpad1 = prog.dw(0x2);
        byte bpad2 = prog.dw(0x20);
        byte bpad3 = prog.dw(0x8);

        int r = chargeLevel < 50f ? 255 : (int)((1f - ((chargeLevel - 50f) / 50f)) * 255f);
        int g = chargeLevel < 50f ? (int)((chargeLevel/ 50f) * 255f) : 255;

        byte prog1 = prog.muxMapAddr(blue);
        prog.setPwm(0);
        if (chargeLevel < 98f) {
            prog.muxMapStart(bpad1);
            prog.muxLdEnd(bpad3);
        }
        if (fadein) prog.wait(400f, 2);
        byte upRamp1 = prog.ramp(100f, (byte) 0, 128);
        prog.ramp(1000f, (byte) 1, 128);
        if (chargeLevel < 98f) prog.muxMapNext();
        else prog.wait(400f, 4);
        prog.branch(0, upRamp1 - prog1);


        byte prog2 = prog.muxMapAddr(red);
        if (fadein) {
            prog.setPwm(0);
            prog.ramp(1000f, (byte) 0, r);
        }
        else
            prog.setPwm(r);
        prog.end();

        byte prog3 = prog.muxMapAddr(green);
        if (fadein) {
            prog.setPwm(0);
            prog.ramp(1000f, (byte) 0, g);
        }
        else
            prog.setPwm(g);
        prog.end();

        return new String[]{
                String.format("%02X", prog1),
                String.format("%02X", prog2),
                String.format("%02X", prog3),
                prog.dump()
        };
    }

}