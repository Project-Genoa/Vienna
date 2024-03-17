package micheal65536.vienna.apiserver.types.inventory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record HotbarItem(
		@NotNull String id,
		int count,
		@Nullable String instanceId,
		@Nullable Float health
)
{
}