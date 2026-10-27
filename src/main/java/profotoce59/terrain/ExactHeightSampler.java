package profotoce59.terrain;

import java.util.Arrays;

public class ExactHeightSampler {
    private static final int HORIZONTAL_CELL_SIZE = 4;
    private static final int VERTICAL_CELL_SIZE = 8;
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int VERTICAL_CELL_COUNT = 48; // 384 / 8
    private static final int MIN_CELL_Y = -8;          // -64 / 8

    private final long worldSeed;
    private final SurfaceHeightEstimator estimator;
    private final BlendedNoise blendedNoise;
    private final NormalNoise jaggedNoise;

    // Cave noises
    private final NormalNoise caveEntrance;
    private final NormalNoise sp3dRarity;
    private final NormalNoise sp3dThickness;
    private final NormalNoise sp3d1;
    private final NormalNoise sp3d2;
    private final NormalNoise spRoughMod;
    private final NormalNoise spRough;
    private final NormalNoise sp2d;
    private final NormalNoise sp2dElevation;
    private final NormalNoise sp2dModulator;
    private final NormalNoise sp2dThickness;
    private final NormalNoise pillar;
    private final NormalNoise pillarRareness;
    private final NormalNoise pillarThickness;
    private final NormalNoise caveLayer;
    private final NormalNoise caveCheese;

    // Noodle noises
    private final NormalNoise noodle;
    private final NormalNoise noodleThickness;
    private final NormalNoise noodleRidgeA;
    private final NormalNoise noodleRidgeB;

    // 16-slot LRU chunk corner cache
    private static final int CHUNK_CACHE_SIZE = 16;
    private static class ChunkCacheEntry {
        long chunkKey = Long.MIN_VALUE;
        long lastAccess = 0;
        final double[][] corners = new double[25][VERTICAL_CELL_COUNT + 1];
        final double[][][] noodleCorners = new double[3][25][VERTICAL_CELL_COUNT + 1];
    }
    private final ChunkCacheEntry[] chunkCache = new ChunkCacheEntry[CHUNK_CACHE_SIZE];
    private long accessTimer = 0;
    private final AquiferSampler aquiferSampler;

    public ExactHeightSampler(long worldSeed) {
        this(new SurfaceHeightEstimator(worldSeed));
    }

    public ExactHeightSampler(SurfaceHeightEstimator estimator) {
        this.worldSeed = estimator.getWorldSeed();
        this.estimator = estimator;
        this.blendedNoise = new BlendedNoise(this.worldSeed);

        double[] jaggedAmps = new double[16];
        Arrays.fill(jaggedAmps, 1.0);
        this.jaggedNoise = createNoise("minecraft:jagged", -16, jaggedAmps);

        this.caveEntrance = createNoise("minecraft:cave_entrance", -7, 0.4, 0.5, 1.0);
        this.sp3dRarity = createNoise("minecraft:spaghetti_3d_rarity", -11, 1.0);
        this.sp3dThickness = createNoise("minecraft:spaghetti_3d_thickness", -8, 1.0);
        this.sp3d1 = createNoise("minecraft:spaghetti_3d_1", -7, 1.0);
        this.sp3d2 = createNoise("minecraft:spaghetti_3d_2", -7, 1.0);
        this.spRoughMod = createNoise("minecraft:spaghetti_roughness_modulator", -8, 1.0);
        this.spRough = createNoise("minecraft:spaghetti_roughness", -5, 1.0);

        this.sp2d = createNoise("minecraft:spaghetti_2d", -7, 1.0);
        this.sp2dElevation = createNoise("minecraft:spaghetti_2d_elevation", -8, 1.0);
        this.sp2dModulator = createNoise("minecraft:spaghetti_2d_modulator", -11, 1.0);
        this.sp2dThickness = createNoise("minecraft:spaghetti_2d_thickness", -11, 1.0);

        this.pillar = createNoise("minecraft:pillar", -7, 1.0, 1.0);
        this.pillarRareness = createNoise("minecraft:pillar_rareness", -8, 1.0);
        this.pillarThickness = createNoise("minecraft:pillar_thickness", -8, 1.0);

        this.caveLayer = createNoise("minecraft:cave_layer", -8, 1.0);
        this.caveCheese = createNoise("minecraft:cave_cheese", -8, 0.5, 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 2.0, 0.0);

        this.noodle = createNoise("minecraft:noodle", -8, 1.0);
        this.noodleThickness = createNoise("minecraft:noodle_thickness", -8, 1.0);
        this.noodleRidgeA = createNoise("minecraft:noodle_ridge_a", -7, 1.0);
        this.noodleRidgeB = createNoise("minecraft:noodle_ridge_b", -7, 1.0);

        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            this.chunkCache[i] = new ChunkCacheEntry();
        }
        this.aquiferSampler = new AquiferSampler(this.estimator);
    }

    private NormalNoise createNoise(String id, int firstOctave, double... amps) {
        Xoroshiro128 xr = Xoroshiro128.createFromWorldSeed(this.worldSeed);
        long xlo = xr.nextLong();
        long xhi = xr.nextLong();
        long[] md5 = NormalNoise.hashOf(id);
        return new NormalNoise(new Xoroshiro128(xlo ^ md5[0], xhi ^ md5[1]), firstOctave, amps);
    }

    public SurfaceHeightEstimator getEstimator() {
        return estimator;
    }

    public BlendedNoise getBlendedNoise() {
        return blendedNoise;
    }

    public void clearCache() {
        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            this.chunkCache[i].chunkKey = Long.MIN_VALUE;
            this.chunkCache[i].lastAccess = 0;
        }
        this.aquiferSampler.clearCache();
    }

    private static double clampedLerp(double a, double b, double delta) {
        if (delta <= 0.0) return a;
        if (delta >= 1.0) return b;
        return a + delta * (b - a);
    }

    private static double lerp(double delta, double a, double b) {
        return a + delta * (b - a);
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

    private static double lerpFromProgress(NormalNoise noise, double x, double y, double z, double a, double b) {
        double v = noise.getValue(x, y, z);
        double t = (v - (-1.0)) / (1.0 - (-1.0));
        return clampedLerp(a, b, t);
    }

    private static double scaleTunnels(double v) {
        if (v < -0.5) return 0.75;
        if (v < 0.0) return 1.0;
        if (v < 0.5) return 1.5;
        return 2.0;
    }

    private static double scaleCaves(double v) {
        if (v < -0.75) return 0.5;
        if (v < -0.5) return 0.75;
        if (v < 0.5) return 1.0;
        if (v < 0.75) return 2.0;
        return 3.0;
    }

    private static double slideApply(double target, int size, int offset, double density, int y) {
        if (size <= 0) return density;
        double dd = (double) (y - offset) / (double) size;
        return clampedLerp(target, density, dd);
    }

    private static double applySlides(double density, int cellY) {
        int i = cellY - MIN_CELL_Y;
        density = slideApply(-0.078125, 2, 8, density, VERTICAL_CELL_COUNT - i);
        density = slideApply(0.1171875, 3, 0, density, i);
        return density;
    }

    private double sampleJagged(double peaks, int x, int z) {
        if (peaks == 0.0) return 0.0;
        double n = this.jaggedNoise.getValue((double) x * 1500.0, 0.0, (double) z * 1500.0);
        return n > 0.0 ? peaks * n : (peaks / 2.0) * n;
    }

    private double sampleCaveEntrance(int x, int y, int z) {
        double s = this.caveEntrance.getValue((double) x * 0.75, (double) y * 0.5, (double) z * 0.75);
        return s + 0.37 + clampedLerp(0.3, 0.0, (10.0 + (double) y) / 40.0);
    }

    private double sampleSpaghettiRoughness(int x, int y, int z) {
        double m = lerpFromProgress(this.spRoughMod, (double) x, (double) y, (double) z, 0.0, 0.1);
        double r = Math.abs(this.spRough.getValue((double) x, (double) y, (double) z));
        return (0.4 - r) * m;
    }

    private double sampleSpaghetti3d(int x, int y, int z) {
        double rarity = this.sp3dRarity.getValue((double) (x * 2), (double) y, (double) (z * 2));
        double s = scaleTunnels(rarity);
        double h = lerpFromProgress(this.sp3dThickness, (double) x, (double) y, (double) z, 0.065, 0.088);

        double l1 = this.sp3d1.getValue((double) x / s, (double) y / s, (double) z / s);
        double m1 = Math.abs(s * l1) - h;
        double l2 = this.sp3d2.getValue((double) x / s, (double) y / s, (double) z / s);
        double m2 = Math.abs(s * l2) - h;

        double maxM = Math.max(m1, m2);
        return Math.max(-1.0, Math.min(1.0, maxM));
    }

    private double sampleSpaghetti2d(int x, int y, int z) {
        double mod = this.sp2dModulator.getValue((double) (x * 2), (double) y, (double) (z * 2));
        double s = scaleCaves(mod);
        double h = lerpFromProgress(this.sp2dThickness, (double) (x * 2), (double) y, (double) (z * 2), 0.6, 1.3);

        double v = this.sp2d.getValue((double) x / s, (double) y / s, (double) z / s);
        double j = Math.abs(s * v) - 0.083 * h;

        double l = lerpFromProgress(this.sp2dElevation, (double) x, 0.0, (double) z, (double) MIN_CELL_Y, 8.0);
        double m = Math.abs(l - (double) y / 8.0) - h;
        m = m * m * m;

        return Math.max(-1.0, Math.min(1.0, Math.max(m, j)));
    }

    private double samplePillar(int x, int y, int z) {
        double rareness = lerpFromProgress(this.pillarRareness, (double) x, (double) y, (double) z, 0.0, 2.0);
        double thick = lerpFromProgress(this.pillarThickness, (double) x, (double) y, (double) z, 0.0, 1.1);
        thick = thick * thick * thick;
        double f = this.pillar.getValue((double) x * 25.0, (double) y * 0.3, (double) z * 25.0);
        double g = thick * (f * 2.0 - rareness);
        return g > 0.03 ? g : Double.NEGATIVE_INFINITY;
    }

    private double sampleCaveLayer(int x, int y, int z) {
        double v = this.caveLayer.getValue((double) x, (double) (y * 8), (double) z);
        return v * v * 4.0;
    }

    private void sampleCornerColumn(int cellX, int cellZ, double[] out) {
        int bx = cellX * HORIZONTAL_CELL_SIZE;
        int bz = cellZ * HORIZONTAL_CELL_SIZE;

        // Sample climate parameters at biome coordinates (cellX, cellZ)
        double px = (double) bx * 0.25 + estimator.getShiftNoise().getValue((double) bx * 0.25, 0.0, (double) bz * 0.25) * 4.0;
        double pz = (double) bz * 0.25 + estimator.getShiftNoise().getValue((double) bz * 0.25, (double) bx * 0.25, 0.0) * 4.0;

        double c = estimator.getContinentalnessNoise().getValue(px, 0.0, pz);
        double e = estimator.getErosionNoise().getValue(px, 0.0, pz);
        double w = estimator.getWeirdnessNoise().getValue(px, 0.0, pz);

        double offset = TerrainSpline.getOffset(c, e, w);
        double factor = TerrainSpline.getFactor(c, e, w);
        double peaks = TerrainSpline.getPeak(c, e, w);

        double jagged = sampleJagged(peaks, bx, bz);

        for (int i = 0; i <= VERTICAL_CELL_COUNT; i++) {
            int y = MIN_Y + i * VERTICAL_CELL_SIZE;
            double baseNoise = blendedNoise.calculateNoise(bx, y, bz);

            double depth = (1.0 - (double) y / 128.0) + offset;
            double dens = (depth + jagged) * factor;
            dens = dens > 0.0 ? dens * 4.0 : dens;
            double g = dens + baseNoise;

            double cheese, spaghetti, pillarVal;
            if (g < -64.0) {
                cheese = g;
                spaghetti = 64.0;
                pillarVal = -64.0;
            } else {
                double diff = g - 1.5625;
                boolean shallow = diff < 0.0;

                double caveEnt = sampleCaveEntrance(bx, y, bz);
                double rough = sampleSpaghettiRoughness(bx, y, bz);
                double sp3d = sampleSpaghetti3d(bx, y, bz);
                double m = Math.min(caveEnt, sp3d + rough);

                if (shallow) {
                    cheese = g;
                    spaghetti = m * 5.0;
                    pillarVal = -64.0;
                } else {
                    double layer = sampleCaveLayer(bx, y, bz);
                    if (layer > 64.0) {
                        cheese = 64.0;
                    } else {
                        double raw = this.caveCheese.getValue((double) bx, (double) y / 1.5, (double) bz);
                        double p = Math.max(-1.0, Math.min(1.0, raw + 0.27));
                        double q = diff * 1.28;
                        cheese = p + clampedLerp(0.5, 0.0, q) + layer;
                    }
                    double sp2dVal = sampleSpaghetti2d(bx, y, bz);
                    spaghetti = Math.min(m, sp2dVal + rough);
                    pillarVal = samplePillar(bx, y, bz);
                }
            }

            double v = Math.max(Math.min(cheese, spaghetti), pillarVal);
            v = applySlides(v, y / VERTICAL_CELL_SIZE);
            if (v < -64.0) v = -64.0;
            else if (v > 64.0) v = 64.0;

            out[i] = v;
        }
    }

    private static final double NOODLE_THICK_BIAS = -0.07500000000000001;
    private static final double NOODLE_THICK_MUL = -0.025;
    private static final double NOODLE_RIDGE_SCALE = 2.6666666666666665;

    private static boolean noodleYInRange(int y) {
        return y >= -60 && y <= 320;
    }

    private void sampleNoodleCornerColumn(int cellX, int cellZ, double[] outThick, double[] outRidgeA, double[] outRidgeB) {
        int bx = cellX * HORIZONTAL_CELL_SIZE;
        int bz = cellZ * HORIZONTAL_CELL_SIZE;
        for (int i = 0; i <= VERTICAL_CELL_COUNT; i++) {
            int y = MIN_Y + i * VERTICAL_CELL_SIZE;
            if (!noodleYInRange(y)) {
                outThick[i] = 0.0;
                outRidgeA[i] = 0.0;
                outRidgeB[i] = 0.0;
            } else {
                outThick[i] = NOODLE_THICK_BIAS + NOODLE_THICK_MUL * this.noodleThickness.getValue((double) bx, (double) y, (double) bz);
                outRidgeA[i] = this.noodleRidgeA.getValue((double) bx * NOODLE_RIDGE_SCALE, (double) y * NOODLE_RIDGE_SCALE, (double) bz * NOODLE_RIDGE_SCALE);
                outRidgeB[i] = this.noodleRidgeB.getValue((double) bx * NOODLE_RIDGE_SCALE, (double) y * NOODLE_RIDGE_SCALE, (double) bz * NOODLE_RIDGE_SCALE);
            }
        }
    }

    private ChunkCacheEntry getChunkEntry(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) | (((long) chunkZ) & 0xFFFFFFFFL);
        accessTimer++;

        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            if (this.chunkCache[i].chunkKey == key) {
                this.chunkCache[i].lastAccess = accessTimer;
                return this.chunkCache[i];
            }
        }

        // LRU evict
        int victim = 0;
        long minAccess = Long.MAX_VALUE;
        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            if (this.chunkCache[i].chunkKey == Long.MIN_VALUE) {
                victim = i;
                break;
            }
            if (this.chunkCache[i].lastAccess < minAccess) {
                minAccess = this.chunkCache[i].lastAccess;
                victim = i;
            }
        }

        ChunkCacheEntry entry = this.chunkCache[victim];
        entry.chunkKey = key;
        entry.lastAccess = accessTimer;

        int baseCellX = chunkX * 4;
        int baseCellZ = chunkZ * 4;
        for (int gx = 0; gx < 5; gx++) {
            for (int gz = 0; gz < 5; gz++) {
                int idx = gx * 5 + gz;
                sampleCornerColumn(baseCellX + gx, baseCellZ + gz, entry.corners[idx]);
                sampleNoodleCornerColumn(baseCellX + gx, baseCellZ + gz,
                        entry.noodleCorners[0][idx],
                        entry.noodleCorners[1][idx],
                        entry.noodleCorners[2][idx]);
            }
        }
        return entry;
    }

    public static double squeeze(double v) {
        double a = Math.max(-1.0, Math.min(1.0, v * 0.64));
        return a / 2.0 - a * a * a / 24.0;
    }

    private static double interpCorners(double[][] grid, int gx, int gz, double dx, double dy, double dz, int cellY) {
        double[] c00 = grid[gx * 5 + gz];
        double[] c10 = grid[(gx + 1) * 5 + gz];
        double[] c01 = grid[gx * 5 + gz + 1];
        double[] c11 = grid[(gx + 1) * 5 + gz + 1];
        return lerp3(dx, dy, dz,
                c00[cellY], c10[cellY],
                c00[cellY + 1], c10[cellY + 1],
                c01[cellY], c11[cellY],
                c01[cellY + 1], c11[cellY + 1]);
    }

    public double getInterpolatedDensity(int x, int y, int z) {
        int yOff = y - MIN_Y;
        if (yOff < 0 || yOff >= HEIGHT) return -1.0;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ChunkCacheEntry entry = getChunkEntry(chunkX, chunkZ);

        int cellX = x >> 2;
        int cellZ = z >> 2;
        int gx = cellX & 3;
        int gz = cellZ & 3;

        int relX = x & 3;
        int relZ = z & 3;
        int cellY = yOff / VERTICAL_CELL_SIZE;
        int relY = yOff - cellY * VERTICAL_CELL_SIZE;

        double dx = (double) relX / 4.0;
        double dy = (double) relY / 8.0;
        double dz = (double) relZ / 4.0;

        double v = interpCorners(entry.corners, gx, gz, dx, dy, dz, cellY);
        double squeezy = squeeze(v);

        double noodleN = noodleYInRange(y) ? this.noodle.getValue((double) x, (double) y, (double) z) : -1.0;
        double noodleVal;
        if (noodleN >= -1000000.0 && noodleN < 0.0) {
            noodleVal = 64.0;
        } else {
            double thick = interpCorners(entry.noodleCorners[0], gx, gz, dx, dy, dz, cellY);
            double ridgeA = interpCorners(entry.noodleCorners[1], gx, gz, dx, dy, dz, cellY);
            double ridgeB = interpCorners(entry.noodleCorners[2], gx, gz, dx, dy, dz, cellY);
            noodleVal = thick + 1.5 * Math.max(Math.abs(ridgeA), Math.abs(ridgeB));
        }

        return Math.min(squeezy, noodleVal);
    }

    public int getHeight(int x, int z) {
        for (int i = VERTICAL_CELL_COUNT - 1; i >= 0; i--) {
            for (int j = VERTICAL_CELL_SIZE - 1; j >= 0; j--) {
                int y = (MIN_CELL_Y + i) * VERTICAL_CELL_SIZE + j;
                double density = getInterpolatedDensity(x, y, z);
                byte st = aquiferSampler.computeSubstance(x, y, z, density);
                if (st != AquiferSampler.BLOCK_AIR) {
                    return y + 1;
                }
            }
        }
        return MIN_Y;
    }
}
