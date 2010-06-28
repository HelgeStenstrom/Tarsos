/**
 */
package be.hogent.tarsos.pitch.pure;


/**
 * Uses YIN and MPM in the background. Only detects Pitch if both pitch trackers
 * agree (within a small error range).
 * @author Joren Six
 */
public final class MetaPitchDetector implements PurePitchDetector {

    /**
     * The YIN pitch tracker.
     */
    private final PurePitchDetector yin;
    /**
     * The MPM pitch tracker.
     */
    private final PurePitchDetector mpm;

    public MetaPitchDetector(final float samplingRate) {
        yin = new Yin(samplingRate, 1024, 512);
        mpm = new McLeodPitchMethod(samplingRate, 1024);
    }

    /*
     * (non-Javadoc)
     * @see be.hogent.tarsos.pitch.pure.PurePitchDetector#getPitch(float[])
     */
    @Override
    public float getPitch(final float[] audioBuffer) {
        final float yinPitch = yin.getPitch(audioBuffer);
        final float mpmPitch = mpm.getPitch(audioBuffer);
        float pitch;
        if (yinPitch == -1 || mpmPitch == -1) {
            pitch = -1;
        } else if (Math.abs(yinPitch - mpmPitch) <= mpmPitch / 150) {
            // pitch within 2 percent => accurate
            pitch = (yinPitch + mpmPitch) / 2;
        } else {
            pitch = -1;
        }
        return pitch;
    }

}