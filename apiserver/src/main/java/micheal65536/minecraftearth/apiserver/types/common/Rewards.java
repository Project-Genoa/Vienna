package micheal65536.minecraftearth.apiserver.types.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: determine format
public record Rewards(
		@Nullable Integer rubies,
		@Nullable Integer experiencePoints,
		@NotNull Item[] inventory,
		@NotNull Buildplate[] buildplates,
		@NotNull Challenge[] challenges,
		@NotNull PersonaItem[] personaItems,
		@NotNull UtilityBlock[] utilityBlocks
)
{
	public record Item(
			@NotNull String id,
			int amount
	)
	{
	}

	public record Buildplate(
	)
	{
	}

	public record Challenge(
	)
	{
	}

	public record PersonaItem(
	)
	{
	}

	public record UtilityBlock(
	)
	{
	}
}