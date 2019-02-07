package com.curtismj.logoplus.fsm;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.PowerManager;
import android.util.Log;

import com.curtismj.logoplus.AudioVisualizer;
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
    private String streamBin;
    private PowerManager pm;
    private PowerManager.WakeLock handlerLock;
    private  Context context;
    private int shellExitCode;

    private LocalSocket streamSocket;
    private OutputStream daemonStream;
    private AudioVisualizer vis;

    private  boolean daemonStarted;

    private Integer streamDaemonPid;

    @Override
    public void cleanup() {
        super.cleanup();

            stopStreamDaemon();
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
            public void onLine(String line) {
                try {
                    streamDaemonPid = Integer.parseInt(line);
                }
                catch (NumberFormatException e)
                {
                    Log.d("debug", "warning, non pid line received which was not expected");
                }
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
        return true;
    }

    private void stopStreamDaemon()
    {
        Log.d("debug", "stop stream daemon");
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

    @Override
    protected void runProgram(String[] program)
    {
        handlerLock.acquire(10000);
        rootSession.waitForIdle();
        rootSession.addCommand(new String[]{
                fadeoutBin,
                "echo \"" + program[3] + "\" > /sys/class/leds/lp5523:channel0/device/memory",
                "echo \"" + program[0] + "\" > /sys/class/leds/lp5523:channel0/device/prog_1_start",
                "echo \"" + program[1] + "\" > /sys/class/leds/lp5523:channel0/device/prog_2_start",
                "echo \"" + program[2] + "\" > /sys/class/leds/lp5523:channel0/device/prog_3_start",
                "echo \"1\" > /sys/class/leds/lp5523:channel0/device/run_engine",
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

    private void appendFSM()
    {
        this
                .Transition(STATE_SCREENON, EVENT_ENTER_VISUALIZER, STATE_VISUALIZER)
                .Transition(STATE_VISUALIZER, EVENT_EXIT_VISUALIZER, STATE_VISUALIZER_JUNCTION)

                .Transition(STATE_VISUALIZER_JUNCTION, EVENT_SCREENON, STATE_SCREENON)
                .Transition(STATE_VISUALIZER_JUNCTION, EVENT_SCREENOFF, STATE_SCREENOFF)

                .Enter(STATE_VISUALIZER, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (LEDState != LED_VIS) {
                            LEDState = LED_VIS;
                            if (!startStreamDaemon())
                                sm.Event(EVENT_EXIT_VISUALIZER);
                            else
                                vis = new AudioVisualizer(0, daemonStream);
                        }
                    }
                })
                .Exit(STATE_VISUALIZER, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        if (LEDState == LED_VIS) {
                            if (vis != null)
                            {
                                vis.stop();                     
                                vis = null;
                            }
                            stopStreamDaemon();
                        }
                        if (LEDState != LED_BLANK)
                        {
                            LEDState = LED_BLANK;
                            blankLights();
                        }
                    }
                })
                .Enter(STATE_VISUALIZER_JUNCTION, new StateMachine.Callback() {
                    @Override
                    public void run(StateMachine sm, int otherState, Object arg) {
                        sm.Event(pm.isInteractive() ? EVENT_SCREENON : EVENT_SCREENOFF);
                    }
                });
    }

    public RootLogoMachine(UIState initial, Context _context) throws IOException, RootDeniedException {
        super(initial);

        context = _context;
        eu.chainfire.libsuperuser.Debug.setDebug(true);
        rootSession = new Shell.Builder().
                setAutoHandler(false).
                setShell("/garden/xbin_bind/su").
                setWantSTDERR(true).
                setWatchdogTimeout(5).
                setMinimalLogging(false).
                open(new Shell.OnCommandResultListener() {

                    // Callback to report whether the shell was successfully started up
                    @Override
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                        Log.d("debug", "Shell result: " + commandCode + "," + exitCode);
                        RootAvail = ((shellExitCode = exitCode) == Shell.OnCommandResultListener.SHELL_RUNNING);
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

            pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            handlerLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":ServiceWorkerLock");

            handlerLock.acquire(10000);
            Log.d("debug", "got root");
            rootSession.addCommand(new String[]{
                    "chmod +x " + fadeoutBin,
                    "chmod +x " + streamBin,
                    "echo 111111111 > /sys/class/leds/lp5523:channel0/device/master_fader_leds",
                    "echo 0 > /sys/class/leds/lp5523:channel0/device/master_fader1",
                    fadeoutBin
            });
            rootSession.waitForIdle();
            handlerLock.release();
            LEDState = LED_BLANK;

            appendFSM();
            StartAt(pm.isInteractive() ? BaseLogoMachine.STATE_SCREENON :  BaseLogoMachine.STATE_SCREENOFF);
        }
        else
        {
            cleanup();
            throw new RootDeniedException(String.valueOf(shellExitCode));
        }
    }
}
