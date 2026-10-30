package profotoce59.terrain;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MultiNoiseBiomeSource {
    private final long worldSeed;
    private final NormalNoise shiftNoise;
    private final NormalNoise temperatureNoise;
    private final NormalNoise humidityNoise;
    private final NormalNoise continentalnessNoise;
    private final NormalNoise erosionNoise;
    private final NormalNoise weirdnessNoise;

    private final CubicSpline offsetSpline;

    // Biome tree lookup table
    private static int order;
    private static int[] steps;
    private static int[] param;
    private static long[] nodes;
    private static boolean tableLoaded = false;

    public MultiNoiseBiomeSource(long worldSeed) {
        this.worldSeed = worldSeed;
        loadTable();

        Xoroshiro128 xr = Xoroshiro128.createFromWorldSeed(worldSeed);
        long xlo = xr.nextLong();
        long xhi = xr.nextLong();

        // 1. Shift: md5 "minecraft:offset", firstOctave = -3, amp = {1, 1, 1, 0}
        long[] md5Shift = NormalNoise.hashOf("minecraft:offset");
        Xoroshiro128 shiftRng = new Xoroshiro128(xlo ^ md5Shift[0], xhi ^ md5Shift[1]);
        this.shiftNoise = new NormalNoise(shiftRng, -3, new double[]{1, 1, 1, 0});

        // 2. Temperature: md5 "minecraft:temperature", firstOctave = -10, amp = {1.5, 0, 1, 0, 0, 0}
        long[] md5Temp = NormalNoise.hashOf("minecraft:temperature");
        Xoroshiro128 tempRng = new Xoroshiro128(xlo ^ md5Temp[0], xhi ^ md5Temp[1]);
        this.temperatureNoise = new NormalNoise(tempRng, -10, new double[]{1.5, 0, 1, 0, 0, 0});

        // 3. Humidity: md5 "minecraft:vegetation", firstOctave = -8, amp = {1, 1, 0, 0, 0, 0}
        long[] md5Hum = NormalNoise.hashOf("minecraft:vegetation");
        Xoroshiro128 humRng = new Xoroshiro128(xlo ^ md5Hum[0], xhi ^ md5Hum[1]);
        this.humidityNoise = new NormalNoise(humRng, -8, new double[]{1, 1, 0, 0, 0, 0});

        // 4. Continentalness: md5 "minecraft:continentalness", firstOctave = -9, amp = {1, 1, 2, 2, 2, 1, 1, 1, 1}
        long[] md5Cont = NormalNoise.hashOf("minecraft:continentalness");
        Xoroshiro128 contRng = new Xoroshiro128(xlo ^ md5Cont[0], xhi ^ md5Cont[1]);
        this.continentalnessNoise = new NormalNoise(contRng, -9, new double[]{1, 1, 2, 2, 2, 1, 1, 1, 1});

        // 5. Erosion: md5 "minecraft:erosion", firstOctave = -9, amp = {1, 1, 0, 1, 1}
        long[] md5Erosion = NormalNoise.hashOf("minecraft:erosion");
        Xoroshiro128 erosionRng = new Xoroshiro128(xlo ^ md5Erosion[0], xhi ^ md5Erosion[1]);
        this.erosionNoise = new NormalNoise(erosionRng, -9, new double[]{1, 1, 0, 1, 1});

        // 6. Weirdness: md5 "minecraft:ridge", firstOctave = -7, amp = {1, 2, 1, 0, 0, 0}
        long[] md5Ridge = NormalNoise.hashOf("minecraft:ridge");
        Xoroshiro128 ridgeRng = new Xoroshiro128(xlo ^ md5Ridge[0], xhi ^ md5Ridge[1]);
        this.weirdnessNoise = new NormalNoise(ridgeRng, -7, new double[]{1, 2, 1, 0, 0, 0});

        this.offsetSpline = TerrainProvider.overworldOffset();
    }

    private static synchronized void loadTable() {
        if (tableLoaded) return;
        try (InputStream in = MultiNoiseBiomeSource.class.getResourceAsStream("/profotoce59/terrain/btree20.bin")) {
            if (in == null) {
                throw new IllegalStateException("Cannot find btree20.bin resource in classpath");
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            order = buf.getInt();
            int nsteps = buf.getInt();
            steps = new int[nsteps];
            for (int i = 0; i < nsteps; i++) {
                steps[i] = buf.getInt();
            }

            int nparams = buf.getInt();
            param = new int[nparams * 2];
            for (int i = 0; i < nparams * 2; i++) {
                param[i] = buf.getInt();
            }

            int nnodes = buf.getInt();
            nodes = new long[nnodes];
            for (int i = 0; i < nnodes; i++) {
                nodes[i] = buf.getLong();
            }
            tableLoaded = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load btree20.bin", e);
        }
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667f) - 0.33333334f) * 3.0f;
    }

    private static long get_np_dist(long[] np, int nodeIdx) {
        long ds = 0;
        long node = nodes[nodeIdx];
        for (int i = 0; i < 6; i++) {
            int pIdx = (int) ((node >>> (8 * i)) & 0xFF);
            long a = np[i] - param[2 * pIdx + 1];
            long b = param[2 * pIdx + 0] - np[i];
            long d = a > 0 ? a : (b > 0 ? b : 0);
            d = d * d;
            ds += d;
        }
        return ds;
    }

    private static int get_resulting_node(long[] np, int idx, int alt, long ds, int depth) {
        if (steps[depth] == 0) {
            return idx;
        }
        int step;
        do {
            step = steps[depth];
            depth++;
        } while (idx + step >= nodes.length);

        long node = nodes[idx];
        int inner = (int) ((node >>> 48) & 0xFFFF);

        int leaf = alt;
        for (int i = 0; i < order; i++) {
            long ds_inner = get_np_dist(np, inner);
            if (Long.compareUnsigned(ds_inner, ds) < 0) {
                int leaf2 = get_resulting_node(np, inner, leaf, ds, depth);
                long ds_leaf2 = (inner == leaf2) ? ds_inner : get_np_dist(np, leaf2);
                if (Long.compareUnsigned(ds_leaf2, ds) < 0) {
                    ds = ds_leaf2;
                    leaf = leaf2;
                }
            }

            inner += step;
            if (inner >= nodes.length) {
                break;
            }
        }
        return leaf;
    }

    public float getContinentalness(int quartX, int quartZ) {
        double px = (double) quartX + shiftNoise.getValue(quartX, 0.0, quartZ) * 4.0;
        double pz = (double) quartZ + shiftNoise.getValue(quartZ, quartX, 0.0) * 4.0;
        return (float) continentalnessNoise.getValue(px, 0.0, pz);
    }

    public boolean isContinentalnessInland(int quartX, int quartZ) {
        return getContinentalness(quartX, quartZ) >= -0.19f;
    }

    public int climateToBiome(long[] np) {
        int idx = get_resulting_node(np, 0, 0, -1L, 0);
        return (int) ((nodes[idx] >>> 48) & 0xFF);
    }

    /**
     * Sample biome at quart (1:4 scale) coordinates.
     */
    public int getBiomeAtQuart(int quartX, int quartY, int quartZ) {
        double px = (double) quartX + shiftNoise.getValue(quartX, 0.0, quartZ) * 4.0;
        double pz = (double) quartZ + shiftNoise.getValue(quartZ, quartX, 0.0) * 4.0;

        float c = (float) continentalnessNoise.getValue(px, 0.0, pz);
        float e = (float) erosionNoise.getValue(px, 0.0, pz);
        float w = (float) weirdnessNoise.getValue(px, 0.0, pz);
        float pv = peaksAndValleys(w);

        float[] np_param = {c, e, pv, w};
        double off = offsetSpline.apply(np_param) + 0.015;
        float d = (float) (1.0 - (quartY * 4.0) / 128.0 - 83.0 / 160.0 + off);

        float t = (float) temperatureNoise.getValue(px, 0.0, pz);
        float h = (float) humidityNoise.getValue(px, 0.0, pz);

        long[] np = new long[]{
                (long) (10000.0f * t),
                (long) (10000.0f * h),
                (long) (10000.0f * c),
                (long) (10000.0f * e),
                (long) (10000.0f * d),
                (long) (10000.0f * w)
        };

        return climateToBiome(np);
    }

    /**
     * Check if a village can spawn in chunk (chunkX, chunkZ).
     * Returns the viable Village biome ID if viable, or -1 if not.
     */
    public int getViableVillageBiome(int chunkX, int chunkZ, int surfaceY) {
        int sampleX = chunkX * 4 + 2;
        int sampleZ = chunkZ * 4 + 2;
        int sampleY = surfaceY >> 2;

        int id = getBiomeAtQuart(sampleX, sampleY, sampleZ);
        if (id == 1 || id == 168 || id == 129) return 1;
        if (id == 2) return 2;
        if (id == 35 || id == 36 || id == 163 || id == 164) return 35;
        if (id == 5) return 5;
        if (id == 12) return 12;

        return -1;
    }

    public int getViableVillageBiomeAtBlock(int blockX, int blockZ) {
        int sampleX = blockX >> 2;
        int sampleZ = blockZ >> 2;
        int sampleY = 79; // 319 >> 2

        int id = getBiomeAtQuart(sampleX, sampleY, sampleZ);
        if (id == 1 || id == 168 || id == 129) return 1;
        if (id == 2) return 2;
        if (id == 35 || id == 36 || id == 163 || id == 164) return 35;
        if (id == 5) return 5;
        if (id == 12) return 12;

        return -1;
    }

    public int getViableVillageBiome(int chunkX, int chunkZ) {
        // In 1.18+, village types are checked separately at sampleY = 319 >> 2 = 79.
        // Center of starting chunk is (chunkX * 4 + 2, chunkZ * 4 + 2).
        int sampleX = chunkX * 4 + 2;
        int sampleZ = chunkZ * 4 + 2;
        int sampleY = 79; // 319 >> 2

        int id = getBiomeAtQuart(sampleX, sampleY, sampleZ);
        // Valid village biomes:
        // Plains: 1 (plains), 168 (meadow)
        // Desert: 2
        // Savanna: 35
        // Taiga: 5
        // Snowy: 12 (snowy_plains)
        if (id == 1 || id == 168 || id == 129) return 1;
        if (id == 2) return 2;
        if (id == 35 || id == 36 || id == 163 || id == 164) return 35;
        if (id == 5) return 5;
        if (id == 12) return 12;

        return -1;
    }

    public long getWorldSeed() {
        return worldSeed;
    }
}
