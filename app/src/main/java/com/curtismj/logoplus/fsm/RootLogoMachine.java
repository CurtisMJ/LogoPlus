package com.curtismj.logoplus.fsm;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.PowerManager;
import android.util.Log;

import com.curtismj.logoplus.BuildConfig;
import com.curtismj.logoplus.RootDeniedException;
import com.curtismj.logoplus.persist.UIState;
import com.curtismj.logoplus.visualizer.AudioVisualizer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.stream.Stream;

import androidx.annotation.NonNull;
import eu.chainfire.libsuperuser.Shell;

public class RootLogoMachine extends BaseLogoMachine {

    private  boolean RootAvail = false, daemonStarted = false;
    private Shell.Interactive rootSession;
    private String fadeoutBin, streamBin;
    private PowerManager.WakeLock handlerLock;
    private int streamDaemonPid;
    private LocalSocket streamSocket;
    private OutputStream daemonStream;
    private AudioVisualizer visualizer;

    @Override
    public void cleanup() {
        super.cleanup();
        stopStreamDaemon();
        if (RootAvail) rootSession.close();
    }

    @Override
    protected void startVisualize() {
        if (daemonStream == null)
        {
            startStreamDaemon();
        }
    }

    @Override
    protected void stopVisualize() {
        stopStreamDaemon();
    }

    @Override
    protected void blankLights()
    {
        handlerLock.acquire(10000);
        stopStreamDaemon();
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

        myOutput = new FileOutputStream(streamBin);
        myInput = context.getAssets().open("stream");
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }
        myInput.close();
        myOutput.flush();
        myOutput.close();
    }

    private boolean startStreamDaemon()
    {
        Log.d("debug", "start stream daemon");
        stopStreamDaemon();
        rootSession.waitForIdle();
        daemonStarted = false;
        rootSession.addCommand(streamBin, 0, new Shell.OnCommandLineListener() {
            @Override
            public void onCommandResult(int commandCode, int exitCode) {
                daemonStarted = (exitCode == 0);
            }

            @Override
            public void onSTDOUT(String line) {
                try {
                    streamDaemonPid = Integer.parseInt(line);
                }
                catch (NumberFormatException e)
                {
                    Log.d("debug", "warning, non pid line received which was not expected");
                }
            }

            @Override
            public void onSTDERR(@NonNull String line) {

            }
        });
        rootSession.waitForIdle();
        if (!daemonStarted)
        {
            return false;
        }
        rootSession.addCommand(new String[]{
                "echo \"" + state.brightness + "\" > /sys/class/leds/lp5523:channel0/device/master_fader1"
        });
        rootSession.waitForIdle();
        streamSocket = new LocalSocket();
        try {
            streamSocket.connect(new LocalSocketAddress("14773833519149dc957ac9606d7f369f"));
            daemonStream = streamSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        visualizer = new AudioVisualizer(0, daemonStream);

        return true;
    }

    private void stopStreamDaemon()
    {
        Log.d("debug", "stop stream daemon");
        if (visualizer != null)
        {
            visualizer.stop();
            visualizer = null;
        }

        if (daemonStream != null)
        {
            try {
                daemonStream.write(new byte[] { 1 } );
                daemonStream.close();
                streamSocket = null;
                daemonStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
                        Log.d("debug", "Shell open result: success " + success + " reason:" + reason);
                        RootAvail = success;
                    }
                });

        Log.d("debug", "wait for root shell");
        rootSession.waitForIdle();

        if (RootAvail) {
            fadeoutBin = context.getFilesDir() + "/fadeout";
            streamBin = context.getFilesDir() + "/logo_plus_stream_daemon";
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
                    "chmod +x " + streamBin,
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
            throw new RootDeniedException("0");
        }
    }
}
