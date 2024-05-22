package micheal65536.vienna.tappablesgenerator;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.staticdata.StaticData;
import micheal65536.vienna.staticdata.TappablesConfig;

import java.util.Arrays;
import java.util.Comparator;
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

	private final StaticData staticData;

	private final Random random;

	public TappableGenerator(@NotNull StaticData staticData)
	{
		this.staticData = staticData;
		if (this.staticData.tappablesConfig.tappables.length == 0)
		{
			LogManager.getLogger().warn("No tappable configs provided");
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
		if (this.staticData.tappablesConfig.tappables.length == 0)
		{
			return new Tappable[0];
		}

		LinkedList<Tappable> tappables = new LinkedList<>();
		for (int count = this.random.nextInt(MIN_COUNT, MAX_COUNT + 1); count > 0; count--)
		{
			long spawnDelay = this.random.nextLong(MIN_DELAY, MAX_DELAY + 1);
			long duration = this.random.nextLong(MIN_DURATION, MAX_DURATION + 1);

			TappablesConfig.TappableConfig tappableConfig = this.staticData.tappablesConfig.tappables[this.random.nextInt(0, this.staticData.tappablesConfig.tappables.length)];

			float[] tileBounds = getTileBounds(tileX, tileY);
			float lat = this.random.nextFloat(tileBounds[1], tileBounds[0]);
			float lon = this.random.nextFloat(tileBounds[2], tileBounds[3]);

			int dropSetIndex = this.random.nextInt(0, Arrays.stream(tappableConfig.dropSets()).mapToInt(dropSet -> dropSet.chance()).sum());
			TappablesConfig.TappableConfig.DropSet dropSet = null;
			for (TappablesConfig.TappableConfig.DropSet dropSet1 : tappableConfig.dropSets())
			{
				dropSet = dropSet1;
				dropSetIndex -= dropSet1.chance();
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
			for (String itemId : dropSet.items())
			{
				TappablesConfig.TappableConfig.ItemCount itemCount = tappableConfig.itemCounts().get(itemId);
				items.add(new Tappable.Drops.Item(itemId, this.random.nextInt(itemCount.min(), itemCount.max() + 1)));
			}

			int experiencePoints = items.stream().mapToInt(item -> this.staticData.catalog.itemsCatalog.getItem(item.id()).experience().tappable() * item.count()).sum();
			Tappable.Rarity rarity = items.stream().map(item -> this.staticData.catalog.itemsCatalog.getItem(item.id()).rarity()).max(Comparator.comparing(rarity1 -> rarity1.ordinal())).map(rarity1 -> Tappable.Rarity.valueOf(rarity1.name())).orElseThrow();

			Tappable.Drops drops = new Tappable.Drops(
					experiencePoints,
					items.toArray(Tappable.Drops.Item[]::new)
			);

			Tappable tappable = new Tappable(
					UUID.randomUUID().toString(),
					lat,
					lon,
					currentTime + spawnDelay,
					duration,
					tappableConfig.icon(),
					rarity,
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