package micheal65536.vienna.tappablesgenerator;

import org.jetbrains.annotations.NotNull;

public record Encounter(
		@NotNull String id,
		float lat,
		float lon,
		long spawnTime,
		long validFor,
		@NotNull String icon,
		@NotNull Rarity rarity,
		@NotNull String encounterBuildplateId
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
}