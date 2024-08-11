package micheal65536.vienna.apiserver.types.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.types.common.Effect;

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
}