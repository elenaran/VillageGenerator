package profotoce59.lookup;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockBox;
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
        CPos start = village.getInRegion(worldSeed, rx, rz, new ChunkRand());
        if (start == null) {
            VillageInfo info = new VillageInfo();
            info.worldSeed = worldSeed;
            info.version = version;
            info.regionX = rx;
            info.regionZ = rz;
            info.rejectionReason = "Could not find village in region";
            return info;
        }
        return evaluate(worldSeed, rx, rz, start.getX(), start.getZ(), null, version, generateLoot);
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
        CPos designated = village.getInRegion(worldSeed, rx, rz, new ChunkRand());

        if (designated == null || designated.getX() != cx || designated.getZ() != cz) {
            VillageInfo info = new VillageInfo();
            info.worldSeed = worldSeed;
            info.version = version;
            info.regionX = rx;
            info.regionZ = rz;
            info.chunkX = cx;
            info.chunkZ = cz;
            info.rejectionReason = String.format("No village starts at chunk (%d, %d). For region (%d, %d), start chunk is (%d, %d)",
                    cx, cz, rx, rz, designated != null ? designated.getX() : 0, designated != null ? designated.getZ() : 0);
            return info;
        }

        return evaluate(worldSeed, rx, rz, cx, cz, null, version, generateLoot);
    }

    public static VillageInfo lookupBlock(long worldSeed, int bx, int bz) {
        return lookupChunk(worldSeed, bx >> 4, bz >> 4, MCVersion.v1_21, false);
    }

    public static VillageInfo lookupBlock(long worldSeed, int bx, int bz, MCVersion version) {
        return lookupChunk(worldSeed, bx >> 4, bz >> 4, version, false);
    }

    public static VillageInfo lookupBlock(long worldSeed, int bx, int bz, MCVersion version, boolean generateLoot) {
        return lookupChunk(worldSeed, bx >> 4, bz >> 4, version, generateLoot);
    }

    public static VillageInfo simulateChunk(long worldSeed, int cx, int cz, VillageType type, MCVersion version, boolean generateLoot) {
        int spacing = version.isNewerOrEqualTo(MCVersion.v1_18) ? VillageConfig.CONFIG_1_18.spacing : 32;
        return evaluate(worldSeed, Math.floorDiv(cx, spacing), Math.floorDiv(cz, spacing), cx, cz, type, version, generateLoot);
    }

    private static VillageInfo evaluate(long worldSeed, int rx, int rz, int cx, int cz,
                                        VillageType typeOverride, MCVersion version, boolean generateLoot) {
        VillageInfo info = new VillageInfo();
        info.worldSeed = worldSeed;
        info.version = version;
        info.regionX = rx;
        info.regionZ = rz;
        info.chunkX = cx;
        info.chunkZ = cz;

        ChunkRand rand = new ChunkRand();

        if (version.isNewerOrEqualTo(MCVersion.v1_18)) {
            MultiNoiseBiomeSource bs = new MultiNoiseBiomeSource(worldSeed);
            int biomeId = bs.getViableVillageBiome(cx, cz);
            VillageType type = typeOverride != null ? typeOverride : villageTypeFromBiomeId(biomeId);

            if (type == null) {
                int rawId = bs.getBiomeAtQuart(cx * 4 + 2, 79, cz * 4 + 2);
                info.rejectionReason = String.format("Invalid village biome: %s (id %d)", getBiomeName(rawId), rawId);
                return info;
            }

            SurfaceHeightEstimator he = new SurfaceHeightEstimator(worldSeed);
            VillageGenerator gen = new VillageGenerator(version);
            gen.setHeightEstimator(he);

            if (!gen.generate(worldSeed, cx, cz, rand, type)) {
                int meetingX = (cx << 4) + 8, meetingZ = (cz << 4) + 8;
                int meetingId = bs.getBiomeAtQuart(meetingX >> 2, 79, meetingZ >> 2);
                info.rejectionReason = String.format("Town meeting point in invalid biome: %s (id %d)", getBiomeName(meetingId), meetingId);
                return info;
            }

            if (generateLoot) {
                try { gen.generateLoot(rand); } catch (Exception ignored) {}
            }
            populate(info, gen, type, he);
        } else {
            OverworldBiomeSource obs = new OverworldBiomeSource(version, worldSeed);
            OverworldTerrainGenerator otg = new OverworldTerrainGenerator(obs);
            VillageGenerator gen = new VillageGenerator(version);

            if (!gen.generate(otg, cx, cz, rand)) {
                info.rejectionReason = "Village generation failed in biome";
                return info;
            }

            if (generateLoot) {
                try { gen.generateLoot(otg, rand); } catch (Exception ignored) {}
            }
            populate(info, gen, gen.getVillageType(), null);
        }
        return info;
    }

    private static void populate(VillageInfo info, VillageGenerator gen, VillageType type, SurfaceHeightEstimator he) {
        List<VillageGenerator.Piece> pieces = gen.getPieces();
        if (pieces.isEmpty()) {
            info.rejectionReason = "No pieces generated";
            return;
        }

        info.exists = true;
        info.villageType = type;
        info.totalPieces = pieces.size();

        VillageGenerator.Piece p0 = pieces.get(0);
        info.meetingPointCenter = new BPos((p0.getBox().minX + p0.getBox().maxX) / 2, p0.getBox().minY, (p0.getBox().minZ + p0.getBox().maxZ) / 2);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (int i = 0; i < pieces.size(); i++) {
            VillageGenerator.Piece p = pieces.get(i);
            BlockBox b = p.getBox();

            minX = Math.min(minX, b.minX); minY = Math.min(minY, b.minY); minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX); maxY = Math.max(maxY, b.maxY); maxZ = Math.max(maxZ, b.maxZ);

            String name = p.getName();
            VillageInfo.PieceCategory cat = categorizePiece(name);
            String prof = getProfession(name);

            if (cat == VillageInfo.PieceCategory.RESIDENCE || cat == VillageInfo.PieceCategory.WORKSHOP) {
                info.housesCount++;
                if (VillageSearcher.isEnclosedBuilding(name)) info.enclosedBuildingsCount++;
            } else if (cat == VillageInfo.PieceCategory.STREET) {
                info.streetsCount++;
            } else if (cat == VillageInfo.PieceCategory.FARM) {
                info.farmsCount++;
            } else if (cat == VillageInfo.PieceCategory.FEATURE) {
                info.featuresCount++;
            }

            if (prof != null) {
                info.workstationCounts.put(prof, info.workstationCounts.getOrDefault(prof, 0) + 1);
            }

            int cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
            int groundY = he != null ? he.getHeight(cx, cz) : b.minY;
            VillageInfo.PieceDetail pd = new VillageInfo.PieceDetail(i, name, cat, prof, b, p.getRotation(), p.getPlacementBehaviour(), p.getDepth(), groundY);

            List<LootTable> tables = VillageStructureLoot.STRUCTURE_LOOT.get(name);
            List<BPos> offsets = VillageStructureLoot.STRUCTURE_LOOT_OFFSETS.get(name);
            if (tables != null && offsets != null) {
                for (int t = 0; t < tables.size(); t++) {
                    BPos cpos = p.getPos().add(VillageGenerator.Piece.getTransformedPos(offsets.get(t), p.getRotation()));
                    String lt = tables.get(t) != null && tables.get(t) != MCLootTables.NULL ? "chests/village" : "empty";
                    VillageInfo.ChestDetail cd = new VillageInfo.ChestDetail(i, name, cpos, lt);
                    if (!p.getLoot().isEmpty()) cd.items.addAll(p.getLoot());
                    pd.chests.add(cd);
                    info.chests.add(cd);
                }
            }
            info.pieces.add(pd);
        }
        info.overallBox = new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VillageType villageTypeFromBiomeId(int id) {
        return switch (id) {
            case 1, 129, 168       -> VillageType.PLAINS;
            case 2                 -> VillageType.DESERT;
            case 35, 36, 163, 164  -> VillageType.SAVANNA;
            case 5                 -> VillageType.TAIGA;
            case 12                -> VillageType.SNOWY;
            default                -> null;
        };
    }

    public static VillageInfo.PieceCategory categorizePiece(String name) {
        String s = name.toLowerCase();
        if (s.contains("meeting_point") || s.contains("town_center")) return VillageInfo.PieceCategory.MEETING_POINT;
        if (s.contains("/streets/")) return VillageInfo.PieceCategory.STREET;
        if (s.contains("farm") || s.contains("animal_pen")) return VillageInfo.PieceCategory.FARM;
        if (getProfession(name) != null) return VillageInfo.PieceCategory.WORKSHOP;
        if (s.contains("/houses/")) return VillageInfo.PieceCategory.RESIDENCE;
        return VillageInfo.PieceCategory.FEATURE;
    }

    public static String getProfession(String name) {
        String s = name.toLowerCase();
        if (s.contains("weaponsmith")) return "Weaponsmith";
        if (s.contains("armorer")) return "Armorer";
        if (s.contains("toolsmith")) return "Toolsmith";
        if (s.contains("butcher")) return "Butcher";
        if (s.contains("cartographer")) return "Cartographer";
        if (s.contains("library")) return "Librarian";
        if (s.contains("fletcher")) return "Fletcher";
        if (s.contains("shepherd")) return "Shepherd";
        if (s.contains("mason")) return "Mason";
        if (s.contains("tannery")) return "Leatherworker";
        if (s.contains("fisher")) return "Fisherman";
        if (s.contains("temple")) return "Cleric";
        return null;
    }

    public static String getBiomeName(int id) {
        return switch (id) {
            case 1 -> "Plains";
            case 2 -> "Desert";
            case 5 -> "Taiga";
            case 7 -> "River";
            case 12 -> "Snowy Plains";
            case 35 -> "Savanna";
            case 36 -> "Savanna Plateau";
            case 129 -> "Sunflower Plains";
            case 163 -> "Windswept Savanna";
            case 164 -> "Windswept Savanna Plateau";
            case 168 -> "Meadow";
            default -> "Biome_" + id;
        };
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: VillageLookup --seed <worldSeed> [--chunk <cx> <cz> | --region <rx> <rz>] [--loot]");
            return;
        }
        long seed = 0;
        Integer cx = null, cz = null, rx = null, rz = null;
        boolean loot = false;

        for (int i = 0; i < args.length; i++) {
            if ("--seed".equalsIgnoreCase(args[i]) && i + 1 < args.length) seed = Long.parseLong(args[++i]);
            else if ("--chunk".equalsIgnoreCase(args[i]) && i + 2 < args.length) { cx = Integer.parseInt(args[++i]); cz = Integer.parseInt(args[++i]); }
            else if ("--region".equalsIgnoreCase(args[i]) && i + 2 < args.length) { rx = Integer.parseInt(args[++i]); rz = Integer.parseInt(args[++i]); }
            else if ("--loot".equalsIgnoreCase(args[i])) loot = true;
        }

        VillageInfo info = cx != null ? lookupChunk(seed, cx, cz, MCVersion.v1_21, loot)
                : rx != null ? lookupRegion(seed, rx, rz, MCVersion.v1_21, loot) : null;

        if (info != null) System.out.println(info.getDetailedReport());
    }
}
