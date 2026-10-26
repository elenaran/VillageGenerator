package profotoce59.terrain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227;
    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;

    public NormalNoise(Xoroshiro128 rng, int firstOctave, double[] amplitudes) {
        long firstLo = rng.nextLong();
        long firstHi = rng.nextLong();
        long secondLo = rng.nextLong();
        long secondHi = rng.nextLong();
        this.first = new PerlinNoise(firstLo, firstHi, firstOctave, amplitudes);
        this.second = new PerlinNoise(secondLo, secondHi, firstOctave, amplitudes);

        int minOct = Integer.MAX_VALUE;
        int maxOct = Integer.MIN_VALUE;
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                minOct = Math.min(minOct, i);
                maxOct = Math.max(maxOct, i);
            }
        }
        this.valueFactor = 0.16666666666666666 / (0.1 * (1.0 + 1.0 / (double) (maxOct - minOct + 1)));
    }

    public double getValue(double x, double y, double z) {
        double x2 = x * INPUT_FACTOR;
        double y2 = y * INPUT_FACTOR;
        double z2 = z * INPUT_FACTOR;
        return (this.first.getValue(x, y, z) + this.second.getValue(x2, y2, z2)) * this.valueFactor;
    }

    public static class PerlinNoise {
        private static final double ROUND_OFF = 3.3554432E7;
        private final ImprovedNoise[] noiseLevels;
        private final double[] amplitudes;
        private final double lowestFreqInputFactor;
        private final double lowestFreqValueFactor;

        public PerlinNoise(long octLo, long octHi, int firstOctave, double[] amplitudes) {
            this.amplitudes = amplitudes;
            int n = amplitudes.length;
            this.noiseLevels = new ImprovedNoise[n];

            for (int i = 0; i < n; i++) {
                if (amplitudes[i] != 0.0) {
                    int octave = firstOctave + i;
                    long[] md5 = hashOf("octave_" + octave);
                    Xoroshiro128 octaveRng = new Xoroshiro128(octLo ^ md5[0], octHi ^ md5[1]);
                    this.noiseLevels[i] = new ImprovedNoise(octaveRng);
                }
            }

            this.lowestFreqInputFactor = Math.pow(2.0, firstOctave);
            this.lowestFreqValueFactor = Math.pow(2.0, n - 1) / (Math.pow(2.0, n) - 1.0);
        }

        public double getValue(double x, double y, double z) {
            double result = 0.0;
            double inFactor = this.lowestFreqInputFactor;
            double valFactor = this.lowestFreqValueFactor;

            for (int i = 0; i < this.noiseLevels.length; i++) {
                ImprovedNoise noise = this.noiseLevels[i];
                if (noise != null) {
                    double sample = noise.noise(wrap(x * inFactor), wrap(y * inFactor), wrap(z * inFactor));
                    result += this.amplitudes[i] * sample * valFactor;
                }
                inFactor *= 2.0;
                valFactor /= 2.0;
            }
            return result;
        }

        private static double wrap(double d) {
            return d - (double) Math.round(d / ROUND_OFF) * ROUND_OFF;
        }
    }

    public static class ImprovedNoise {
        private final byte[] p = new byte[256];
        public final double xo;
        public final double yo;
        public final double zo;

        private static final int[][] GRADIENTS = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
        };

        public ImprovedNoise(Xoroshiro128 rng) {
            this.xo = rng.nextDouble() * 256.0;
            this.yo = rng.nextDouble() * 256.0;
            this.zo = rng.nextDouble() * 256.0;
            for (int i = 0; i < 256; i++) {
                this.p[i] = (byte) i;
            }
            for (int i = 0; i < 256; i++) {
                int n2 = rng.nextInt(256 - i);
                byte by = this.p[i];
                this.p[i] = this.p[i + n2];
                this.p[i + n2] = by;
            }
        }

        public double noise(double x, double y, double z) {
            double d7 = x + this.xo;
            double d8 = y + this.yo;
            double d9 = z + this.zo;
            int n = (int) Math.floor(d7);
            int n2 = (int) Math.floor(d8);
            int n3 = (int) Math.floor(d9);
            double d10 = d7 - (double) n;
            double d11 = d8 - (double) n2;
            double d12 = d9 - (double) n3;

            int n4 = this.p(n);
            int n5 = this.p(n + 1);
            int n6 = this.p(n4 + n2);
            int n7 = this.p(n4 + n2 + 1);
            int n8 = this.p(n5 + n2);
            int n9 = this.p(n5 + n2 + 1);

            double d5 = gradDot(this.p(n6 + n3), d10, d11, d12);
            double d6 = gradDot(this.p(n8 + n3), d10 - 1.0, d11, d12);
            double d7_ = gradDot(this.p(n7 + n3), d10, d11 - 1.0, d12);
            double d8_ = gradDot(this.p(n9 + n3), d10 - 1.0, d11 - 1.0, d12);
            double d9_ = gradDot(this.p(n6 + n3 + 1), d10, d11, d12 - 1.0);
            double d10_ = gradDot(this.p(n8 + n3 + 1), d10 - 1.0, d11, d12 - 1.0);
            double d11_ = gradDot(this.p(n7 + n3 + 1), d10, d11 - 1.0, d12 - 1.0);
            double d12_ = gradDot(this.p(n9 + n3 + 1), d10 - 1.0, d11 - 1.0, d12 - 1.0);

            double dx = smoothstep(d10);
            double dy = smoothstep(d11);
            double dz = smoothstep(d12);

            return lerp3(dx, dy, dz, d5, d6, d7_, d8_, d9_, d10_, d11_, d12_);
        }

        private int p(int n) {
            return this.p[n & 0xFF] & 0xFF;
        }

        private static double gradDot(int n, double x, double y, double z) {
            int[] g = GRADIENTS[n & 0xF];
            return (double) g[0] * x + (double) g[1] * y + (double) g[2] * z;
        }

        private static double smoothstep(double x) {
            return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private static double lerp3(double dx, double dy, double dz,
                                    double c000, double c100, double c010, double c110,
                                    double c001, double c101, double c011, double c111) {
            double c00 = lerp(dx, c000, c100);
            double c10 = lerp(dx, c010, c110);
            double c01 = lerp(dx, c001, c101);
            double c11 = lerp(dx, c011, c111);
            double c0 = lerp(dy, c00, c10);
            double c1 = lerp(dy, c01, c11);
            return lerp(dz, c0, c1);
        }
    }

    public static long[] hashOf(String str) {
        try {
            byte[] b = MessageDigest.getInstance("MD5").digest(str.getBytes(StandardCharsets.UTF_8));
            long lo = ByteBuffer.wrap(b, 0, 8).getLong();
            long hi = ByteBuffer.wrap(b, 8, 8).getLong();
            return new long[]{lo, hi};
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
