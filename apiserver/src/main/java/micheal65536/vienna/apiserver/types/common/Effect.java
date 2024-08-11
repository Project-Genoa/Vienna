package micheal65536.vienna.apiserver.types.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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