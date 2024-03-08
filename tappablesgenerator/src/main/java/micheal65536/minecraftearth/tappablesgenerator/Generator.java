package micheal65536.minecraftearth.tappablesgenerator;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class Generator
{
	// TODO: make these configurable
	private static final int MIN_COUNT = 1;
	private static final int MAX_COUNT = 3;
	private static final long MIN_DURATION = 2 * 60 * 1000;
	private static final long MAX_DURATION = 5 * 60 * 1000;
	private static final long MIN_DELAY = 1 * 60 * 1000;
	private static final long MAX_DELAY = 2 * 60 * 1000;

	private record TappableConfig(
			@NotNull String tappableID,
			@NotNull Rarity rarity,
			int experiencePoints,
			@NotNull String[][] possibleDropSets,
			@NotNull HashMap<String, ItemCount> possibleItemCount
	)
	{
		public enum Rarity
		{
			// TODO: find actual weights
			@SerializedName("Common") COMMON(1.0f),
			@SerializedName("Uncommon") UNCOMMON(0.75f),
			@SerializedName("Rare") RARE(0.5f),
			@SerializedName("Epic") EPIC(0.25f),
			@SerializedName("Legendary") LEGENDARY(0.125f);

			public final float weight;

			Rarity(float weight)
			{
				this.weight = weight;
			}
		}

		public record ItemCount(
				int min,
				int max
		)
		{
		}
	}

	private final TappableConfig[] tappableConfigs;
	private final float totalWeight;

	private final Random random;

	public Generator()
	{
		try
		{
			LogManager.getLogger().info("Loading tappable generator data");
			File dataDir = new File("data", "tappable");
			LinkedList<TappableConfig> tappableConfigs = new LinkedList<>();
			for (File file : dataDir.listFiles())
			{
				tappableConfigs.add(new Gson().fromJson(new FileReader(file), TappableConfig.class));
			}
			this.tappableConfigs = tappableConfigs.toArray(TappableConfig[]::new);
			this.totalWeight = (float) tappableConfigs.stream().mapToDouble(tappableConfig -> tappableConfig.rarity.weight).sum();
		}
		catch (Exception exception)
		{
			LogManager.getLogger().fatal("Failed to load tappable generator data", exception);
			System.exit(1);
			throw new AssertionError();
		}

		if (this.tappableConfigs.length == 0)
		{
			LogManager.getLogger().fatal("No tappable configs provided");
			System.exit(1);
			throw new AssertionError();
		}
		for (TappableConfig tappableConfig : this.tappableConfigs)
		{
			if (tappableConfig.possibleDropSets.length == 0)
			{
				LogManager.getLogger().warn("Tappable config {} has no drop sets", tappableConfig.tappableID);
			}
			Arrays.stream(tappableConfig.possibleDropSets).flatMap(Arrays::stream).forEach(itemId ->
			{
				if (!tappableConfig.possibleItemCount.containsKey(itemId))
				{
					LogManager.getLogger().fatal("Tappable config {} has no item count for item {}", tappableConfig.tappableID, itemId);
					System.exit(1);
					throw new AssertionError();
				}
			});
		}

		this.random = new Random();
	}

	public long getMaxTappableLifetime()
	{
		return MAX_DELAY + MAX_DURATION + 30 * 1000;
	}

	@NotNull
	public Tappable[] generateTappables(int tileX, int tileY, long currentTime)
	{
		LinkedList<Tappable> tappables = new LinkedList<>();
		for (int count = this.random.nextInt(MIN_COUNT, MAX_COUNT + 1); count > 0; count--)
		{
			long spawnDelay = this.random.nextLong(MIN_DELAY, MAX_DELAY + 1);
			long duration = this.random.nextLong(MIN_DURATION, MAX_DURATION + 1);

			float configPos = this.random.nextFloat(0.0f, this.totalWeight);
			TappableConfig tappableConfig = null;
			for (TappableConfig tappableConfig1 : this.tappableConfigs)
			{
				tappableConfig = tappableConfig1;
				configPos -= tappableConfig1.rarity.weight;
				if (configPos <= 0.0f)
				{
					break;
				}
			}
			if (tappableConfig == null)
			{
				throw new AssertionError();
			}

			float[] tileBounds = getTileBounds(tileX, tileY);
			float lat = this.random.nextFloat(tileBounds[1], tileBounds[0]);
			float lon = this.random.nextFloat(tileBounds[2], tileBounds[3]);

			LinkedList<Tappable.Drops.Item> items = new LinkedList<>();
			String[] dropSet = tappableConfig.possibleDropSets[this.random.nextInt(0, tappableConfig.possibleDropSets.length)];
			for (String itemId : dropSet)
			{
				TappableConfig.ItemCount itemCount = tappableConfig.possibleItemCount.get(itemId);
				items.add(new Tappable.Drops.Item(itemId, this.random.nextInt(itemCount.min, itemCount.max + 1)));
			}
			Tappable.Drops drops = new Tappable.Drops(
					tappableConfig.experiencePoints,
					items.toArray(Tappable.Drops.Item[]::new)
			);

			Tappable tappable = new Tappable(
					UUID.randomUUID().toString(),
					lat,
					lon,
					currentTime + spawnDelay,
					duration,
					tappableConfig.tappableID,
					Tappable.Rarity.valueOf(tappableConfig.rarity.name()),
					drops
			);
			tappables.add(tappable);
		}
		return tappables.toArray(Tappable[]::new);
	}

	private static float[] getTileBounds(int tileX, int tileY)
	{
		return new float[]{
				yToLat((float) tileY / (1 << 16)),
				yToLat((float) (tileY + 1) / (1 << 16)),
				xToLon((float) tileX / (1 << 16)),
				xToLon((float) (tileX + 1) / (1 << 16))
		};
	}

	private static float xToLon(float x)
	{
		return (float) Math.toDegrees((x * 2.0 - 1.0) * Math.PI);
	}

	private static float yToLat(float y)
	{
		return (float) Math.toDegrees(Math.atan(Math.sinh((1.0 - y * 2.0) * Math.PI)));
	}
}