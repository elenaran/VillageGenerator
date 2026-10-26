# VillageGenerator
Simulate the generation of villages in Minecraft.  
Support for every type of village (Plains, Desert, Savanna, Taiga, Snowy).  
- **Versions supported for piece generation:** 1.14 – 1.21+  
- **Versions supported for chest loot & loot seeds:** 1.16 – 1.21+  
*(Village structure pools, region spacing, and placement rules have remained consistent since 1.18, so modern and future releases follow the same pipeline unless Mojang alters village generation).*

## Credits
- Original project by **profotoce59**.
- Based on libraries and code by **KaptainWutax**, **Mojang**, and **Neil**.
- Thanks to **Solver3002** and **Jellejurre** for their contributions to earlier versions.
- Thanks to **Kludwisz** and **Gaider10** for the **[AncientCityGenerator](https://github.com/Kludwisz/AncientCityGenerator)** project and reference implementation.
- Credit to **cubitect** for the **[Cubiomes](https://github.com/Cubitect/cubiomes)** project, and **xpple** for the **[cubiomes fork](https://github.com/xpple/cubiomes)** implementing 1.18+ village pieces and surface height estimation reference.

## 1.18+ Surface Height Estimation
Minecraft 1.18 introduced the 3D multi-noise terrain generator. Full 3D column density simulation is slow, so `SurfaceHeightEstimator` provides an estimation based on preliminary surface levels (using continentalness, erosion, peaks & valleys, and cubic splines), inspired by Cubiomes.

### Usage Example (1.18+)
```java
MCVersion version = MCVersion.v1_21;
long seed = 12345L;

// 1. Create village structure and find candidate chunk
Village village = VillageConfig.getVillage(version);
ChunkRand rand = new ChunkRand();
CPos chunkPos = village.getInRegion(seed, 0, 0, rand);

// 2. Setup VillageGenerator with the SurfaceHeightEstimator
VillageGenerator generator = new VillageGenerator(version);
SurfaceHeightEstimator heightEstimator = new SurfaceHeightEstimator(seed);
generator.setHeightEstimator(heightEstimator);

// 3. Generate village pieces
OverworldBiomeSource biomeSource = new OverworldBiomeSource(version, seed);
// ... generate and retrieve chest locations with loot seeds
List<Triplet<BPos, LootTable, Long>> chests = generator.getChestsWithLootSeeds(seed, version);
```

### Limitations
- `SurfaceHeightEstimator` computes preliminary surface height. On typical rolling terrain, plains, deserts, and plateaus, it matches closely. However, it does not simulate 3D caves, overhangs, floating terrain, or local surface carvers.
- On extreme mountain overhangs or steep cliffs, estimated surface heights may differ by a few blocks from the in-game terrain, which can occasionally alter piece attachment heights in complex village layouts.

# Install Instructions
Add the following to your build.gradle repositories block
```    
    maven {url "https://jitpack.io"}
```
and the following to your build.gradle dependencies block:
```
    implementation('com.github.profotoce59:VillageGenerator:main-SNAPSHOT'){transitive=false}
```
