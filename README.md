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
- Credit to **cubitect** for the **[Cubiomes](https://github.com/Cubitect/cubiomes)** project, and **xpple** for the **[cubiomes fork](https://github.com/xpple/cubiomes)** implementing 1.18+ terrain and structure references.
- Thanks to **[M33Galaxy](https://github.com/M33Galaxy)** and **[SunnySlopes](https://github.com/SunnySlopes)** for **[LowYSwampHut](https://github.com/M33Galaxy/LowYSwampHut)**, and **[KalleStruik](https://github.com/KalleStruik)** for **[noise-sampler](https://github.com/KalleStruik/noise-sampler)**, used as references for exact 1.18+ 3D terrain density and aquifer simulation.

## 1.18+ Exact Surface Height & Biome Sampling
Minecraft 1.18 introduced the 3D multi-noise terrain generator and `NoiseBasedAquifer`. `SurfaceHeightEstimator` (backed by `ExactHeightSampler` and `AquiferSampler`) reproduces vanilla's exact `WORLD_SURFACE_WG` column heights by evaluating 3D `final_density` (including `BlendedNoise`, `TerrainSpline`, cheese/spaghetti/pillar/entrance caves, and 3D-interpolated `noodle` caves) together with `NoiseBasedAquifer` fluid and pressure-barrier simulation. `MultiNoiseBiomeSource` provides 1.18+ multi-noise biome lookup for validating village biomes.

### Usage Example (1.18+)
```java
MCVersion version = MCVersion.v1_21;
long seed = 2264904L;

// 1. Create village structure and find candidate chunk
Village village = VillageConfig.getVillage(version);
ChunkRand rand = new ChunkRand();
CPos chunkPos = village.getInRegion(seed, 1, -1, rand);

// 2. Check 1.18+ village biome and setup exact surface height sampler
MultiNoiseBiomeSource biomeSource = new MultiNoiseBiomeSource(seed);
int biomeId = biomeSource.getViableVillageBiome(chunkPos.getX(), chunkPos.getZ());

VillageGenerator generator = new VillageGenerator(version);
SurfaceHeightEstimator heightEstimator = new SurfaceHeightEstimator(seed);
generator.setHeightEstimator(heightEstimator);

// 3. Generate village pieces and retrieve chest locations with loot seeds
generator.generate(seed, chunkPos.getX(), chunkPos.getZ(), rand, VillageType.DESERT);
List<Triplet<BPos, LootTable, Long>> chests = generator.getChestsWithLootSeeds(seed, version);
```

## Seed Searcher (`VillageSearcher`)
`VillageSearcher` provides a fast, multi-threaded search across structure seeds and world seeds:
- **Stage 1:** Fast 48-bit structure seed layout filtering 
- **Stage 2:** Upper 16-bit world seed verification with biome and exact 3D surface/aquifer heights.

```java
VillageSearcher.Filter filter = new VillageSearcher.Filter()
    .setVersion(MCVersion.v1_21)
    .setVillageTypes(VillageType.DESERT, VillageType.TAIGA, VillageType.PLAINS)
    .setRegionRadius(2)
    .setMinEnclosedBuildings(35)
    .requirePieceCount("cartographer", 2)
    .requirePieceCount("weaponsmith", 1);

new VillageSearcher(filter).search(1, 10000, 5, result -> {
    System.out.println(result);
    System.out.printf("  Cartographers: %d, Weaponsmiths: %d%n",
        result.countPiecesMatching("cartographer"),
        result.countPiecesMatching("weaponsmith"));
});
```

# Install Instructions
Add the following to your build.gradle repositories block
```    
    maven {url "https://jitpack.io"}
```
and the following to your build.gradle dependencies block:
```
    implementation('com.github.profotoce59:VillageGenerator:main-SNAPSHOT'){transitive=false}
```
