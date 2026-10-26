package profotoce59.terrain;

public class SurfaceHeightEstimator {
    private final long worldSeed;
    private final NormalNoise shiftNoise;
    private final NormalNoise continentalnessNoise;
    private final NormalNoise erosionNoise;
    private final NormalNoise weirdnessNoise;

    private final CubicSpline offsetSpline;
    private final CubicSpline factorSpline;

    public SurfaceHeightEstimator(long worldSeed) {
        this.worldSeed = worldSeed;
        Xoroshiro128 xr = Xoroshiro128.createFromWorldSeed(worldSeed);
        long xlo = xr.nextLong();
        long xhi = xr.nextLong();

        // 1. Shift: md5 "minecraft:offset", firstOctave = -3, amp = {1, 1, 1, 0}
        long[] md5Shift = NormalNoise.hashOf("minecraft:offset");
        Xoroshiro128 shiftRng = new Xoroshiro128(xlo ^ md5Shift[0], xhi ^ md5Shift[1]);
        this.shiftNoise = new NormalNoise(shiftRng, -3, new double[]{1, 1, 1, 0});

        // 2. Continentalness: md5 "minecraft:continentalness", firstOctave = -9, amp = {1, 1, 2, 2, 2, 1, 1, 1, 1}
        long[] md5Cont = NormalNoise.hashOf("minecraft:continentalness");
        Xoroshiro128 contRng = new Xoroshiro128(xlo ^ md5Cont[0], xhi ^ md5Cont[1]);
        this.continentalnessNoise = new NormalNoise(contRng, -9, new double[]{1, 1, 2, 2, 2, 1, 1, 1, 1});

        // 3. Erosion: md5 "minecraft:erosion", firstOctave = -9, amp = {1, 1, 0, 1, 1}
        long[] md5Erosion = NormalNoise.hashOf("minecraft:erosion");
        Xoroshiro128 erosionRng = new Xoroshiro128(xlo ^ md5Erosion[0], xhi ^ md5Erosion[1]);
        this.erosionNoise = new NormalNoise(erosionRng, -9, new double[]{1, 1, 0, 1, 1});

        // 4. Weirdness (Ridge): md5 "minecraft:ridge", firstOctave = -7, amp = {1, 2, 1, 0, 0, 0}
        long[] md5Ridge = NormalNoise.hashOf("minecraft:ridge");
        Xoroshiro128 ridgeRng = new Xoroshiro128(xlo ^ md5Ridge[0], xhi ^ md5Ridge[1]);
        this.weirdnessNoise = new NormalNoise(ridgeRng, -7, new double[]{1, 2, 1, 0, 0, 0});

        this.offsetSpline = TerrainProvider.overworldOffset();
        this.factorSpline = TerrainProvider.overworldFactor();
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667f) - 0.33333334f) * 3.0f;
    }

    public double getOffset(int x, int z) {
        double px = (double) x * 0.25 + shiftNoise.getValue((double) x * 0.25, 0.0, (double) z * 0.25) * 4.0;
        double pz = (double) z * 0.25 + shiftNoise.getValue((double) z * 0.25, (double) x * 0.25, 0.0) * 4.0;

        float c = (float) continentalnessNoise.getValue(px, 0.0, pz);
        float e = (float) erosionNoise.getValue(px, 0.0, pz);
        float w = (float) weirdnessNoise.getValue(px, 0.0, pz);
        float pv = peaksAndValleys(w);

        float[] params = new float[]{c, e, pv, w};
        return offsetSpline.apply(params) - 0.50375;
    }

    public long getWorldSeed() {
        return this.worldSeed;
    }

    public int getHeight(int x, int z) {
        return getPreliminarySurfaceLevel(x, z);
    }

    public int getSurfaceHeight(int x, int z) {
        double off = getOffset(x, z);
        return Math.max(-64, (int) Math.round(128.0 * (1.0 + off)));
    }

    public int getPreliminarySurfaceLevel(int x, int z) {
        double px = (double) x * 0.25 + shiftNoise.getValue((double) x * 0.25, 0.0, (double) z * 0.25) * 4.0;
        double pz = (double) z * 0.25 + shiftNoise.getValue((double) z * 0.25, (double) x * 0.25, 0.0) * 4.0;

        float c = (float) continentalnessNoise.getValue(px, 0.0, pz);
        float e = (float) erosionNoise.getValue(px, 0.0, pz);
        float w = (float) weirdnessNoise.getValue(px, 0.0, pz);
        float pv = peaksAndValleys(w);

        float[] params = new float[]{c, e, pv, w};

        double offset = offsetSpline.apply(params) - 0.50375;
        double factor = factorSpline.apply(params);

        double upperBound = clamp(remap(0.2734375 / factor - offset, 1.5, -1.5, -64.0, 320.0), -40.0, 320.0);

        int lowerBound = -64;
        int cellHeight = 8; // 2 << 2 = 8
        int upperCell = (int) Math.floor(upperBound / (double) cellHeight) * cellHeight;
        if (upperCell <= lowerBound) {
            return lowerBound;
        }

        for (int y = upperCell; y >= lowerBound; y -= cellHeight) {
            double depth = offset + yClampedGradient(y, -64, 320, 1.5, -1.5);
            double density = slideOverworld(clamp(noiseGradientDensity(factor, depth) - 0.703125, -64.0, 64.0), y) - 0.390625;
            if (density > 0.0) {
                for (int fineY = y + cellHeight - 1; fineY > y; fineY--) {
                    double fineDepth = offset + yClampedGradient(fineY, -64, 320, 1.5, -1.5);
                    double fineDensity = slideOverworld(clamp(noiseGradientDensity(factor, fineDepth) - 0.703125, -64.0, 64.0), fineY) - 0.390625;
                    if (fineDensity > 0.0) {
                        return fineY;
                    }
                }
                return y;
            }
        }
        return lowerBound;
    }

    private static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
        double slope = (outMax - outMin) / (inMax - inMin);
        double intercept = outMin - inMin * slope;
        return value * slope + intercept;
    }

    private static double yClampedGradient(double y, int fromY, int toY, double fromValue, double toValue) {
        return clampedMap(y, fromY, toY, fromValue, toValue);
    }

    private static double noiseGradientDensity(double factor, double depth) {
        double f = depth * factor;
        return f > 0.0 ? f * 4.0 : f;
    }

    private static double slideOverworld(double densityFunction, int y) {
        return slide(densityFunction, -64, 384, 80, 64, -0.078125, 0, 24, 0.1171875, y);
    }

    private static double slide(double input, int minY, int height, int topStartOffset, int topEndOffset,
                                double topDelta, int bottomStartOffset, int bottomEndOffset, double bottomDelta, int y) {
        double a = clampedMap(y, minY + height - topStartOffset, minY + height - topEndOffset, 1.0, 0.0);
        double b = lerp(a, topDelta, input);
        double c = clampedMap(y, minY + bottomStartOffset, minY + bottomEndOffset, 0.0, 1.0);
        return lerp(c, bottomDelta, b);
    }

    private static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        if (fromMin == fromMax) return toMin;
        double t = (value - fromMin) / (fromMax - fromMin);
        if (t < 0.0) return toMin;
        if (t > 1.0) return toMax;
        return toMin + t * (toMax - toMin);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
