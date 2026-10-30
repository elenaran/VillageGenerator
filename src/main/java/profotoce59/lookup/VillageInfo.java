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
        FARM("Farm"),
        STREET("Street"),
        FEATURE("Feature"),
        OTHER("Other");

        public final String label;
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
            return String.format("Chest at (%d, %d, %d) in %s [%s]", pos.getX(), pos.getY(), pos.getZ(), pieceName,
                    items.isEmpty() ? (lootTableName != null ? lootTableName : "empty") : items.toString());
        }
    }

    public static class PieceDetail {
        public final int index;
        public final String name;
        public final PieceCategory category;
        public final String profession;
        public final BlockBox box;
        public final int centerX, centerY, centerZ;
        public final int sizeX, sizeY, sizeZ;
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

    public boolean exists;
    public String rejectionReason;

    public long worldSeed;
    public MCVersion version = MCVersion.v1_21;
    public int regionX, regionZ;
    public int chunkX, chunkZ;

    public VillageType villageType;
    public BPos meetingPointCenter;
    public BlockBox overallBox;

    public int totalPieces;
    public int housesCount;
    public int enclosedBuildingsCount;
    public int streetsCount;
    public int farmsCount;
    public int featuresCount;

    public final Map<String, Integer> workstationCounts = new TreeMap<>();
    public final List<PieceDetail> pieces = new ArrayList<>();
    public final List<ChestDetail> chests = new ArrayList<>();

    public boolean exists() { return exists; }
    public String getRejectionReason() { return rejectionReason; }

    public String getTeleportCommand() {
        return meetingPointCenter != null ? String.format("/tp @s %d %d %d",
                meetingPointCenter.getX(), meetingPointCenter.getY(), meetingPointCenter.getZ()) : null;
    }

    public String getSummary() {
        if (!exists) {
            return String.format("No village at chunk (%d, %d) in region (%d, %d): %s",
                    chunkX, chunkZ, regionX, regionZ, rejectionReason);
        }
        return String.format("%s Village at chunk (%d, %d) | Center: %s (%s) | %d pieces, %d houses (%d enclosed), %d chests",
                villageType, chunkX, chunkZ, meetingPointCenter, getTeleportCommand(),
                totalPieces, housesCount, enclosedBuildingsCount, chests.size());
    }

    public String getDetailedReport() {
        if (!exists) return getSummary();
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary()).append("\n");
        sb.append(String.format("Bounds: [%d, %d, %d] to [%d, %d, %d] (Span: %dx%dx%d blocks, Relief: Y %d to %d)\n",
                overallBox.minX, overallBox.minY, overallBox.minZ, overallBox.maxX, overallBox.maxY, overallBox.maxZ,
                overallBox.maxX - overallBox.minX + 1, overallBox.maxY - overallBox.minY + 1, overallBox.maxZ - overallBox.minZ + 1,
                overallBox.minY, overallBox.maxY));

        if (!workstationCounts.isEmpty()) {
            sb.append("Workstations: ").append(workstationCounts).append("\n");
        }
        if (!chests.isEmpty()) {
            sb.append("Chests (").append(chests.size()).append("):\n");
            for (ChestDetail c : chests) {
                sb.append(String.format("  - %s in %s: %s\n", c.getTeleportCommand(), c.pieceName,
                        c.items.isEmpty() ? c.lootTableName : c.items));
            }
        }
        sb.append("\nPieces (").append(pieces.size()).append("):\n");
        sb.append(getPiecesTable());
        return sb.toString();
    }

    public String getPiecesTable() {
        if (!exists || pieces.isEmpty()) return "No pieces generated.";
        StringBuilder sb = new StringBuilder();
        for (PieceDetail p : pieces) {
            sb.append(String.format("  #%-3d %-36s | %-12s | Y=%-3d (grnd %-3d) | %s\n",
                    p.index, p.name, p.category.label, p.centerY, p.groundSurfaceY, p.getTeleportCommand()));
        }
        return sb.toString();
    }

    public String getChestsReport() {
        if (chests.isEmpty()) return "No chests found in village.";
        StringBuilder sb = new StringBuilder();
        for (ChestDetail c : chests) {
            sb.append(String.format("  - Chest at %s in %s (%s)\n", c.getTeleportCommand(), c.pieceName, c.lootTableName));
        }
        return sb.toString();
    }

    public String toJson() {
        return String.format("{\"exists\": %b, \"type\": \"%s\", \"chunk\": [%d, %d], \"pieces\": %d}",
                exists, villageType, chunkX, chunkZ, totalPieces);
    }

    @Override
    public String toString() {
        return getDetailedReport();
    }
}
