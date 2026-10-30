package profotoce59.thread;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.Village;
import com.seedfinding.mcterrain.TerrainGenerator;
import profotoce59.enumType.VillageType;
import profotoce59.properties.VillageConfig;
import profotoce59.properties.VillageGenerator;
import profotoce59.terrain.MultiNoiseBiomeSource;
import profotoce59.terrain.SurfaceHeightEstimator;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class VillageSearcher {

    public static class Filter {
        public MCVersion version = MCVersion.v1_21;
        public Set<VillageType> villageTypes = new HashSet<>(Arrays.asList(VillageType.values()));
        public int minRegionX = -2, maxRegionX = 2;
        public int minRegionZ = -2, maxRegionZ = 2;
        public int minHouses = 0;
        public int minEnclosedBuildings = 0;
        public Map<String, Integer> requiredPieces = new HashMap<>();
        public int maxDistanceBlocks = Integer.MAX_VALUE;

        public Filter setVersion(MCVersion v) { this.version = v; return this; }
        public Filter setVillageTypes(VillageType... types) { this.villageTypes = new HashSet<>(Arrays.asList(types)); return this; }
        public Filter setRegionBounds(int minRx, int maxRx, int minRz, int maxRz) {
            this.minRegionX = minRx; this.maxRegionX = maxRx;
            this.minRegionZ = minRz; this.maxRegionZ = maxRz;
            return this;
        }
        public Filter setRegionRadius(int r) { return setRegionBounds(-r, r, -r, r); }
        public Filter setMinHouses(int n) { this.minHouses = n; return this; }
        public Filter setMinEnclosedBuildings(int n) { this.minEnclosedBuildings = n; return this; }
        public Filter requirePieceCount(String pieceSubstring, int count) {
            this.requiredPieces.put(pieceSubstring.toLowerCase(), count);
            return this;
        }
        public Filter setMaxDistanceBlocks(int maxDist) { this.maxDistanceBlocks = maxDist; return this; }
    }

    public static class Result {
        public final long worldSeed;
        public final long structureSeed;
        public final VillageType villageType;
        public final CPos chunkPos;
        public final int totalPieces;
        public final int housesCount;
        public final int enclosedBuildingsCount;
        public final List<VillageGenerator.Piece> pieces;

        public Result(long worldSeed, long structureSeed, VillageType villageType, CPos chunkPos,
                      int totalPieces, int housesCount, int enclosedBuildingsCount, List<VillageGenerator.Piece> pieces) {
            this.worldSeed = worldSeed;
            this.structureSeed = structureSeed;
            this.villageType = villageType;
            this.chunkPos = chunkPos;
            this.totalPieces = totalPieces;
            this.housesCount = housesCount;
            this.enclosedBuildingsCount = enclosedBuildingsCount;
            this.pieces = pieces;
        }

        public int getBlockX() { return (chunkPos.getX() << 4) + 8; }
        public int getBlockZ() { return (chunkPos.getZ() << 4) + 8; }

        public int countPiecesMatching(String substring) {
            String lower = substring.toLowerCase();
            int count = 0;
            for (VillageGenerator.Piece p : pieces) {
                if (p.getName().toLowerCase().contains(lower)) count++;
            }
            return count;
        }

        @Override
        public String toString() {
            return String.format("Village Result: Seed %d (struct: %d) | %s at /tp %d 90 %d | Total: %d, Houses: %d, Enclosed: %d",
                    worldSeed, structureSeed, villageType, getBlockX(), getBlockZ(), totalPieces, housesCount, enclosedBuildingsCount);
        }
    }

    private final Filter filter;
    private final int threads;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public VillageSearcher(Filter filter) {
        this(filter, Runtime.getRuntime().availableProcessors());
    }

    public VillageSearcher(Filter filter, int threads) {
        this.filter = filter;
        this.threads = Math.max(1, threads);
    }

    public void stop() {
        this.stopped.set(true);
    }

    public static boolean isEnclosedBuilding(String name) {
        return name.contains("/houses/") && !name.contains("farm") && !name.contains("animal_pen");
    }

    public static int countEnclosed(VillageGenerator gen) {
        int count = 0;
        for (VillageGenerator.Piece p : gen.pieces) {
            if (isEnclosedBuilding(p.getName())) count++;
        }
        return count;
    }

    private static boolean matchesFilter(VillageGenerator gen, Filter f) {
        if (gen.getNumberOfHouses() < f.minHouses) return false;
        if (f.minEnclosedBuildings > 0 && countEnclosed(gen) < f.minEnclosedBuildings) return false;
        for (Map.Entry<String, Integer> req : f.requiredPieces.entrySet()) {
            int count = 0;
            String key = req.getKey();
            for (VillageGenerator.Piece p : gen.pieces) {
                if (p.getName().toLowerCase().contains(key)) count++;
            }
            if (count < req.getValue()) return false;
        }
        return true;
    }

    public void search(long startStructureSeed, long maxStructureSeeds, int maxResults, Consumer<Result> onFound) {
        AtomicInteger found = new AtomicInteger(0);
        MCVersion version = filter.version;
        boolean is18Plus = version.isNewerOrEqualTo(MCVersion.v1_18);

        IntStream.range(0, threads).parallel().forEach(t -> {
            ChunkRand rand = new ChunkRand();
            Village village = VillageConfig.getVillage(version);
            VillageGenerator fastGen = new VillageGenerator(version);

            for (long structSeed = startStructureSeed + t;
                 structSeed < startStructureSeed + maxStructureSeeds && !stopped.get() && found.get() < maxResults;
                 structSeed += threads) {

                for (int rx = filter.minRegionX; rx <= filter.maxRegionX && !stopped.get() && found.get() < maxResults; rx++) {
                    for (int rz = filter.minRegionZ; rz <= filter.maxRegionZ && !stopped.get() && found.get() < maxResults; rz++) {
                        CPos cpos = village.getInRegion(structSeed, rx, rz, rand);
                        if (cpos == null) continue;

                        int bx = (cpos.getX() << 4) + 8, bz = (cpos.getZ() << 4) + 8;
                        if (filter.maxDistanceBlocks < Integer.MAX_VALUE && (long) bx * bx + (long) bz * bz > (long) filter.maxDistanceBlocks * filter.maxDistanceBlocks) {
                            continue;
                        }

                        for (VillageType vType : filter.villageTypes) {
                            // Stage 1: Fast flat-terrain check (no 3D noise)
                            fastGen.generate(structSeed, cpos.getX(), cpos.getZ(), rand, vType, 64);
                            if (!matchesFilter(fastGen, filter)) continue;

                            // Stage 2: 64-bit world seed search (biome check + exact surface heights)
                            for (long upper = 0; upper < 65536 && !stopped.get() && found.get() < maxResults; upper++) {
                                long worldSeed = structSeed | (upper << 48);

                                VillageGenerator exactGen = new VillageGenerator(version);
                                if (is18Plus) {
                                    MultiNoiseBiomeSource bs = new MultiNoiseBiomeSource(worldSeed);
                                    int townCenterX = (cpos.getX() << 4) + 8;
                                    int townCenterZ = (cpos.getZ() << 4) + 8;
                                    if (!fastGen.pieces.isEmpty()) {
                                        townCenterX = (fastGen.pieces.get(0).box.minX + fastGen.pieces.get(0).box.maxX) / 2;
                                        townCenterZ = (fastGen.pieces.get(0).box.minZ + fastGen.pieces.get(0).box.maxZ) / 2;
                                    }
                                    if (bs.getViableVillageBiomeAtBlock(townCenterX, townCenterZ) != VillageType.getTargetBiomeId(vType)) continue;
                                    exactGen.setHeightEstimator(new SurfaceHeightEstimator(worldSeed));
                                    if (!exactGen.generate(worldSeed, cpos.getX(), cpos.getZ(), rand, vType)) continue;
                                } else {
                                    OverworldBiomeSource bs = new OverworldBiomeSource(version, worldSeed);
                                    TerrainGenerator tgen = TerrainGenerator.of(Dimension.OVERWORLD, bs);
                                    if (!exactGen.generate(tgen, cpos.getX(), cpos.getZ(), rand) || exactGen.getVillageType() != vType) continue;
                                }

                                if (!matchesFilter(exactGen, filter)) continue;

                                found.incrementAndGet();
                                onFound.accept(new Result(
                                        worldSeed, structSeed, vType, cpos,
                                        exactGen.pieces.size(), exactGen.getNumberOfHouses(), countEnclosed(exactGen),
                                        new ArrayList<>(exactGen.pieces)
                                ));
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    public static void main(String[] args) {
        Filter f = new Filter()
                .setVersion(MCVersion.v1_21)
                .setVillageTypes(VillageType.DESERT, VillageType.TAIGA, VillageType.PLAINS)
                .setRegionRadius(2)
                .setMinEnclosedBuildings(35)
                .requirePieceCount("cartographer", 2)
                .requirePieceCount("weaponsmith", 1);

        new VillageSearcher(f).search(1, 10000, 5, res -> {
            System.out.println(res);
            System.out.printf("  Cartographers: %d, Weaponsmiths: %d%n",
                    res.countPiecesMatching("cartographer"),
                    res.countPiecesMatching("weaponsmith"));
        });
    }
}
