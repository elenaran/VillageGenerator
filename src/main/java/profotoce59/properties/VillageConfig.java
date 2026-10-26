package profotoce59.properties;

import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcfeature.structure.Village;

public class VillageConfig {
    public static final RegionStructure.Config CONFIG_1_18 = new RegionStructure.Config(34, 8, 10387312);

    public static Village getVillage(MCVersion version) {
        if (version.isNewerOrEqualTo(MCVersion.v1_18)) {
            return new Village(CONFIG_1_18, version);
        }
        return new Village(version);
    }
}
