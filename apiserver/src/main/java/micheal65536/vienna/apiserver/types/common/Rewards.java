package micheal65536.vienna.apiserver.types.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: determine format
public record Rewards(
		@Nullable Integer rubies,
		@Nullable Integer experiencePoints,
		@Nullable Integer level,
		@NotNull Item[] inventory,
		@NotNull Buildplate[] buildplates,
		@NotNull Challenge[] challenges,
		@NotNull String[] personaItems,
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

	public record UtilityBlock(
	)
	{
	}
}