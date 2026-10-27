package profotoce59.terrain;

import java.util.Arrays;

/**
 * Ported directly from lysh-c aquifer.c & column_top.c (MC 1.21 NoiseBasedAquifer & preliminarySurfaceLevel).
 * Provides bit-for-bit exact aquifer substance evaluation and preliminary surface level computation.
 */
public class AquiferSampler {
    public static final byte BLOCK_NULL = 0;  // Solid (non-air)
    public static final byte BLOCK_AIR = 1;
    public static final byte BLOCK_WATER = 2;
    public static final byte BLOCK_LAVA = 3;

    private static final long POS_UNSET = Long.MAX_VALUE;
    private static final int NO_FLUID_Y = -32512;
    private static final double DEEP_DARK_EROSION = -0.22499999403953552;
    private static final double DEEP_DARK_DEPTH = 0.8999999761581421;
    private static final double FLOWING_UPDATE_SIMULARITY = -0.76;

    private static final int LAVA_Y = -54;
    private static final int WATER_Y = 63;
    private static final int LAVA_CUT = -54;

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = {
            {0, 0},
            {-2, -1}, {-1, -1}, {0, -1}, {1, -1},
            {-3, 0}, {-2, 0}, {-1, 0}, {1, 0},
            {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
    };

    private final SurfaceHeightEstimator estimator;
    private final NormalNoise barrierNoise;
    private final NormalNoise floodednessNoise;
    private final NormalNoise spreadNoise;
    private final NormalNoise lavaNoise;
    private final long randLo;
    private final long randHi;

    // 16-slot LRU chunk aquifer state cache
    private static final int CHUNK_CACHE_SIZE = 16;

    private static class AquiferChunkState {
        long chunkKey = Long.MIN_VALUE;
        long lastAccess = 0;
        int minGridX, minGridY, minGridZ;
        int gridSizeX, gridSizeY, gridSizeZ;
        int gridLen;
        int skipSamplingAboveY;
        int[] fluidLevel;
        byte[] fluidType;
        boolean[] cacheValid;
        long[] location;
    }

    private final AquiferChunkState[] chunkStates = new AquiferChunkState[CHUNK_CACHE_SIZE];
    private long accessTimer = 0;

    // 256-slot simple hash cache for preliminarySurfaceLevel(biomeX, biomeZ) since bx >> 2 is biome-aligned
    private static final int PSL_CACHE_MASK = 1023;
    private final long[] pslKeys = new long[PSL_CACHE_MASK + 1];
    private final int[] pslValues = new int[PSL_CACHE_MASK + 1];

    public AquiferSampler(SurfaceHeightEstimator estimator) {
        this.estimator = estimator;
        long worldSeed = estimator.getWorldSeed();

        this.barrierNoise = createNoise(worldSeed, "minecraft:aquifer_barrier", -3, 1.0);
        this.floodednessNoise = createNoise(worldSeed, "minecraft:aquifer_fluid_level_floodedness", -7, 1.0);
        this.spreadNoise = createNoise(worldSeed, "minecraft:aquifer_fluid_level_spread", -5, 1.0);
        this.lavaNoise = createNoise(worldSeed, "minecraft:aquifer_lava", -1, 1.0);

        Xoroshiro128 rng = Xoroshiro128.createFromWorldSeed(worldSeed);
        long baseLo = rng.nextLong();
        long baseHi = rng.nextLong();
        long[] md5 = NormalNoise.hashOf("minecraft:aquifer");
        Xoroshiro128 aqRng = new Xoroshiro128(baseLo ^ md5[0], baseHi ^ md5[1]);
        this.randLo = aqRng.nextLong();
        this.randHi = aqRng.nextLong();

        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            this.chunkStates[i] = new AquiferChunkState();
        }
        Arrays.fill(this.pslKeys, Long.MIN_VALUE);
    }

    private static NormalNoise createNoise(long worldSeed, String id, int firstOctave, double... amps) {
        Xoroshiro128 xr = Xoroshiro128.createFromWorldSeed(worldSeed);
        long xlo = xr.nextLong();
        long xhi = xr.nextLong();
        long[] md5 = NormalNoise.hashOf(id);
        return new NormalNoise(new Xoroshiro128(xlo ^ md5[0], xhi ^ md5[1]), firstOctave, amps);
    }

    public void clearCache() {
        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            this.chunkStates[i].chunkKey = Long.MIN_VALUE;
            this.chunkStates[i].lastAccess = 0;
        }
        Arrays.fill(this.pslKeys, Long.MIN_VALUE);
    }

    private static int gridX(int v) {
        return v >> 4;
    }

    private static int gridZ(int v) {
        return v >> 4;
    }

    private static int gridY(int v) {
        return Math.floorDiv(v, 12);
    }

    private static int fromGridX(int i, int j) {
        return (i << 4) + j;
    }

    private static int fromGridZ(int i, int j) {
        return (i << 4) + j;
    }

    private static int fromGridY(int i, int j) {
        return i * 12 + j;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static double clampedLerp(double start, double end, double delta) {
        if (delta < 0.0) return start;
        if (delta > 1.0) return end;
        return lerp(delta, start, end);
    }

    private static double lerpProgress(double value, double start, double end) {
        return (value - start) / (end - start);
    }

    private static double lerpFromProgress(double value, double min, double max, double minOut, double maxOut) {
        return lerp(lerpProgress(value, min, max), minOut, maxOut);
    }

    private static double yClampedGradient(double y, double fromY, double toY, double fromValue, double toValue) {
        double t = clamp((y - fromY) / (toY - fromY), 0.0, 1.0);
        return lerp(t, fromValue, toValue);
    }

    private static double quarterNegative(double v) {
        return v > 0.0 ? v : v * 0.25;
    }

    private static double psDensity(double y, double offset, double factor) {
        double depth = yClampedGradient(y, -64.0, 320.0, 1.5, -1.5) + offset;
        double cheese = 4.0 * quarterNegative(depth * factor);
        double c = clamp(-0.703125 + cheese, -64.0, 64.0);
        double a1 = yClampedGradient(y, 240.0, 256.0, 1.0, 0.0);
        double a2 = yClampedGradient(y, -64.0, -40.0, 0.0, 1.0);
        return -0.390625 + (0.1171875 + a2 * (-0.1171875 + (-0.078125 + a1 * (0.078125 + c))));
    }

    private static double psUpperBound(double offset, double factor) {
        double inv = 1.0 / factor;
        double inner = inv * 0.2734375 + offset * -1.0;
        return clamp(128.0 + -128.0 * inner, -40.0, 320.0);
    }

    public int preliminarySurfaceLevel(int blockX, int blockZ) {
        int biomeX = blockX >> 2;
        int biomeZ = blockZ >> 2;
        long key = (((long) biomeX) << 32) | (((long) biomeZ) & 0xFFFFFFFFL);
        int slot = ((int) (key ^ (key >>> 32))) & PSL_CACHE_MASK;
        if (this.pslKeys[slot] == key) {
            return this.pslValues[slot];
        }

        double shiftedX = (double) biomeX + estimator.getShiftNoise().getValue((double) biomeX, 0.0, (double) biomeZ) * 4.0;
        double shiftedZ = (double) biomeZ + estimator.getShiftNoise().getValue((double) biomeZ, (double) biomeX, 0.0) * 4.0;

        float c = (float) estimator.getContinentalnessNoise().getValue(shiftedX, 0.0, shiftedZ);
        float e = (float) estimator.getErosionNoise().getValue(shiftedX, 0.0, shiftedZ);
        float w = (float) estimator.getWeirdnessNoise().getValue(shiftedX, 0.0, shiftedZ);
        // Note: column_top.c uses lysh_normalized_weirdness(float w) = -(fabsf(fabsf(w) - 0.6666667f) - 0.33333334f) * 3.0f
        float pv = -(Math.abs(Math.abs(w) - 0.6666667f) - 0.33333334f) * 3.0f;
        float[] p = new float[]{c, e, pv, w};

        double rawOffset = (double) TerrainSpline.apply(TerrainSpline.ROOT_OFFSET, p);
        double rawFactor = (double) TerrainSpline.apply(TerrainSpline.ROOT_FACTOR, p);

        double offset = -0.5037500262260437 + rawOffset;
        double factor = 10.0 + (-10.0 + rawFactor);

        int y = (int) (psUpperBound(offset, factor) / 8.0) * 8;
        int res = -64;
        if (y > -64) {
            for (int i = y; i >= -64; i -= 8) {
                if (psDensity((double) i, offset, factor) > 0.0) {
                    res = i;
                    break;
                }
            }
        }
        this.pslKeys[slot] = key;
        this.pslValues[slot] = res;
        return res;
    }

    private boolean isDeepDarkRegion(int x, int y, int z) {
        int biomeX = x >> 2;
        int biomeZ = z >> 2;
        double shiftedX = (double) biomeX + estimator.getShiftNoise().getValue((double) biomeX, 0.0, (double) biomeZ) * 4.0;
        double shiftedZ = (double) biomeZ + estimator.getShiftNoise().getValue((double) biomeZ, (double) biomeX, 0.0) * 4.0;

        double e = estimator.getErosionNoise().getValue(shiftedX, 0.0, shiftedZ);
        if (!(e < DEEP_DARK_EROSION)) return false;

        double c = estimator.getContinentalnessNoise().getValue(shiftedX, 0.0, shiftedZ);
        double w = estimator.getWeirdnessNoise().getValue(shiftedX, 0.0, shiftedZ);
        float cf = (float) c;
        float ef = (float) e;
        float wf = (float) w;
        float pv = -(Math.abs(Math.abs(wf) - 0.6666667f) - 0.33333334f) * 3.0f;
        float[] p = new float[]{cf, ef, pv, wf};
        double rawOffset = (double) TerrainSpline.apply(TerrainSpline.ROOT_OFFSET, p);
        double offset = -0.5037500262260437 + rawOffset;

        double depth = yClampedGradient((double) y, -64.0, 320.0, 1.5, -1.5) + offset;
        return depth > DEEP_DARK_DEPTH;
    }

    private static long blockPosAsLong(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    private static int blockPosX(long v) {
        return (int) (v >> 38);
    }

    private static int blockPosY(long v) {
        return (int) ((v << 52) >> 52);
    }

    private static int blockPosZ(long v) {
        return (int) ((v << 26) >> 38);
    }

    private static long mcHashCode(int x, int y, int z) {
        long xm = (long) (x * 3129871);
        long i = xm ^ ((long) z * 116129781L) ^ (long) y;
        i = i * i * 42317861L + i * 11L;
        return i >> 16;
    }

    private static byte levelBlockAt(int level, byte type, int atY) {
        return atY < level ? type : BLOCK_AIR;
    }

    private static double similarity(int a, int b) {
        return 1.0 - (double) (b - a) / 25.0;
    }

    private AquiferChunkState getChunkState(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) | (((long) chunkZ) & 0xFFFFFFFFL);
        accessTimer++;
        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            if (this.chunkStates[i].chunkKey == key) {
                this.chunkStates[i].lastAccess = accessTimer;
                return this.chunkStates[i];
            }
        }

        int victim = 0;
        long minAccess = Long.MAX_VALUE;
        for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
            if (this.chunkStates[i].chunkKey == Long.MIN_VALUE) {
                victim = i;
                break;
            }
            if (this.chunkStates[i].lastAccess < minAccess) {
                minAccess = this.chunkStates[i].lastAccess;
                victim = i;
            }
        }

        AquiferChunkState st = this.chunkStates[victim];
        st.chunkKey = key;
        st.lastAccess = accessTimer;

        int minBlockX = chunkX * 16, maxBlockX = minBlockX + 15;
        int minBlockZ = chunkZ * 16, maxBlockZ = minBlockZ + 15;

        int minGx = gridX(minBlockX - 5);
        int maxGx = gridX(maxBlockX - 5) + 1;
        int minGz = gridZ(minBlockZ - 5);
        int maxGz = gridZ(maxBlockZ - 5) + 1;

        st.minGridX = minGx;
        st.gridSizeX = maxGx - minGx + 1;
        st.minGridZ = minGz;
        st.gridSizeZ = maxGz - minGz + 1;

        int minGy = gridY(MIN_Y + 1) - 1;
        int maxGy = gridY(MIN_Y + HEIGHT + 1) + 1;
        st.minGridY = minGy;
        st.gridSizeY = maxGy - minGy + 1;

        int n = st.gridSizeX * st.gridSizeY * st.gridSizeZ;
        st.gridLen = n;
        if (st.fluidLevel == null || st.fluidLevel.length < n) {
            st.fluidLevel = new int[n];
            st.fluidType = new byte[n];
            st.cacheValid = new boolean[n];
            st.location = new long[n];
        } else {
            Arrays.fill(st.cacheValid, 0, n, false);
        }
        Arrays.fill(st.location, 0, n, POS_UNSET);

        int x1 = fromGridX(minGx, 0), z1 = fromGridZ(minGz, 0);
        int x2 = fromGridX(maxGx, 9), z2 = fromGridZ(maxGz, 9);
        int maxPsl = Integer.MIN_VALUE;
        for (int z = z1; z <= z2; z += 4) {
            for (int x = x1; x <= x2; x += 4) {
                int p = preliminarySurfaceLevel(x, z);
                if (p > maxPsl) maxPsl = p;
            }
        }
        int g = gridY((maxPsl + 8) + 12) + 1;
        st.skipSamplingAboveY = fromGridY(g, 11) - 1;

        return st;
    }

    private static int aqIndex(AquiferChunkState st, int gx, int gy, int gz) {
        int dx = gx - st.minGridX;
        int dy = gy - st.minGridY;
        int dz = gz - st.minGridZ;
        if (dx < 0 || dx >= st.gridSizeX) return -1;
        if (dy < 0 || dy >= st.gridSizeY) return -1;
        if (dz < 0 || dz >= st.gridSizeZ) return -1;
        return (dy * st.gridSizeZ + dz) * st.gridSizeX + dx;
    }

    private int randomizedFluidSurfaceLevel(int x, int y, int z, int minCellTopY) {
        int fx = Math.floorDiv(x, 16);
        int fy = Math.floorDiv(y, 40);
        int fz = Math.floorDiv(z, 16);
        int base = fy * 40 + 20;
        double n = this.spreadNoise.getValue((double) fx, (double) fy * 0.7142857142857143, (double) fz) * 10.0;
        int q = (int) Math.floor(n / 3.0) * 3;
        int level = base + q;
        return Math.min(minCellTopY, level);
    }

    private int computeSurfaceLevel(int x, int y, int z, int globalLevel, int minCellTopY, boolean hasAbove) {
        if (isDeepDarkRegion(x, y, z)) return NO_FLUID_Y;

        int d = (minCellTopY + 8) - y;
        double f = hasAbove ? clampedLerp(1.0, 0.0, lerpProgress((double) d, 0.0, 64.0)) : 0.0;

        double high = lerpFromProgress(f, 1.0, 0.0, -0.3, 0.8);
        double low = lerpFromProgress(f, 1.0, 0.0, -0.8, 0.4);

        double floodedness = clamp(this.floodednessNoise.getValue((double) x, (double) y * 0.67, (double) z), -1.0, 1.0);
        if (floodedness - high > 0.0) return globalLevel;
        if (floodedness - low > 0.0) return randomizedFluidSurfaceLevel(x, y, z, minCellTopY);
        return NO_FLUID_Y;
    }

    private byte computeFluidType(int x, int y, int z, byte globalType, int level) {
        byte t = globalType;
        if (level <= -10 && level != NO_FLUID_Y && globalType != BLOCK_LAVA) {
            int bx = Math.floorDiv(x, 64);
            int by = Math.floorDiv(y, 40);
            int bz = Math.floorDiv(z, 64);
            double n = this.lavaNoise.getValue((double) bx, (double) by, (double) bz);
            if (Math.abs(n) > 0.3) t = BLOCK_LAVA;
        }
        return t;
    }

    private void computeFluid(AquiferChunkState st, int idx, int x, int y, int z) {
        int globalLevel = y < LAVA_CUT ? LAVA_Y : WATER_Y;
        byte globalType = y < LAVA_CUT ? BLOCK_LAVA : BLOCK_WATER;

        int minCellTopY = Integer.MAX_VALUE;
        int yPlus12 = y + 12;
        int yMinus12 = y - 12;
        boolean hasAbove = false;

        for (int i = 0; i < 13; i++) {
            int bx = x + SURFACE_SAMPLING_OFFSETS_IN_CHUNKS[i][0] * 16;
            int bz = z + SURFACE_SAMPLING_OFFSETS_IN_CHUNKS[i][1] * 16;
            int top = preliminarySurfaceLevel(bx, bz);
            int topAdj = top + 8;
            boolean isCenter = (i == 0);

            if (isCenter && yMinus12 > topAdj) {
                st.fluidLevel[idx] = globalLevel;
                st.fluidType[idx] = globalType;
                st.cacheValid[idx] = true;
                return;
            }
            boolean above = yPlus12 > topAdj;
            if (above || isCenter) {
                int lvl = topAdj < LAVA_CUT ? LAVA_Y : WATER_Y;
                byte typ = topAdj < LAVA_CUT ? BLOCK_LAVA : BLOCK_WATER;
                if (levelBlockAt(lvl, typ, topAdj) != BLOCK_AIR) {
                    if (isCenter) hasAbove = true;
                    if (above) {
                        st.fluidLevel[idx] = lvl;
                        st.fluidType[idx] = typ;
                        st.cacheValid[idx] = true;
                        return;
                    }
                }
            }
            if (top < minCellTopY) minCellTopY = top;
        }

        int level = computeSurfaceLevel(x, y, z, globalLevel, minCellTopY, hasAbove);
        byte type = computeFluidType(x, y, z, globalType, level);
        st.fluidLevel[idx] = level;
        st.fluidType[idx] = type;
        st.cacheValid[idx] = true;
    }

    private void ensureAquiferStatus(AquiferChunkState st, int idx) {
        if (st.cacheValid[idx]) return;
        long p = st.location[idx];
        computeFluid(st, idx, blockPosX(p), blockPosY(p), blockPosZ(p));
    }

    private double calculatePressure(int x, int y, int z, double[] barrierAcc,
                                     int aLevel, byte aType, int bLevel, byte bType) {
        byte sa = levelBlockAt(aLevel, aType, y);
        byte sb = levelBlockAt(bLevel, bType, y);

        if ((sa == BLOCK_LAVA && sb == BLOCK_WATER) || (sa == BLOCK_WATER && sb == BLOCK_LAVA)) {
            return 2.0;
        }

        int dy = Math.abs(aLevel - bLevel);
        if (dy == 0) return 0.0;

        double mid = 0.5 * ((double) aLevel + (double) bLevel);
        double f = ((double) y + 0.5) - mid;
        double half = (double) dy / 2.0;
        double n = half - Math.abs(f);

        double o;
        if (f > 0.0) {
            double p = 0.0 + n;
            o = p > 0.0 ? p / 1.5 : p / 2.5;
        } else {
            double p = 3.0 + n;
            o = p > 0.0 ? p / 3.0 : p / 10.0;
        }

        double inner;
        if (o < -2.0 || o > 2.0) {
            inner = 0.0;
        } else if (Double.isNaN(barrierAcc[0])) {
            double q = this.barrierNoise.getValue((double) x, (double) y * 0.5, (double) z);
            barrierAcc[0] = q;
            inner = q;
        } else {
            inner = barrierAcc[0];
        }
        return 2.0 * (inner + o);
    }

    public byte computeSubstance(int x, int y, int z, double density) {
        if (density > 0.0) {
            return BLOCK_NULL;
        }

        AquiferChunkState st = getChunkState(x >> 4, z >> 4);

        int globalLevel = y < LAVA_CUT ? LAVA_Y : WATER_Y;
        byte globalType = y < LAVA_CUT ? BLOCK_LAVA : BLOCK_WATER;

        if (y > st.skipSamplingAboveY) {
            return levelBlockAt(globalLevel, globalType, y);
        }
        if (levelBlockAt(globalLevel, globalType, y) == BLOCK_LAVA) {
            return BLOCK_LAVA;
        }

        int gx = Math.floorDiv(x - 5, 16);
        int gy = Math.floorDiv(y + 1, 12);
        int gz = Math.floorDiv(z - 5, 16);

        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE, d2 = Integer.MAX_VALUE, d3 = Integer.MAX_VALUE;
        int s0 = 0, s1 = 0, s2 = 0, s3 = 0;

        for (int i = 0; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = 0; k <= 1; k++) {
                    int cx = gx + i, cy = gy + j, cz = gz + k;
                    int idx = aqIndex(st, cx, cy, cz);
                    if (idx < 0) continue;

                    long packed = st.location[idx];
                    if (packed == POS_UNSET) {
                        long h = mcHashCode(cx, cy, cz);
                        Xoroshiro128 rnd = new Xoroshiro128(h ^ this.randLo, this.randHi);
                        int px = fromGridX(cx, rnd.nextInt(10));
                        int py = fromGridY(cy, rnd.nextInt(9));
                        int pz = fromGridZ(cz, rnd.nextInt(10));
                        packed = blockPosAsLong(px, py, pz);
                        st.location[idx] = packed;
                    }

                    int dx = blockPosX(packed) - x;
                    int dy = blockPosY(packed) - y;
                    int dz = blockPosZ(packed) - z;
                    int dist = dx * dx + dy * dy + dz * dz;

                    if (dist <= d0) {
                        d3 = d2; d2 = d1; d1 = d0; d0 = dist;
                        s3 = s2; s2 = s1; s1 = s0; s0 = idx;
                    } else if (dist <= d1) {
                        d3 = d2; d2 = d1; d1 = dist;
                        s3 = s2; s2 = s1; s1 = idx;
                    } else if (dist <= d2) {
                        d3 = d2; d2 = dist;
                        s3 = s2; s2 = idx;
                    } else if (dist <= d3) {
                        d3 = dist; s3 = idx;
                    }
                }
            }
        }

        ensureAquiferStatus(st, s0);
        int l0 = st.fluidLevel[s0];
        byte t0 = st.fluidType[s0];
        double s01 = similarity(d0, d1);
        byte result = levelBlockAt(l0, t0, y);

        if (!(s01 > 0.0)) {
            if (s01 >= FLOWING_UPDATE_SIMULARITY) {
                ensureAquiferStatus(st, s1);
            }
            return result;
        }

        int belowLevel = (y - 1) < LAVA_CUT ? LAVA_Y : WATER_Y;
        byte belowType = (y - 1) < LAVA_CUT ? BLOCK_LAVA : BLOCK_WATER;
        if (result == BLOCK_WATER && levelBlockAt(belowLevel, belowType, y - 1) == BLOCK_LAVA) {
            return result;
        }

        double[] barrierAcc = new double[]{Double.NaN};

        ensureAquiferStatus(st, s1);
        int l1 = st.fluidLevel[s1];
        byte t1 = st.fluidType[s1];
        double p1 = s01 * calculatePressure(x, y, z, barrierAcc, l0, t0, l1, t1);
        if (density + p1 > 0.0) {
            return BLOCK_NULL;
        }

        ensureAquiferStatus(st, s2);
        int l2 = st.fluidLevel[s2];
        byte t2 = st.fluidType[s2];
        double s02 = similarity(d0, d2);
        if (s02 > 0.0) {
            double p2 = s01 * s02 * calculatePressure(x, y, z, barrierAcc, l0, t0, l2, t2);
            if (density + p2 > 0.0) {
                return BLOCK_NULL;
            }
        }

        double s12 = similarity(d1, d2);
        if (s12 > 0.0) {
            double p3 = s01 * s12 * calculatePressure(x, y, z, barrierAcc, l1, t1, l2, t2);
            if (density + p3 > 0.0) {
                return BLOCK_NULL;
            }
        }

        boolean b0 = !(l0 == l1 && t0 == t1);
        boolean b1 = (s12 >= FLOWING_UPDATE_SIMULARITY) && !(l1 == l2 && t1 == t2);
        boolean b2 = (s02 >= FLOWING_UPDATE_SIMULARITY) && !(l0 == l2 && t0 == t2);
        if (!(b0 || b1 || b2)) {
            ensureAquiferStatus(st, s3);
        }
        return result;
    }
}
