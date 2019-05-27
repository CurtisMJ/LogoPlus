package com.curtismj.logoplus.fsm;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.curtismj.logoplus.BuildConfig;
import com.curtismj.logoplus.RootDeniedException;
import com.curtismj.logoplus.persist.UIState;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class RootLogoMachine extends BaseLogoMachine {

    private  boolean RootAvail = false;
    private Shell.Interactive rootSession;
    private String fadeoutBin;
    private PowerManager.WakeLock handlerLock;
    private int shellExitCode;

    @Override
    public void cleanup() {
        super.cleanup();
        rootSession.close();
    }

    @Override
    protected void blankLights()
    {
        handlerLock.acquire(10000);
        rootSession.waitForIdle();
        rootSession.addCommand(new String[]{
                fadeoutBin
        });
        rootSession.waitForIdle();
        handlerLock.release();
    }

    @Override
    protected void runProgram(String[] program)
    {
        handlerLock.acquire(10000);
        rootSession.waitForIdle();
        if (state.brightness > 0) {
            rootSession.addCommand(new String[]{
                    "echo \"" + program[3] + "\" > /sys/class/leds/lp5523:channel0/device/memory",
                    "echo \"" + program[0] + "\" > /sys/class/leds/lp5523:channel0/device/prog_1_start",
                    "echo \"" + program[1] + "\" > /sys/class/leds/lp5523:channel0/device/prog_2_start",
                    "echo \"" + program[2] + "\" > /sys/class/leds/lp5523:channel0/device/prog_3_start",
                    "echo \"1\" > /sys/class/leds/lp5523:channel0/device/run_engine"
            });
            rootSession.waitForIdle();
        }
        rootSession.addCommand(new String[]{
                "echo \"" + state.brightness + "\" > /sys/class/leds/lp5523:channel0/device/master_fader1"
        });
        rootSession.waitForIdle();
        handlerLock.release();
    }

    private void dumpBinaries() throws IOException {
        OutputStream myOutput = new FileOutputStream(fadeoutBin);
        byte[] buffer = new byte[1024];
        int length;
        InputStream myInput = context.getAssets().open("fadeout");
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }
        myInput.close();
        myOutput.flush();
        myOutput.close();
    }


    public RootLogoMachine(UIState initial, Context _context) throws IOException, RootDeniedException {
        super(_context, initial);

        rootSession = new Shell.Builder().
                setAutoHandler(false).
                useSU().
                setWantSTDERR(true).
                setWatchdogTimeout(5).
                setMinimalLogging(true).
                open(new Shell.OnShellOpenResultListener() {
                    @Override
                    public void onOpenResult(boolean success, int reason)
                    {
                        Log.d("debug", "Shell open result: success" + success + " reason:" + reason);
                        RootAvail = success;
                    }
                });

        Log.d("debug", "wait for root shell");
        rootSession.waitForIdle();

        if (RootAvail) {
            fadeoutBin = context.getFilesDir() + "/fadeout";
            try {
                dumpBinaries();
            }
            catch (IOException e)
            {
                cleanup();
                throw e;
            }

            handlerLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":RootStateMachine");

            handlerLock.acquire(10000);
            Log.d("debug", "got root");
            rootSession.addCommand(new String[]{
                    "chmod +x " + fadeoutBin,
                    "echo 111111111 > /sys/class/leds/lp5523:channel0/device/master_fader_leds",
                    "echo 0 > /sys/class/leds/lp5523:channel0/device/master_fader1"
            });
            rootSession.waitForIdle();
            handlerLock.release();
            LEDState = LED_BLANK;

            StartAt(pm.isInteractive() ? BaseLogoMachine.STATE_SCREENON :  BaseLogoMachine.STATE_SCREENOFF);
        }
        else
        {
            cleanup();
            throw new RootDeniedException(String.valueOf(shellExitCode));
        }
    }
}
