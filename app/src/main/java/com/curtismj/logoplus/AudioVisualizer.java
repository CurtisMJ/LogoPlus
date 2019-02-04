package com.curtismj.logoplus;

import android.icu.util.Output;
import android.media.audiofx.Visualizer;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.OutputStream;

public class AudioVisualizer implements Visualizer.OnDataCaptureListener  {
    private static final float MAX_DB_VALUE = 45;

    private static final int F1 = 300;
    private static final int F2 = 1000;
    private static final int F3 = 1500;
    private static final int F4 = 1800;
    private static final int F5 = 2000;
    private static final int F6 = 2200;
    private static final int F7 = 2500;
    private static final int F8 = 2800;
    private static final int F9 = 3000;

    private static final float[] SOUND_INDEX_COEFFICIENTS = new float[]{
            F1 / 44100f,
            F2 / 44100f,
            F3  / 44100f,
            F4 / 44100f,
            F5 / 44100f,
            F6  / 44100f,
            F7 / 44100f,
            F8 / 44100f,
            F9  / 44100f
    };

    private float[] mDbsPercentagesConcrete = new float[SOUND_INDEX_COEFFICIENTS.length];

    private static final float FILTRATION_ALPHA = 0.55f;
    private static final float FILTRATION_BETA = 1 - FILTRATION_ALPHA;

    private OutputStream stream;

    private Visualizer mVisualizer;

    private  streamPusher pusher;

    private final class streamPusher extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... voids) {
            while (true)
            {
                byte l1 = (byte)Math.min((int)(mDbsPercentagesConcrete[0] * 255f), 255);
                byte l2 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * 255f), 255);
                byte l3 = (byte)Math.min((int)(mDbsPercentagesConcrete[2] * 255f), 255);
                byte l4 = (byte)Math.min((int)(mDbsPercentagesConcrete[3] * 255f), 255);
                byte l5 = (byte)Math.min((int)(mDbsPercentagesConcrete[4] * 255f), 255);
                byte l6 = (byte)Math.min((int)(mDbsPercentagesConcrete[5] * 255f), 255);
                byte l7 = (byte)Math.min((int)(mDbsPercentagesConcrete[6] * 255f), 255);
                byte l8 = (byte)Math.min((int)(mDbsPercentagesConcrete[7] * 255f), 255);
                byte l9 = (byte)Math.min((int)(mDbsPercentagesConcrete[8] * 255f), 255);

                try {
                    stream.write(new byte[] {0, l1,l2,l3,l4,l5,l6,l7,l8,l9} );
                } catch (IOException e) {
                    return null;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return null;
                }
            }
        }
    }

    public AudioVisualizer(int audioSessionId, OutputStream daemon) {
        mVisualizer = new Visualizer(audioSessionId);
        mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        mVisualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate(), false, true);
        stream = daemon;
        mVisualizer.setEnabled(true);
        pusher = new streamPusher();
        pusher.execute();
    }

    public void stop()
    {
        pusher.cancel(true);
        mVisualizer.setEnabled(false);
        mVisualizer.release();
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {

    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        int dataSize = fft.length / 2 - 1;
        for (int i = 0; i < SOUND_INDEX_COEFFICIENTS.length; i++) {
            int index = (int) (SOUND_INDEX_COEFFICIENTS[i] * dataSize);
            byte real = fft[2 * index];
            byte imag = fft[2 * index + 1];
            long magnitudeSquare = real * real + imag * imag;
            magnitudeSquare = (long) Math.sqrt(magnitudeSquare);
            float dbs = magnitudeToDb(magnitudeSquare);
            float dbPercentage = dbs / MAX_DB_VALUE;
            if (dbPercentage > 1.0f) {
                dbPercentage = 1.0f;
            }
            mDbsPercentagesConcrete[i] = mDbsPercentagesConcrete[i] * FILTRATION_ALPHA + dbPercentage * FILTRATION_BETA;
        }
    }

    private float magnitudeToDb(float squareMag) {
        if (squareMag == 0) {
            return 0;
        }
        return (float) (20 * Math.log10(squareMag));
    }
}
