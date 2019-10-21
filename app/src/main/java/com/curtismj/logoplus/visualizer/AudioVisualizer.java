package com.curtismj.logoplus.visualizer;

import android.media.audiofx.Visualizer;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.OutputStream;

public class AudioVisualizer implements Visualizer.OnDataCaptureListener  {
    private static final float MAX_DB_VALUE = 45;

    private static final int F1 = 300;
    private static final int F2 = 3000;

    private static final float[] SOUND_INDEX_COEFFICIENTS = new float[]{
            F1 / 44100f,
            F2 / 44100f
    };

    private float[] mDbsPercentagesConcrete = new float[SOUND_INDEX_COEFFICIENTS.length];

    private static final float FILTRATION_ALPHA = 0.55f;
    private static final float FILTRATION_BETA = 1 - FILTRATION_ALPHA;

    private OutputStream stream;

    private Visualizer mVisualizer;

    private  streamPusher pusher;

    private  final int WINDOW_SIZE = 10;
    private  final float BEAT_THRESHOLD = 0.2f;
    private float[] window;
    private int windowCounter = 0;
    private float[][] beatmaps = {
            {0f,0f,0f,0f,0f,0f,1f,1f,1f},
            {1f,0f,1f,0f,1f,0f,0f,0f,0f},
            {0f,1f,0f,1f,0f,1f,0f,0f,0f},
            {1f,0f,1f,0f,1f,0f,1f,1f,1f},
            {0f,1f,0f,1f,0f,1f,1f,1f,1f},
            {1f,1f,1f,1f,1f,1f,0f,0f,0f}
    };
    private int beat_counter = 0;

    private final class streamPusher extends AsyncTask<Void, Void, Void>
    {


        @Override
        protected Void doInBackground(Void... voids) {
            float impulse;
            while (true)
            {
                window[windowCounter++] = mDbsPercentagesConcrete[0];
                if (windowCounter >= WINDOW_SIZE) windowCounter = 0;
                impulse = Math.max(0f, mDbsPercentagesConcrete[0] - window[windowCounter] );
                if (impulse > BEAT_THRESHOLD)
                {
                    if (++beat_counter >= beatmaps.length) beat_counter = 0;
                }

                byte l1 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][0] * 255f), 255);
                byte l2 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][1] * 255f), 255);
                byte l3 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][2] * 255f), 255);
                byte l4 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][3] * 255f), 255);
                byte l5 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][4] * 255f), 255);
                byte l6 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][5] * 255f), 255);
                byte l7 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][6] * 255f), 255);
                byte l8 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][7] * 255f), 255);
                byte l9 = (byte)Math.min((int)(mDbsPercentagesConcrete[1] * beatmaps[beat_counter][8] * 255f), 255);

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

        window = new float[WINDOW_SIZE];
        for (int x = 0; x < WINDOW_SIZE; x++) window[x] = 0f;

        pusher.execute();
    }

    public void stop()
    {
        if (pusher != null) pusher.cancel(true);
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
        }
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
