package micheal65536.vienna.tappablesgenerator;

import org.jetbrains.annotations.NotNull;

public record Tappable(
		@NotNull String id,
		float lat,
		float lon,
		long spawnTime,
		long validFor,
		@NotNull String icon,
		@NotNull Rarity rarity,
		@NotNull Item[] items
)
{
	public enum Rarity
	{
		COMMON,
		UNCOMMON,
		RARE,
		EPIC,
		LEGENDARY
	}

	public record Item(
			@NotNull String id,
			int count
	)
	{
	}
}