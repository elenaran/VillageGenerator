package profotoce59.terrain;

public class BlendedNoise {
    private static final double BASE_XZ_MULTIPLIER = 684.412 * 0.25;  // 171.103
    private static final double BASE_Y_MULTIPLIER = 684.412 * 0.125;  // 85.5515
    private static final double XZ_FACTOR = 80.0;
    private static final double Y_FACTOR = 160.0;
    private static final double SMEAR_SCALE_MULTIPLIER = 8.0;

    private static final double ROUND_OFF = 3.3554432E7;

    private final LegacyOctave lower;
    private final LegacyOctave upper;
    private final LegacyOctave interp;

    public BlendedNoise(long worldSeed) {
        Xoroshiro128 root = Xoroshiro128.createFromWorldSeed(worldSeed);
        long deriverLo = root.nextLong();
        long deriverHi = root.nextLong();
        long[] md5 = NormalNoise.hashOf("minecraft:terrain");
        Xoroshiro128 terrainRng = new Xoroshiro128(deriverLo ^ md5[0], deriverHi ^ md5[1]);

        this.lower = new LegacyOctave(terrainRng, -15, 16);
        this.upper = new LegacyOctave(terrainRng, -15, 16);
        this.interp = new LegacyOctave(terrainRng, -7, 8);
    }

    public static double maintainPrecision(double v) {
        if (v > -1.0e7 && v < 1.0e7) return v;
        return v - Math.floor(v * 2.9802322387695312E-08 + 0.5) * ROUND_OFF;
    }

    private static double clampedLerp(double a, double b, double delta) {
        if (delta <= 0.0) return a;
        if (delta >= 1.0) return b;
        return a + delta * (b - a);
    }

    public double calculateNoise(int x, int y, int z) {
        double d = (double) x * BASE_XZ_MULTIPLIER;
        double e = (double) y * BASE_Y_MULTIPLIER;
        double f = (double) z * BASE_XZ_MULTIPLIER;
        double g = d / XZ_FACTOR;
        double h = e / Y_FACTOR;
        double i = f / XZ_FACTOR;
        double j = BASE_Y_MULTIPLIER * SMEAR_SCALE_MULTIPLIER;
        double k = j / Y_FACTOR;

        double n = 0.0;
        double q = 1.0;
        for (int oct = 0; oct < 8; oct++) {
            Perlin p = this.interp.get(oct);
            if (p != null) {
                n += p.sample(
                        maintainPrecision(g * q),
                        maintainPrecision(h * q),
                        maintainPrecision(i * q),
                        k * q,
                        h * q
                ) / q;
            }
            q /= 2.0;
        }

        double t = (n / 10.0 + 1.0) / 2.0;
        boolean skipMin = t >= 1.0;
        boolean skipMax = t <= 0.0;

        double l = 0.0;
        double m = 0.0;
        q = 1.0;
        for (int oct = 0; oct < 16; oct++) {
            double xx = maintainPrecision(d * q);
            double yy = maintainPrecision(e * q);
            double zz = maintainPrecision(f * q);
            double ys = j * q;
            double ymax = e * q;

            if (!skipMin) {
                Perlin p = this.lower.get(oct);
                if (p != null) {
                    l += p.sample(xx, yy, zz, ys, ymax) / q;
                }
            }
            if (!skipMax) {
                Perlin p = this.upper.get(oct);
                if (p != null) {
                    m += p.sample(xx, yy, zz, ys, ymax) / q;
                }
            }
            q /= 2.0;
        }

        return clampedLerp(l / 512.0, m / 512.0, t) / 128.0;
    }

    public static class LegacyOctave {
        private final int n;
        private final Perlin[] octaves;

        public LegacyOctave(Xoroshiro128 rng, int firstOctave, int n) {
            this.n = n;
            this.octaves = new Perlin[n];
            int i0 = -firstOctave;

            Perlin shared = new Perlin(rng);
            if (i0 >= 0 && i0 < n) {
                this.octaves[i0] = shared;
            }

            for (int j = i0 - 1; j >= 0; j--) {
                if (j < n) {
                    this.octaves[j] = new Perlin(rng);
                }
            }
        }

        public Perlin get(int i) {
            int idx = this.n - 1 - i;
            if (idx < 0 || idx >= this.n) return null;
            return this.octaves[idx];
        }
    }

    public static class Perlin {
        private final byte[] perm = new byte[256];
        public final double ox;
        public final double oy;
        public final double oz;

        private static final int[][] GRADIENTS = {
                {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
                {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
                {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
                {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
        };

        public Perlin(Xoroshiro128 rng) {
            this.ox = rng.nextDouble() * 256.0;
            this.oy = rng.nextDouble() * 256.0;
            this.oz = rng.nextDouble() * 256.0;
            for (int i = 0; i < 256; i++) {
                this.perm[i] = (byte) i;
            }
            for (int i = 0; i < 256; i++) {
                int j = rng.nextInt(256 - i);
                byte b = this.perm[i];
                this.perm[i] = this.perm[i + j];
                this.perm[i + j] = b;
            }
        }

        private int p(int i) {
            return this.perm[i & 0xFF] & 0xFF;
        }

        private static double smoothstep(double d) {
            return d * d * d * (d * (d * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double delta, double start, double end) {
            return start + delta * (end - start);
        }

        private static double lerp3(double dx, double dy, double dz,
                                    double n000, double n100, double n010, double n110,
                                    double n001, double n101, double n011, double n111) {
            double xz00 = lerp(dy, n000, n010);
            double xz10 = lerp(dy, n100, n110);
            double xz01 = lerp(dy, n001, n011);
            double xz11 = lerp(dy, n101, n111);
            double z0 = lerp(dx, xz00, xz10);
            double z1 = lerp(dx, xz01, xz11);
            return lerp(dz, z0, z1);
        }

        private static double gradDot(int hash, double x, double y, double z) {
            int[] g = GRADIENTS[hash & 15];
            return (double) g[0] * x + (double) g[1] * y + (double) g[2] * z;
        }

        public double sample(double x, double y, double z, double yScale, double yMax) {
            double d = x + this.ox;
            double e = y + this.oy;
            double f = z + this.oz;
            int i = (int) Math.floor(d);
            int j = (int) Math.floor(e);
            int k = (int) Math.floor(f);
            double g = d - (double) i;
            double h = e - (double) j;
            double l = f - (double) k;
            double m;
            if (yScale != 0.0) {
                double nn = (yMax >= 0.0 && yMax < h) ? yMax : h;
                m = Math.floor(nn / yScale + 1.0000000116860974E-7) * yScale;
            } else {
                m = 0.0;
            }

            int a = p(i);
            int b = p(i + 1);
            int c = p(a + j);
            int d_ = p(a + j + 1);
            int e_ = p(b + j);
            int f_ = p(b + j + 1);

            double ym = h - m;
            double v000 = gradDot(p(c + k), g, ym, l);
            double v100 = gradDot(p(e_ + k), g - 1.0, ym, l);
            double v010 = gradDot(p(d_ + k), g, ym - 1.0, l);
            double v110 = gradDot(p(f_ + k), g - 1.0, ym - 1.0, l);
            double v001 = gradDot(p(c + k + 1), g, ym, l - 1.0);
            double v101 = gradDot(p(e_ + k + 1), g - 1.0, ym, l - 1.0);
            double v011 = gradDot(p(d_ + k + 1), g, ym - 1.0, l - 1.0);
            double v111 = gradDot(p(f_ + k + 1), g - 1.0, ym - 1.0, l - 1.0);

            return lerp3(smoothstep(g), smoothstep(h), smoothstep(l),
                    v000, v100, v010, v110, v001, v101, v011, v111);
        }
    }
}
