package micheal65536.vienna.tappablesgenerator;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class TappableGenerator
{
	// TODO: make these configurable
	private static final int MIN_COUNT = 1;
	private static final int MAX_COUNT = 3;
	private static final long MIN_DURATION = 2 * 60 * 1000;
	private static final long MAX_DURATION = 5 * 60 * 1000;
	private static final long MIN_DELAY = 1 * 60 * 1000;
	private static final long MAX_DELAY = 2 * 60 * 1000;

	private record TappableConfig(
			@NotNull String icon,
			int experiencePoints,    // TODO: how is tappable XP determined?
			@NotNull DropSet[] dropSets,
			@NotNull HashMap<String, ItemCount> itemCounts
	)
	{
		public record DropSet(
				@NotNull String[] items,
				int chance
		)
		{
		}

		public record ItemCount(
				int min,
				int max
		)
		{
		}
	}

	private final TappableConfig[] tappableConfigs;

	private final Random random;

	public TappableGenerator()
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
			if (tappableConfig.dropSets.length == 0)
			{
				LogManager.getLogger().warn("Tappable config {} has no drop sets", tappableConfig.icon);
			}
			Arrays.stream(tappableConfig.dropSets).flatMap(dropSet -> Arrays.stream(dropSet.items)).forEach(itemId ->
			{
				if (!tappableConfig.itemCounts.containsKey(itemId))
				{
					LogManager.getLogger().fatal("Tappable config {} has no item count for item {}", tappableConfig.icon, itemId);
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

			TappableConfig tappableConfig = this.tappableConfigs[this.random.nextInt(0, this.tappableConfigs.length)];

			float[] tileBounds = getTileBounds(tileX, tileY);
			float lat = this.random.nextFloat(tileBounds[1], tileBounds[0]);
			float lon = this.random.nextFloat(tileBounds[2], tileBounds[3]);

			int dropSetIndex = this.random.nextInt(0, Arrays.stream(tappableConfig.dropSets).mapToInt(dropSet -> dropSet.chance).sum());
			TappableConfig.DropSet dropSet = null;
			for (TappableConfig.DropSet dropSet1 : tappableConfig.dropSets)
			{
				dropSet = dropSet1;
				dropSetIndex -= dropSet1.chance;
				if (dropSetIndex <= 0)
				{
					break;
				}
			}
			if (dropSet == null)
			{
				throw new AssertionError();
			}

			LinkedList<Tappable.Drops.Item> items = new LinkedList<>();
			for (String itemId : dropSet.items)
			{
				TappableConfig.ItemCount itemCount = tappableConfig.itemCounts.get(itemId);
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
					tappableConfig.icon,
					Tappable.Rarity.COMMON,    // TODO: determine rarity from drops
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