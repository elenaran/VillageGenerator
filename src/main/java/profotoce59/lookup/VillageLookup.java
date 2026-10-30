package profotoce59.lookup;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockMirror;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.structure.Village;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;
import profotoce59.enumType.VillageType;
import profotoce59.properties.VillageConfig;
import profotoce59.properties.VillageGenerator;
import profotoce59.reecriture.VillagePools.VillageStructureLoot;
import profotoce59.reecriture.VillagePools.VillageStructureSize;
import profotoce59.terrain.MultiNoiseBiomeSource;
import profotoce59.terrain.SurfaceHeightEstimator;
import profotoce59.thread.VillageSearcher;

import java.util.*;

public class VillageLookup {

    public static VillageInfo lookupRegion(long worldSeed, int rx, int rz) {
        return lookupRegion(worldSeed, rx, rz, MCVersion.v1_21, false);
    }

    public static VillageInfo lookupRegion(long worldSeed, int rx, int rz, MCVersion version) {
        return lookupRegion(worldSeed, rx, rz, version, false);
    }

    public static VillageInfo lookupRegion(long worldSeed, int rx, int rz, MCVersion version, boolean generateLoot) {
        Village village = VillageConfig.getVillage(version);
        ChunkRand rand = new ChunkRand();
        CPos designatedStart = village.getInRegion(worldSeed, rx, rz, rand);
        if (designatedStart == null) {
            return VillageInfo.notFound(worldSeed, version, rx, rz, 0, 0,
                    String.format("Could not compute designated village chunk for region (%d, %d).", rx, rz));
        }
        return evaluateVillage(worldSeed, rx, rz, designatedStart.getX(), designatedStart.getZ(), null, version, generateLoot, null);
    }

    public static VillageInfo lookupChunk(long worldSeed, int cx, int cz) {
        return lookupChunk(worldSeed, cx, cz, MCVersion.v1_21, false);
    }

    public static VillageInfo lookupChunk(long worldSeed, int cx, int cz, MCVersion version) {
        return lookupChunk(worldSeed, cx, cz, version, false);
    }

    public static VillageInfo lookupChunk(long worldSeed, int cx, int cz, MCVersion version, boolean generateLoot) {
        int spacing = version.isNewerOrEqualTo(MCVersion.v1_18) ? VillageConfig.CONFIG_1_18.spacing : 32;
        int rx = Math.floorDiv(cx, spacing);
        int rz = Math.floorDiv(cz, spacing);

        Village village = VillageConfig.getVillage(version);
        ChunkRand rand = new ChunkRand();
        CPos designatedStart = village.getInRegion(worldSeed, rx, rz, rand);

        if (designatedStart == null || designatedStart.getX() != cx || designatedStart.getZ() != cz) {
            int desBx = designatedStart != null ? (designatedStart.getX() << 4) + 8 : 0;
            int desBz = designatedStart != null ? (designatedStart.getZ() << 4) + 8 : 0;
            String reason = String.format("No village starts at chunk (%d, %d). For region (%d, %d), the designated start chunk is (%d, %d) [around /tp @s %d 80 %d].",
                    cx, cz, rx, rz,
                    designatedStart != null ? designatedStart.getX() : 0,
                    designatedStart != null ? designatedStart.getZ() : 0,
                    desBx, desBz);
            return VillageInfo.notFound(worldSeed, version, rx, rz, cx, cz, reason);
        }

        return evaluateVillage(worldSeed, rx, rz, cx, cz, null, version, generateLoot, null);
    }

    public static VillageInfo lookupBlock(long worldSeed, int bx, int bz) {
        return lookupBlock(worldSeed, bx, bz, MCVersion.v1_21, false);
    }

    public static VillageInfo lookupBlock(long worldSeed, int bx, int bz, MCVersion version) {
        return lookupBlock(worldSeed, bx, bz, version, false);
    }

    public static VillageInfo lookupBlock(long worldSeed, int bx, int bz, MCVersion version, boolean generateLoot) {
        return lookupChunk(worldSeed, bx >> 4, bz >> 4, version, generateLoot);
    }

    public static VillageInfo simulateChunk(long worldSeed, int cx, int cz, VillageType type, MCVersion version, boolean generateLoot) {
        int spacing = version.isNewerOrEqualTo(MCVersion.v1_18) ? VillageConfig.CONFIG_1_18.spacing : 32;
        int rx = Math.floorDiv(cx, spacing);
        int rz = Math.floorDiv(cz, spacing);
        return evaluateVillage(worldSeed, rx, rz, cx, cz, type, version, generateLoot,
                "Forced jigsaw simulation (bypassing region start chunk check)");
    }

    private static VillageInfo evaluateVillage(long worldSeed, int rx, int rz, int cx, int cz,
                                               VillageType typeOverride, MCVersion version,
                                               boolean generateLoot, String extraNote) {
        ChunkRand rand = new ChunkRand();
        boolean is18Plus = version.isNewerOrEqualTo(MCVersion.v1_18);

        if (is18Plus) {
            MultiNoiseBiomeSource bs = new MultiNoiseBiomeSource(worldSeed);
            int chunkCenterBiomeId = bs.getViableVillageBiome(cx, cz);
            int rawChunkBiomeId = bs.getBiomeAtQuart(cx * 4 + 2, 79, cz * 4 + 2);
            String chunkBiomeName = getBiomeName(rawChunkBiomeId);

            VillageType vType = typeOverride;
            if (vType == null) {
                if (chunkCenterBiomeId == -1) {
                    int bx = (cx << 4) + 8;
                    int bz = (cz << 4) + 8;
                    String reason = String.format("Biome at chunk center (%d, %d) is %s (id %d), which does not support villages.",
                            bx, bz, chunkBiomeName, rawChunkBiomeId);
                    return VillageInfo.notFound(worldSeed, version, rx, rz, cx, cz, reason);
                }
                vType = villageTypeFromBiomeId(chunkCenterBiomeId);
            }

            SurfaceHeightEstimator he = new SurfaceHeightEstimator(worldSeed);
            VillageGenerator gen = new VillageGenerator(version);
            gen.setHeightEstimator(he);

            boolean success = gen.generate(worldSeed, cx, cz, rand, vType);
            if (!success) {
                // Determine why it failed (town meeting point invalid biome check)
                int startBx = (cx << 4);
                int startBz = (cz << 4);
                ChunkRand carverRand = (new ChunkRand()).asChunkRandDebugger();
                carverRand.setCarverSeed(worldSeed, cx, cz, version);
                BlockRotation rot = BlockRotation.getRandom(carverRand);
                VillageGenerator.JigSawPool starts = VillageGenerator.STARTS.get(vType);
                String template = carverRand.getRandom(starts.getTemplates());
                BPos size = VillageStructureSize.STRUCTURE_SIZE.get(template);
                BPos bPos = new CPos(cx, cz).toBlockPos(0);
                BlockBox box = BlockBox.getBoundingBox(bPos, rot, BPos.ORIGIN, BlockMirror.NONE, size);
                int meetingX = (box.minX + box.maxX) / 2;
                int meetingZ = (box.minZ + box.maxZ) / 2;
                int meetingBiomeId = bs.getBiomeAtQuart(meetingX >> 2, 79, meetingZ >> 2);
                String meetingBiomeName = getBiomeName(meetingBiomeId);

                String reason = String.format("Village placement cancelled by Minecraft: Town meeting point ('%s') at block (%d, %d) sits in %s (id %d), which is invalid for %s village.",
                        template, meetingX, meetingZ, meetingBiomeName, meetingBiomeId, vType);
                return VillageInfo.notFound(worldSeed, version, rx, rz, cx, cz, reason);
            }

            if (generateLoot) {
                try {
                    gen.generateLoot(rand);
                } catch (Exception ignored) {}
            }

            return buildInfo(gen, worldSeed, version, rx, rz, cx, cz, vType,
                    rawChunkBiomeId, chunkBiomeName, he, extraNote);
        } else {
            // Pre-1.18 using OverworldTerrainGenerator
            OverworldBiomeSource obs = new OverworldBiomeSource(version, worldSeed);
            OverworldTerrainGenerator otg = new OverworldTerrainGenerator(obs);
            Village village = VillageConfig.getVillage(version);
            VillageGenerator gen = new VillageGenerator(version);

            int sampleQuartX = (cx << 2) + 2;
            int sampleQuartZ = (cz << 2) + 2;
            var biome = obs.getBiomeForNoiseGen(sampleQuartX, 0, sampleQuartZ);
            String biomeName = biome != null ? biome.getName() : "Unknown";
            int biomeId = biome != null ? biome.getId() : -1;

            boolean success = gen.generate(otg, cx, cz, rand);
            if (!success) {
                return VillageInfo.notFound(worldSeed, version, rx, rz, cx, cz,
                        String.format("Village generation failed at chunk (%d, %d) in biome %s.", cx, cz, biomeName));
            }

            if (generateLoot) {
                try {
                    gen.generateLoot(otg, rand);
                } catch (Exception ignored) {}
            }

            return buildInfo(gen, worldSeed, version, rx, rz, cx, cz, gen.getVillageType(),
                    biomeId, biomeName, null, extraNote);
        }
    }

    private static VillageInfo buildInfo(VillageGenerator gen, long worldSeed, MCVersion version,
                                         int rx, int rz, int cx, int cz, VillageType vType,
                                         int chunkBiomeId, String chunkBiomeName,
                                         SurfaceHeightEstimator he, String extraNote) {
        List<VillageGenerator.Piece> rawPieces = gen.getPieces();
        if (rawPieces.isEmpty()) {
            return VillageInfo.notFound(worldSeed, version, rx, rz, cx, cz, "No village pieces generated.");
        }

        VillageGenerator.Piece firstPiece = rawPieces.get(0);
        int townCenterX = (firstPiece.getBox().minX + firstPiece.getBox().maxX) / 2;
        int townCenterY = firstPiece.getBox().minY;
        int townCenterZ = (firstPiece.getBox().minZ + firstPiece.getBox().maxZ) / 2;
        BPos townCenter = new BPos(townCenterX, townCenterY, townCenterZ);

        int meetingBiomeId = chunkBiomeId;
        String meetingBiomeName = chunkBiomeName;
        if (version.isNewerOrEqualTo(MCVersion.v1_18)) {
            MultiNoiseBiomeSource bs = new MultiNoiseBiomeSource(worldSeed);
            meetingBiomeId = bs.getBiomeAtQuart(townCenterX >> 2, 79, townCenterZ >> 2);
            meetingBiomeName = getBiomeName(meetingBiomeId);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        int housesCount = 0;
        int enclosedCount = 0;
        int streetsCount = 0;
        int farmsCount = 0;
        int featuresCount = 0;

        Map<String, Integer> workstationCounts = new TreeMap<>();
        Map<String, Integer> pieceCounts = new TreeMap<>();
        List<VillageInfo.PieceDetail> pieceDetails = new ArrayList<>(rawPieces.size());
        List<VillageInfo.ChestDetail> chestDetails = new ArrayList<>();

        for (int i = 0; i < rawPieces.size(); i++) {
            VillageGenerator.Piece p = rawPieces.get(i);
            BlockBox b = p.getBox();

            minX = Math.min(minX, b.minX);
            minY = Math.min(minY, b.minY);
            minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX);
            maxY = Math.max(maxY, b.maxY);
            maxZ = Math.max(maxZ, b.maxZ);

            String name = p.getName();
            pieceCounts.put(name, pieceCounts.getOrDefault(name, 0) + 1);

            VillageInfo.PieceCategory category = categorizePiece(name);
            String profession = getProfession(name);

            if (category == VillageInfo.PieceCategory.RESIDENCE || category == VillageInfo.PieceCategory.WORKSHOP) {
                housesCount++;
                if (VillageSearcher.isEnclosedBuilding(name)) {
                    enclosedCount++;
                }
            } else if (category == VillageInfo.PieceCategory.STREET) {
                streetsCount++;
            } else if (category == VillageInfo.PieceCategory.FARM) {
                farmsCount++;
            } else if (category == VillageInfo.PieceCategory.FEATURE) {
                featuresCount++;
            }

            if (profession != null) {
                workstationCounts.put(profession, workstationCounts.getOrDefault(profession, 0) + 1);
            }

            int pCenterX = (b.minX + b.maxX) / 2;
            int pCenterZ = (b.minZ + b.maxZ) / 2;
            int groundY = he != null ? he.getHeight(pCenterX, pCenterZ) : b.minY;

            VillageInfo.PieceDetail detail = new VillageInfo.PieceDetail(
                    i, name, category, profession, b, p.getRotation(),
                    p.getPlacementBehaviour(), p.getDepth(), groundY
            );

            // Check chests in this piece
            List<LootTable> tables = VillageStructureLoot.STRUCTURE_LOOT.get(name);
            List<BPos> offsets = VillageStructureLoot.STRUCTURE_LOOT_OFFSETS.get(name);
            if (tables != null && offsets != null && !tables.isEmpty()) {
                for (int t = 0; t < tables.size(); t++) {
                    BPos offset = offsets.get(t);
                    BPos chestPos = p.getPos().add(VillageGenerator.Piece.getTransformedPos(offset, p.getRotation()));
                    LootTable lt = tables.get(t);
                    String ltName = (lt != null && lt != MCLootTables.NULL) ? "chests/village/" + name.substring(name.lastIndexOf('/') + 1) : "chests/empty";
                    VillageInfo.ChestDetail cd = new VillageInfo.ChestDetail(i, name, chestPos, ltName);
                    if (!p.getLoot().isEmpty()) {
                        cd.items.addAll(p.getLoot());
                    }
                    detail.chests.add(cd);
                    chestDetails.add(cd);
                }
            }

            pieceDetails.add(detail);
        }

        BlockBox overallBox = new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);

        return new VillageInfo(
                true, null, extraNote, worldSeed, version,
                rx, rz, cx, cz, vType,
                chunkBiomeId, chunkBiomeName,
                meetingBiomeId, meetingBiomeName,
                townCenter, overallBox,
                minY, maxY,
                rawPieces.size(), housesCount, enclosedCount,
                streetsCount, farmsCount, featuresCount,
                workstationCounts, pieceCounts,
                pieceDetails, chestDetails
        );
    }

    private static VillageInfo copyWithNote(VillageInfo original, String note) {
        return new VillageInfo(
                original.exists, original.rejectionReason, note,
                original.worldSeed, original.version,
                original.regionX, original.regionZ, original.chunkX, original.chunkZ,
                original.villageType, original.chunkCenterBiomeId, original.chunkCenterBiomeName,
                original.meetingPointBiomeId, original.meetingPointBiomeName,
                original.meetingPointCenter, original.overallBox,
                original.minSurfaceY, original.maxSurfaceY,
                original.totalPieces, original.housesCount, original.enclosedBuildingsCount,
                original.streetsCount, original.farmsCount, original.featuresCount,
                original.workstationCounts, original.pieceCounts,
                original.pieces, original.chests
        );
    }

    public static VillageType villageTypeFromBiomeId(int id) {
        return switch (id) {
            case 1, 129, 168 -> VillageType.PLAINS;
            case 2 -> VillageType.DESERT;
            case 35, 36, 163, 164 -> VillageType.SAVANNA;
            case 5 -> VillageType.TAIGA;
            case 12 -> VillageType.SNOWY;
            default -> null;
        };
    }

    public static VillageInfo.PieceCategory categorizePiece(String name) {
        if (name == null) return VillageInfo.PieceCategory.OTHER;
        String lower = name.toLowerCase();
        if (lower.contains("meeting_point") || lower.contains("town_center")) return VillageInfo.PieceCategory.MEETING_POINT;
        if (lower.contains("/streets/") || lower.contains("street")) return VillageInfo.PieceCategory.STREET;
        if (lower.contains("farm") || lower.contains("animal_pen")) return VillageInfo.PieceCategory.FARM;
        if (isWorkshop(lower)) return VillageInfo.PieceCategory.WORKSHOP;
        if (lower.contains("/houses/") || lower.contains("house")) return VillageInfo.PieceCategory.RESIDENCE;
        if (lower.startsWith("pile_") || lower.contains("/pile_") || lower.contains("patch_")
                || lower.equals("oak") || lower.contains("acacia") || lower.contains("spruce")
                || lower.contains("pine") || lower.contains("flower") || lower.contains("iron_golem")
                || lower.contains("cat_") || lower.contains("animals/") || lower.contains("lamp")) {
            return VillageInfo.PieceCategory.FEATURE;
        }
        return VillageInfo.PieceCategory.OTHER;
    }

    public static boolean isWorkshop(String lower) {
        return lower.contains("weaponsmith") || lower.contains("armorer") || lower.contains("toolsmith")
                || lower.contains("butcher") || lower.contains("cartographer") || lower.contains("library")
                || lower.contains("fletcher") || lower.contains("shepherd") || lower.contains("mason")
                || lower.contains("tannery") || lower.contains("fisher") || lower.contains("temple");
    }

    public static String getProfession(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        if (lower.contains("weaponsmith")) return "Weaponsmith";
        if (lower.contains("armorer")) return "Armorer";
        if (lower.contains("toolsmith")) return "Toolsmith";
        if (lower.contains("butcher")) return "Butcher";
        if (lower.contains("cartographer")) return "Cartographer";
        if (lower.contains("library")) return "Librarian";
        if (lower.contains("fletcher")) return "Fletcher";
        if (lower.contains("shepherd")) return "Shepherd";
        if (lower.contains("mason")) return "Mason";
        if (lower.contains("tannery")) return "Leatherworker";
        if (lower.contains("fisher")) return "Fisherman";
        if (lower.contains("temple")) return "Cleric";
        if (lower.contains("large_farm") || lower.contains("farm_")) return "Farmer";
        return null;
    }

    public static String getBiomeName(int id) {
        return switch (id) {
            case 0 -> "Ocean";
            case 1 -> "Plains";
            case 2 -> "Desert";
            case 3 -> "Windswept Hills";
            case 4 -> "Forest";
            case 5 -> "Taiga";
            case 6 -> "Swamp";
            case 7 -> "River";
            case 8 -> "Nether Wastes";
            case 9 -> "The End";
            case 10 -> "Frozen Ocean";
            case 11 -> "Frozen River";
            case 12 -> "Snowy Plains";
            case 14 -> "Mushroom Fields";
            case 16 -> "Beach";
            case 21 -> "Jungle";
            case 24 -> "Deep Ocean";
            case 25 -> "Stony Shore";
            case 26 -> "Snowy Beach";
            case 27 -> "Birch Forest";
            case 29 -> "Dark Forest";
            case 30 -> "Snowy Taiga";
            case 32 -> "Old Growth Pine Taiga";
            case 33 -> "Old Growth Spruce Taiga";
            case 34 -> "Windswept Forest";
            case 35 -> "Savanna";
            case 36 -> "Savanna Plateau";
            case 37 -> "Badlands";
            case 38 -> "Wooded Badlands";
            case 39 -> "Eroded Badlands";
            case 44 -> "Warm Ocean";
            case 45 -> "Lukewarm Ocean";
            case 46 -> "Cold Ocean";
            case 47 -> "Deep Warm Ocean";
            case 48 -> "Deep Lukewarm Ocean";
            case 49 -> "Deep Cold Ocean";
            case 50 -> "Deep Frozen Ocean";
            case 129 -> "Meadow / Sunflower Plains";
            case 163 -> "Windswept Savanna";
            case 164 -> "Windswept Savanna Plateau";
            case 168 -> "Meadow";
            case 169 -> "Cherry Grove";
            case 170 -> "Grove";
            case 171 -> "Snowy Slopes";
            case 172 -> "Frozen Peaks";
            case 173 -> "Jagged Peaks";
            case 174 -> "Stony Peaks";
            case 175 -> "Deep Dark";
            case 177 -> "Mangrove Swamp";
            case 178 -> "Sparse Jungle";
            case 179 -> "Windswept Savanna";
            case 180 -> "Old Growth Birch Forest";
            default -> "Biome_" + id;
        };
    }

    public static void main(String[] args) {
        if (args.length == 0 || hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage();
            return;
        }

        Long seed = null;
        Integer regionX = null, regionZ = null;
        Integer chunkX = null, chunkZ = null;
        Integer blockX = null, blockZ = null;
        MCVersion version = MCVersion.v1_21;
        VillageType typeOverride = null;
        boolean generateLoot = hasFlag(args, "--loot");
        boolean jsonOutput = hasFlag(args, "--json");
        boolean summaryOnly = hasFlag(args, "--summary");

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--seed".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg)) {
                seed = Long.parseLong(args[++i]);
            } else if ("--region".equalsIgnoreCase(arg) || "-r".equalsIgnoreCase(arg)) {
                regionX = Integer.parseInt(args[++i]);
                regionZ = Integer.parseInt(args[++i]);
            } else if ("--chunk".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                chunkX = Integer.parseInt(args[++i]);
                chunkZ = Integer.parseInt(args[++i]);
            } else if ("--block".equalsIgnoreCase(arg) || "-b".equalsIgnoreCase(arg)) {
                blockX = Integer.parseInt(args[++i]);
                blockZ = Integer.parseInt(args[++i]);
            } else if ("--version".equalsIgnoreCase(arg) || "-v".equalsIgnoreCase(arg)) {
                version = parseVersion(args[++i]);
            } else if ("--type".equalsIgnoreCase(arg) || "-t".equalsIgnoreCase(arg)) {
                typeOverride = VillageType.valueOf(args[++i].toUpperCase());
            }
        }

        if (seed == null) {
            System.err.println("Error: Missing required --seed <worldSeed> argument.");
            printUsage();
            System.exit(1);
        }

        VillageInfo info;
        if (chunkX != null && chunkZ != null) {
            info = (typeOverride != null)
                    ? simulateChunk(seed, chunkX, chunkZ, typeOverride, version, generateLoot)
                    : lookupChunk(seed, chunkX, chunkZ, version, generateLoot);
        } else if (regionX != null && regionZ != null) {
            info = lookupRegion(seed, regionX, regionZ, version, generateLoot);
        } else if (blockX != null && blockZ != null) {
            info = lookupBlock(seed, blockX, blockZ, version, generateLoot);
        } else {
            System.err.println("Error: Must specify one of --region <rx> <rz>, --chunk <cx> <cz>, or --block <bx> <bz>.");
            printUsage();
            System.exit(1);
            return;
        }

        if (jsonOutput) {
            System.out.println(info.toJson());
        } else if (summaryOnly) {
            System.out.println(info.getSummary());
        } else {
            System.out.println(info.getDetailedReport());
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) return true;
        }
        return false;
    }

    private static MCVersion parseVersion(String v) {
        String cleaned = v.toLowerCase().replace("v", "").replace(".", "_");
        for (MCVersion mv : MCVersion.values()) {
            if (mv.name().equalsIgnoreCase("v" + cleaned) || mv.name().equalsIgnoreCase(cleaned) || mv.toString().equalsIgnoreCase(v)) {
                return mv;
            }
        }
        return MCVersion.v1_21;
    }

    private static void printUsage() {
        System.out.println("VillageLookup - Comprehensive 3D Village Inspection Utility");
        System.out.println("Usage:");
        System.out.println("  java profotoce59.lookup.VillageLookup --seed <worldSeed> [target] [options]");
        System.out.println();
        System.out.println("Target (choose one):");
        System.out.println("  --chunk <cx> <cz>        Inspect village at or originating near chunk (cx, cz)");
        System.out.println("  --region <rx> <rz>       Inspect designated village for region (rx, rz)");
        System.out.println("  --block <bx> <bz>        Inspect village enclosing block coordinates (bx, bz)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version <v>            Minecraft version (e.g. 1.21, 1.20, 1.18, 1.16.5) [Default: 1.21]");
        System.out.println("  --type <type>            Override village type (PLAINS, DESERT, SAVANNA, TAIGA, SNOWY)");
        System.out.println("  --loot                   Simulate chunk loot decorators and list chest contents");
        System.out.println("  --summary                Output only high-level summary (omit pieces table)");
        System.out.println("  --json                   Output full results as JSON");
        System.out.println("  --help, -h               Show this help message");
    }
}
