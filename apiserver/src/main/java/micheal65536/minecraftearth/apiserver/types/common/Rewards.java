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
			@NotNull String id
	)
	{
	}

	public record Challenge(
			@NotNull String id
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