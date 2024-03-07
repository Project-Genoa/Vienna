package micheal65536.minecraftearth.apiserver.types.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BoostMetadata(
		@NotNull String name,
		@NotNull String type,
		@NotNull String attribute,
		boolean canBeDeactivated,
		boolean canBeRemoved,
		@Nullable String activeDuration,
		boolean additive,
		@Nullable Integer level,
		@NotNull Effect[] effects,
		@Nullable String scenario,
		@Nullable String cooldown
)
{
	public record Effect(
			@NotNull String type,
			@Nullable String duration,
			@Nullable Integer value,
			@Nullable String unit,
			@NotNull String targets,
			@NotNull String[] items,
			@NotNull String[] itemScenarios,
			@NotNull String activation,
			@Nullable String modifiesType
	)
	{
	}
}