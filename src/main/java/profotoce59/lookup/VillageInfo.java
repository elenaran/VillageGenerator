package profotoce59.lookup;

import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import profotoce59.enumType.VillageType;
import profotoce59.properties.VillageGenerator;

import java.util.*;

public class VillageInfo {

    public enum PieceCategory {
        MEETING_POINT("Meeting Point"),
        WORKSHOP("Workstation"),
        RESIDENCE("Residence"),
        FARM("Farm / Pen"),
        STREET("Street / Path"),
        FEATURE("Decor / Feature"),
        OTHER("Other");

        private final String label;
        PieceCategory(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static class ChestDetail {
        public final int pieceIndex;
        public final String pieceName;
        public final BPos pos;
        public final String lootTableName;
        public final List<ItemStack> items = new ArrayList<>();

        public ChestDetail(int pieceIndex, String pieceName, BPos pos, String lootTableName) {
            this.pieceIndex = pieceIndex;
            this.pieceName = pieceName;
            this.pos = pos;
            this.lootTableName = lootTableName;
        }

        public String getTeleportCommand() {
            return String.format("/tp @s %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public String toString() {
            return String.format("Chest at (%d, %d, %d) [%s] - %s",
                    pos.getX(), pos.getY(), pos.getZ(), pieceName,
                    items.isEmpty() ? (lootTableName != null ? lootTableName : "empty") : items.toString());
        }
    }

    public static class PieceDetail {
        public final int index;
        public final String name;
        public final PieceCategory category;
        public final String profession;
        public final BlockBox box;
        public final int centerX;
        public final int centerY;
        public final int centerZ;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        public final BlockRotation rotation;
        public final VillageGenerator.PlacementBehaviour placementBehaviour;
        public final int depth;
        public final int groundSurfaceY;
        public final int deltaYFromGround;
        public final List<ChestDetail> chests = new ArrayList<>();

        public PieceDetail(int index, String name, PieceCategory category, String profession,
                           BlockBox box, BlockRotation rotation, VillageGenerator.PlacementBehaviour placementBehaviour,
                           int depth, int groundSurfaceY) {
            this.index = index;
            this.name = name;
            this.category = category;
            this.profession = profession;
            this.box = box;
            this.centerX = (box.minX + box.maxX) / 2;
            this.centerY = box.minY;
            this.centerZ = (box.minZ + box.maxZ) / 2;
            this.sizeX = box.maxX - box.minX + 1;
            this.sizeY = box.maxY - box.minY + 1;
            this.sizeZ = box.maxZ - box.minZ + 1;
            this.rotation = rotation;
            this.placementBehaviour = placementBehaviour;
            this.depth = depth;
            this.groundSurfaceY = groundSurfaceY;
            this.deltaYFromGround = this.centerY - groundSurfaceY;
        }

        public String getTeleportCommand() {
            return String.format("/tp @s %d %d %d", centerX, centerY, centerZ);
        }
    }

    public final boolean exists;
    public final String rejectionReason;
    public final String note;

    public final long worldSeed;
    public final long structureSeed;
    public final MCVersion version;

    public final int regionX;
    public final int regionZ;
    public final int chunkX;
    public final int chunkZ;
    public final int chunkCenterBlockX;
    public final int chunkCenterBlockZ;

    public final VillageType villageType;
    public final int chunkCenterBiomeId;
    public final String chunkCenterBiomeName;
    public final int meetingPointBiomeId;
    public final String meetingPointBiomeName;

    public final BPos meetingPointCenter;
    public final BlockBox overallBox;
    public final int minSurfaceY;
    public final int maxSurfaceY;

    public final int totalPieces;
    public final int housesCount;
    public final int enclosedBuildingsCount;
    public final int streetsCount;
    public final int farmsCount;
    public final int featuresCount;

    public final Map<String, Integer> workstationCounts;
    public final Map<String, Integer> pieceCounts;
    public final List<PieceDetail> pieces;
    public final List<ChestDetail> chests;

    public VillageInfo(boolean exists, String rejectionReason, String note,
                       long worldSeed, MCVersion version,
                       int regionX, int regionZ, int chunkX, int chunkZ,
                       VillageType villageType,
                       int chunkCenterBiomeId, String chunkCenterBiomeName,
                       int meetingPointBiomeId, String meetingPointBiomeName,
                       BPos meetingPointCenter, BlockBox overallBox,
                       int minSurfaceY, int maxSurfaceY,
                       int totalPieces, int housesCount, int enclosedBuildingsCount,
                       int streetsCount, int farmsCount, int featuresCount,
                       Map<String, Integer> workstationCounts,
                       Map<String, Integer> pieceCounts,
                       List<PieceDetail> pieces,
                       List<ChestDetail> chests) {
        this.exists = exists;
        this.rejectionReason = rejectionReason;
        this.note = note;
        this.worldSeed = worldSeed;
        this.structureSeed = worldSeed & 0xFFFF_FFFF_FFFFL;
        this.version = version;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunkCenterBlockX = (chunkX << 4) + 8;
        this.chunkCenterBlockZ = (chunkZ << 4) + 8;
        this.villageType = villageType;
        this.chunkCenterBiomeId = chunkCenterBiomeId;
        this.chunkCenterBiomeName = chunkCenterBiomeName;
        this.meetingPointBiomeId = meetingPointBiomeId;
        this.meetingPointBiomeName = meetingPointBiomeName;
        this.meetingPointCenter = meetingPointCenter;
        this.overallBox = overallBox;
        this.minSurfaceY = minSurfaceY;
        this.maxSurfaceY = maxSurfaceY;
        this.totalPieces = totalPieces;
        this.housesCount = housesCount;
        this.enclosedBuildingsCount = enclosedBuildingsCount;
        this.streetsCount = streetsCount;
        this.farmsCount = farmsCount;
        this.featuresCount = featuresCount;
        this.workstationCounts = workstationCounts != null ? Collections.unmodifiableMap(workstationCounts) : Collections.emptyMap();
        this.pieceCounts = pieceCounts != null ? Collections.unmodifiableMap(pieceCounts) : Collections.emptyMap();
        this.pieces = pieces != null ? Collections.unmodifiableList(pieces) : Collections.emptyList();
        this.chests = chests != null ? Collections.unmodifiableList(chests) : Collections.emptyList();
    }

    public static VillageInfo notFound(long worldSeed, MCVersion version, int rx, int rz, int cx, int cz, String reason) {
        return new VillageInfo(false, reason, null, worldSeed, version, rx, rz, cx, cz,
                null, -1, "Unknown", -1, "Unknown",
                null, null, 0, 0,
                0, 0, 0, 0, 0, 0,
                null, null, null, null);
    }

    public boolean exists() { return exists; }
    public String getRejectionReason() { return rejectionReason; }
    public String getNote() { return note; }

    public String getTeleportCommand() {
        if (!exists || meetingPointCenter == null) return null;
        return String.format("/tp @s %d %d %d", meetingPointCenter.getX(), meetingPointCenter.getY(), meetingPointCenter.getZ());
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Village Lookup Result for Seed %d (%s):\n", worldSeed, version.name()));
        sb.append(String.format("Status: %s\n", exists ? "VALID (Village Spawns)" : "NOT PRESENT / CANCELLED"));
        if (!exists) {
            sb.append(String.format("Reason: %s\n", rejectionReason));
            sb.append(String.format("Region: (%d, %d) | Chunk: (%d, %d) | Block center: (%d, %d)\n",
                    regionX, regionZ, chunkX, chunkZ, chunkCenterBlockX, chunkCenterBlockZ));
            return sb.toString();
        }

        if (note != null && !note.isEmpty()) {
            sb.append(String.format("Note: %s\n", note));
        }

        sb.append(String.format("Type: %s Village | Biome: %s (id %d)\n",
                villageType, chunkCenterBiomeName, chunkCenterBiomeId));
        sb.append(String.format("Region: (%d, %d) | Chunk: (%d, %d)\n", regionX, regionZ, chunkX, chunkZ));
        sb.append(String.format("Town Center: (%d, %d, %d) -> %s\n",
                meetingPointCenter.getX(), meetingPointCenter.getY(), meetingPointCenter.getZ(), getTeleportCommand()));
        sb.append(String.format("3D Bounds: [%d, %d, %d] to [%d, %d, %d] (Span: %dx%dx%d blocks, Relief: Y %d to %d)\n",
                overallBox.minX, overallBox.minY, overallBox.minZ,
                overallBox.maxX, overallBox.maxY, overallBox.maxZ,
                overallBox.maxX - overallBox.minX + 1, overallBox.maxY - overallBox.minY + 1, overallBox.maxZ - overallBox.minZ + 1,
                overallBox.minY, overallBox.maxY));
        sb.append(String.format("Composition: %d Pieces | %d Houses (%d Enclosed) | %d Streets | %d Farms | %d Decor | %d Chests\n",
                totalPieces, housesCount, enclosedBuildingsCount, streetsCount, farmsCount, featuresCount, chests.size()));

        if (!workstationCounts.isEmpty()) {
            List<String> wsList = new ArrayList<>();
            for (Map.Entry<String, Integer> e : workstationCounts.entrySet()) {
                wsList.add(e.getKey() + ": " + e.getValue());
            }
            sb.append("Professions / Workstations: ").append(String.join(", ", wsList)).append("\n");
        }
        return sb.toString();
    }

    public String getPiecesTable() {
        if (!exists || pieces.isEmpty()) return "No pieces generated.";
        StringBuilder sb = new StringBuilder();
        String format = "| %3d | %-38s | %-13s | %-10s | %18s | %6d | %+7d | %-24s |\n";
        String border = "+-----+----------------------------------------+---------------+------------+--------------------+--------+---------+--------------------------+\n";

        sb.append(border);
        sb.append(String.format("| %-3s | %-38s | %-13s | %-10s | %-18s | %-6s | %-7s | %-24s |\n",
                "#", "Piece Name", "Category", "Size (WxHxL)", "Center (X, Y, Z)", "Grnd Y", "Delta Y", "Teleport Command"));
        sb.append(border);

        for (PieceDetail p : pieces) {
            String size = String.format("%2dx%2dx%2d", p.sizeX, p.sizeY, p.sizeZ);
            String center = String.format("%d, %d, %d", p.centerX, p.centerY, p.centerZ);
            sb.append(String.format(format,
                    p.index,
                    truncate(p.name, 38),
                    p.category.getLabel(),
                    size,
                    center,
                    p.groundSurfaceY,
                    p.deltaYFromGround,
                    p.getTeleportCommand()));
        }
        sb.append(border);
        return sb.toString();
    }

    public String getChestsReport() {
        if (!exists) return "No village exists.";
        if (chests.isEmpty()) return "No chests found in this village.";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d Chest(s) in Village:\n", chests.size()));
        for (int i = 0; i < chests.size(); i++) {
            ChestDetail c = chests.get(i);
            sb.append(String.format("  [%d] (%d, %d, %d) in piece #%d '%s'\n",
                    i + 1, c.pos.getX(), c.pos.getY(), c.pos.getZ(), c.pieceIndex, c.pieceName));
            sb.append(String.format("      Loot Table: %s | Teleport: %s\n",
                    c.lootTableName != null ? c.lootTableName : "None", c.getTeleportCommand()));
            if (!c.items.isEmpty()) {
                List<String> itemStrs = new ArrayList<>();
                for (ItemStack it : c.items) {
                    itemStrs.add(it.getCount() + "x " + it.getItem().getName());
                }
                sb.append(String.format("      Items: %s\n", String.join(", ", itemStrs)));
            }
        }
        return sb.toString();
    }

    public String getDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================================\n");
        sb.append("                                  VILLAGE 3D REAL-TERRAIN AUDIT REPORT                                 \n");
        sb.append("========================================================================================================\n");
        sb.append(getSummary());
        sb.append("\n");
        if (exists) {
            sb.append("CHESTS & LOOT:\n");
            sb.append(getChestsReport());
            sb.append("\n");
            sb.append("ALL 3D VILLAGE PIECES:\n");
            sb.append(getPiecesTable());
        }
        sb.append("========================================================================================================\n");
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"exists\": ").append(exists).append(",\n");
        if (!exists) {
            sb.append("  \"rejectionReason\": \"").append(escapeJson(rejectionReason)).append("\",\n");
        }
        if (note != null) {
            sb.append("  \"note\": \"").append(escapeJson(note)).append("\",\n");
        }
        sb.append("  \"worldSeed\": ").append(worldSeed).append(",\n");
        sb.append("  \"structureSeed\": ").append(structureSeed).append(",\n");
        sb.append("  \"version\": \"").append(version.name()).append("\",\n");
        sb.append("  \"region\": [").append(regionX).append(", ").append(regionZ).append("],\n");
        sb.append("  \"chunk\": [").append(chunkX).append(", ").append(chunkZ).append("],\n");
        sb.append("  \"chunkCenterBlock\": [").append(chunkCenterBlockX).append(", ").append(chunkCenterBlockZ).append("],\n");

        if (exists) {
            sb.append("  \"villageType\": \"").append(villageType).append("\",\n");
            sb.append("  \"biome\": {\"id\": ").append(chunkCenterBiomeId).append(", \"name\": \"").append(escapeJson(chunkCenterBiomeName)).append("\"},\n");
            sb.append("  \"meetingPointBiome\": {\"id\": ").append(meetingPointBiomeId).append(", \"name\": \"").append(escapeJson(meetingPointBiomeName)).append("\"},\n");
            sb.append("  \"townCenter\": {\"x\": ").append(meetingPointCenter.getX()).append(", \"y\": ").append(meetingPointCenter.getY()).append(", \"z\": ").append(meetingPointCenter.getZ()).append("},\n");
            sb.append("  \"teleportCommand\": \"").append(getTeleportCommand()).append("\",\n");
            sb.append("  \"bounds\": {\"minX\": ").append(overallBox.minX).append(", \"minY\": ").append(overallBox.minY).append(", \"minZ\": ").append(overallBox.minZ)
                    .append(", \"maxX\": ").append(overallBox.maxX).append(", \"maxY\": ").append(overallBox.maxY).append(", \"maxZ\": ").append(overallBox.maxZ).append("},\n");
            sb.append("  \"totalPieces\": ").append(totalPieces).append(",\n");
            sb.append("  \"housesCount\": ").append(housesCount).append(",\n");
            sb.append("  \"enclosedBuildingsCount\": ").append(enclosedBuildingsCount).append(",\n");
            sb.append("  \"streetsCount\": ").append(streetsCount).append(",\n");
            sb.append("  \"farmsCount\": ").append(farmsCount).append(",\n");
            sb.append("  \"featuresCount\": ").append(featuresCount).append(",\n");

            sb.append("  \"workstations\": {");
            int wCount = 0;
            for (Map.Entry<String, Integer> e : workstationCounts.entrySet()) {
                if (wCount++ > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(e.getKey())).append("\": ").append(e.getValue());
            }
            sb.append("},\n");

            sb.append("  \"chests\": [\n");
            for (int i = 0; i < chests.size(); i++) {
                ChestDetail c = chests.get(i);
                sb.append("    {\"pieceIndex\": ").append(c.pieceIndex)
                        .append(", \"pieceName\": \"").append(escapeJson(c.pieceName)).append("\"")
                        .append(", \"pos\": [").append(c.pos.getX()).append(", ").append(c.pos.getY()).append(", ").append(c.pos.getZ()).append("]")
                        .append(", \"lootTable\": \"").append(escapeJson(c.lootTableName)).append("\"")
                        .append(", \"tp\": \"").append(c.getTeleportCommand()).append("\"}");
                if (i < chests.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            sb.append("  \"pieces\": [\n");
            for (int i = 0; i < pieces.size(); i++) {
                PieceDetail p = pieces.get(i);
                sb.append("    {\"index\": ").append(p.index)
                        .append(", \"name\": \"").append(escapeJson(p.name)).append("\"")
                        .append(", \"category\": \"").append(p.category.name()).append("\"")
                        .append(", \"profession\": ").append(p.profession != null ? "\"" + escapeJson(p.profession) + "\"" : "null")
                        .append(", \"box\": [").append(p.box.minX).append(", ").append(p.box.minY).append(", ").append(p.box.minZ).append(", ")
                        .append(p.box.maxX).append(", ").append(p.box.maxY).append(", ").append(p.box.maxZ).append("]")
                        .append(", \"center\": [").append(p.centerX).append(", ").append(p.centerY).append(", ").append(p.centerZ).append("]")
                        .append(", \"groundY\": ").append(p.groundSurfaceY)
                        .append(", \"deltaY\": ").append(p.deltaYFromGround)
                        .append(", \"tp\": \"").append(p.getTeleportCommand()).append("\"}");
                if (i < pieces.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
        } else {
            sb.append("  \"pieces\": []\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() <= max ? str : str.substring(0, max - 3) + "...";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
