/**
 */
package be.hogent.tarsos.pitch.pure;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Implementation of The McLeod Pitch Method (MPM). It is described in the
 * article <a href="http://miracle.otago.ac.nz/postgrads/tartini/papers/A_Smarter_Way_to_Find_Pitch.pdf"
 * >A Smarter Way to Find Pitch</a>. According to the article:
 * </p>
 * <blockquote> <i>
 * <p>
 * A fast, accurate and robust method for finding the continuous pitch in
 * monophonic musical sounds. [It uses] a special normalized version of the
 * Squared Difference Function (SDF) coupled with a peak picking algorithm.
 * </p>
 * <p>
 * MPM runs in real time with a standard 44.1 kHz sampling rate. It operates
 * without using low-pass filtering so it can work on sound with high harmonic
 * frequencies such as a violin and it can display pitch changes of one cent
 * reliably. MPM works well without any post processing to correct the pitch.
 * </p>
 * </i> </blockquote>
 * <p>
 * For the moment this implementation uses the inefficient way of calculating
 * the pitch. It uses <code>O(Ww)</code> with W the window size in samples and w
 * the desired number of ACF coefficients. The implementation can be optimized
 * to <code>O((W+w)log(W+w))</code> by using an FFT to calculate the AFC.
 * </p>
 * @author Joren Six
 */
public final class McLeodPitchMethod implements PurePitchDetector {

    /**
     * The expected size of an audio buffer (in samples).
     */
    public static final int BUFFER_SIZE = 1024;

    /**
     * Overlap defines how much two audio buffers following each other should
     * overlap (in samples).
     */
    public static final int OVERLAP = 512;

    /**
     * Defines the relative size the chosen peak (pitch) has. 0.93 means: choose
     * the first peak that is higher than 93% of the highest peak detected. 93%
     * is the default value used in the Tartini user interface.
     */
    private static double CUTOFF = 0.93;
    /**
     * For performance reasons, peaks below this cutoff are not even considered.
     */
    private static double SMALL_CUTOFF = 0.5;

    /**
     * The audio sample rate. Most audio has a sample rate of 44.1kHz.
     */
    private final float sampleRate;

    /**
     *Contains a normalized square difference value for each delay (tau).
     */
    private final float[] nsdf;

    /**
     * The x and y coordinate of the top of the curve.
     */
    private float turningPointX, turningPointY;


    /**
     * A list with minimum and maximum values of the nsdf curve.
     */
    private final List<Integer> maxPositions = new ArrayList<Integer>();

    /**
     * A list of estimates of the period of the signal (in samples).
     */
    private final List<Float> periodEstimates = new ArrayList<Float>();

    /**
     * A list of estimates of the amplitudes corresponding with the period
     * estimates.
     */
    private final List<Float> amplitudeEstimates = new ArrayList<Float>();

    /**
     * Initializes the normalized square difference value array and stores the
     * sample rate.
     * @param audioSampleRate
     *            The sample rate of the audio to check.
     */
    public McLeodPitchMethod(final float audioSampleRate) {
        this.sampleRate = audioSampleRate;
        nsdf = new float[BUFFER_SIZE];
    }

    /**
     * Implements the normalized square difference function. See section 4 (and
     * the explanation before) in the MPM article. This calculation can be
     * optimized by using an FFT. The results should remain the same.
     * @param audioBuffer
     *            The buffer with audio information.
     */
    private void normalizedSquareDifference(final float[] audioBuffer) {
        for (int tau = 0; tau < audioBuffer.length; tau++) {
            float acf = 0;
            float m = 0;
            for (int i = 0; i < audioBuffer.length - tau; i++) {
                acf += audioBuffer[i] * audioBuffer[i + tau];
                m += audioBuffer[i] * audioBuffer[i] + audioBuffer[i + tau] * audioBuffer[i + tau];
            }
            nsdf[tau] = 2 * acf / m;
        }
    }


    public float getPitch(final float[] audioBuffer) {
        final float pitch;

        //0. Clear previous results (should be faster than initializing again?)
        maxPositions.clear();
        periodEstimates.clear();
        amplitudeEstimates.clear();

        // 1. Calculate the normalized square difference for each Tau value.
        normalizedSquareDifference(audioBuffer);
        // 2. peak picking time: time to pick some peaks
        peakPicking();

        double highestAmplitude = Double.NEGATIVE_INFINITY;

        for (final Integer tau : maxPositions) {
            if (nsdf[tau] > SMALL_CUTOFF) {
                // calculates turningPointX and Y
                prabolicInterpolation(tau);
                // store the turning points
                amplitudeEstimates.add(turningPointY);
                periodEstimates.add(turningPointX);
                // remember the highest amplitude
                highestAmplitude = Math.max(highestAmplitude, turningPointY);
            }
        }


        if (periodEstimates.isEmpty()) {
            pitch = -1;
        } else {
            // use the overall maximum to calculate a cutoff.
            // The cutoff value is based on the highest value and a relative
            // threshold.
            final double cutoff = CUTOFF * highestAmplitude;

            // find first period above or equal to cutoff
            int periodIndex = 0;
            for (int i = 0; i < amplitudeEstimates.size(); i++) {
                if (amplitudeEstimates.get(i) >= cutoff) {
                    periodIndex = i;
                    break;
                }
            }

            final double period = periodEstimates.get(periodIndex);
            pitch = (float) (sampleRate / period);
        }

        return pitch;
    }

    /**
     * <p>
     * Finds the x value corresponding with the peak of a parabola.
     * </p>
     * <p>
     * a,b,c are three samples that follow each other. E.g. a is at 511, b at
     * 512 and c at 513; f(a), f(b) and f(c) are the normalized square
     * difference values for those samples; x is the peak of the parabola and is
     * what we are looking for. Because the samples follow each other
     * <code>b - a = 1</code> the formula for <a
     * href="http://fizyka.umk.pl/nrbook/c10-2.pdf">parabolic interpolation</a>
     * can be simplified a lot.
     * </p>
     * <p>
     * The following ASCII ART shows it a bit more clear, imagine this to be a
     * bit more curvaceous.
     * </p>
     * <pre>
     *       y
     *       ^
     *       |
     * f(x)  |------ ^
     * f(b)  |     / |\
     * f(a)  |    /  | \
     *       |   /   |  \
     *       |  /    |   \
     * f(c)  | /     |    \
     *       |_____________________> x
     *            a  x b  c
     * </pre>
     * @param tau
     *            The b value in the drawing is the tau value.
     */
    private void prabolicInterpolation(final int tau) {
        final float fa = nsdf[tau - 1];
        final float fb = nsdf[tau];
        final float fc = nsdf[tau + 1];
        final float b = tau;
        final float bottom = fc + fa - 2 * fb;
        if (bottom == 0.0) {
            turningPointX = b;
            turningPointY = fb;
        } else {
            final float delta = fa - fc;
            turningPointX = b + delta / (2 * bottom);
            turningPointY = fb - delta * delta / (8 * bottom);
        }
    }



    /**
     * <p>
     * Implementation based on the GPL'ED code of <a
     * href="http://tartini.net">Tartini</a> This code can be found in the file
     * <code>general/mytransforms.cpp</code>.
     * </p>
     * <p>
     * Finds the highest value between each pair of positive zero crossings.
     * Including the highest value between the last positive zero crossing and
     * the end (if any). Ignoring the first maximum (which is at zero). In this
     * diagram the desired values are marked with a +
     * </p>
     * 
     * <pre>
     *  f(x)
     *   ^
     *   |
     *  1|               +
     *   | \      +     /\      +     /\
     *  0| _\____/\____/__\/\__/\____/_______> x
     *   |   \  /  \  /      \/  \  /
     * -1|    \/    \/            \/
     *   |
     * </pre>
     * 
     * @param nsdf
     *            The array to look for maximum values in. It should contain
     *            values between -1 and 1
     * @author Phillip McLeod
     */
    private void peakPicking() {

        int pos = 0;

        int curMaxPos = 0;

        // find the first negative zero crossing
        while (pos < (nsdf.length - 1) / 3 && nsdf[pos] > 0) {
            pos++;
        }

        // loop over all the values below zero
        while (pos < nsdf.length - 1 && nsdf[pos] <= 0.0) {
            pos++;
        }

        // can happen if output[0] is NAN
        if (pos == 0) {
            pos = 1;
        }

        while (pos < nsdf.length - 1) {
            assert nsdf[pos] >= 0;
            if (nsdf[pos] > nsdf[pos - 1] && nsdf[pos] >= nsdf[pos + 1]) {
                if (curMaxPos == 0) {
                    // the first max (between zero crossings)
                    curMaxPos = pos;
                } else if (nsdf[pos] > nsdf[curMaxPos]) {
                    // a higher max (between the zero crossings)
                    curMaxPos = pos;
                }
            }
            pos++;
            // a negative zero crossing
            if (pos < nsdf.length - 1 && nsdf[pos] <= 0) {
                // if there was a maximum add it to the list of maxima
                if (curMaxPos > 0) {
                    maxPositions.add(curMaxPos);
                    curMaxPos = 0; // clear the maximum position, so we start
                    // looking for a new ones
                }
                while (pos < nsdf.length - 1 && nsdf[pos] <= 0.0f) {
                    pos++; // loop over all the values below zero
                }
            }
        }
        if (curMaxPos > 0) { // if there was a maximum in the last part
            maxPositions.add(curMaxPos); // add it to the vector of maxima
        }
    }
}