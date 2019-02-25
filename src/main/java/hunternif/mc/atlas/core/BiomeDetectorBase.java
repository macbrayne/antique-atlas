package hunternif.mc.atlas.core;

import hunternif.mc.atlas.ext.ExtTileIdMap;
import hunternif.mc.atlas.util.ByteUtil;
import net.minecraft.block.Block;

import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ViewableWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.Heightmap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Detects the 256 vanilla biomes, water pools and lava pools.
 * Water and beach biomes are given priority because shore line is the defining
 * feature of the map, and so that rivers are more connected.
 * @author Hunternif
 */
public class BiomeDetectorBase implements IBiomeDetector {
	private boolean doScanPonds = true;
	private boolean doScanRavines = true;

	/** Biome used for occasional pools of water. */
	private static final Biome waterPoolBiome = null; // TODO FABRIC
	/** Increment the counter for water biomes by this much during iteration.
	 * This is done so that water pools are more visible. */
	private static final int priorityRavine = 12, priorityWaterPool = 3, prioritylavaPool = 6;

	/** Minimum depth in the ground to be considered a ravine */
	private static final int ravineMinDepth = 7;

	/** Set to true for biome IDs that return true for BiomeDictionary.isBiomeOfType(WATER) */
	private static final Set<Biome> waterBiomes = new HashSet<>();
	/** Set to true for biome IDs that return true for BiomeDictionary.isBiomeOfType(BEACH) */
	private static final Set<Biome> beachBiomes = new HashSet<>();

	private static final Set<Biome> swampBiomes = new HashSet<>();

	/** Scan all registered biomes to mark biomes of certain types that will be
	 * given higher priority when identifying mean biome ID for a chunk.
	 * (Currently WATER, BEACH and SWAMP) */
	public static void scanBiomeTypes() {
		for (Biome biome : Registry.BIOME) {
			switch (biome.getCategory()) {
				case BEACH:
					beachBiomes.add(biome);
					break;
				case OCEAN:
					waterBiomes.add(biome);
					break;
				case SWAMP:
					swampBiomes.add(biome);
					break;
			}
		}
	}

	public void setScanPonds(boolean value) {
		this.doScanPonds = value;
	}

	public void setScanRavines(boolean value) {
		this.doScanRavines = value;
	}

	int priorityForBiome(Biome biome) {
		if (waterBiomes.contains(biome)) {
			return 4;
		} else if (beachBiomes.contains(biome)) {
			return 3;
		} else {
			return 1;
		}
	}

	/** If no valid biome ID is found, returns null. */
	@Override
	public TileKind getBiomeID(Chunk chunk) {
		Biome[] chunkBiomes = chunk.getBiomeArray();
		Map<Biome, Integer> biomeOccurrences = new HashMap<>(Registry.BIOME.getIds().size());

		// The following important pseudo-biomes don't have IDs:
		int lavaOccurrences = 0;
		int ravineOccurences = 0;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				Biome biomeID = chunkBiomes[x << 4 | z];
				if (doScanPonds) {
					int y = chunk instanceof WorldChunk ? ((WorldChunk) chunk).getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES).get(x, z) : chunk.getHeight() - 1;
					if (y > 0) {
						// TODO FABRIC
						Block topBlock = chunk.getBlockState(new BlockPos(x, y-1, z)).getBlock();
						// For some reason lava doesn't count in height value
						// TODO: check if 1.8 fixes this!
						Block topBlock2 = chunk.getBlockState(new BlockPos(x, y, z)).getBlock();
						// Check if there's surface of water at (x, z), but not swamp
						if (topBlock == Blocks.WATER && !swampBiomes.contains(biomeID)) {
							int occurrence = biomeOccurrences.getOrDefault(waterPoolBiome, 0) + priorityWaterPool;
							biomeOccurrences.put(waterPoolBiome, occurrence);
						} else if (topBlock2 == Blocks.LAVA) {
							lavaOccurrences += prioritylavaPool;
						}
					}
				}
				if (doScanRavines) {
					// TODO FABRIC
					/* if(chunk.XX_1_12_2_b_XX(x, z) < chunk.XX_1_12_2_q_XX().t.XX_1_12_2_i_XX() - ravineMinDepth)	{
						ravineOccurences += priorityRavine;
					} */
				}

				int occurrence = biomeOccurrences.getOrDefault(biomeID, 0) + priorityForBiome(biomeID);
				biomeOccurrences.put(biomeID, occurrence);
			}
		}

		try {
			Map.Entry<Biome, Integer> meanBiome = Collections.max(biomeOccurrences.entrySet(), Comparator.comparingInt(Map.Entry::getValue));
			Biome meanBiomeId = meanBiome.getKey();
			int meanBiomeOccurrences = meanBiome.getValue();

			// The following important pseudo-biomes don't have IDs:
			if (meanBiomeOccurrences < ravineOccurences) {
				return TileKindFactory.get(ExtTileIdMap.TILE_RAVINE);
			}
			if (meanBiomeOccurrences < lavaOccurrences) {
				return TileKindFactory.get(ExtTileIdMap.TILE_LAVA);
			}

			return TileKindFactory.get(meanBiomeId);
		} catch(NoSuchElementException e){
			return TileKindFactory.get(Biomes.DEFAULT);
		}
	}
}
