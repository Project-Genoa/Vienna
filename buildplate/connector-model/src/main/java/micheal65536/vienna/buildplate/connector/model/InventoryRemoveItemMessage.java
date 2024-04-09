package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record InventoryRemoveItemMessage(
		@NotNull String playerId,
		@NotNull String itemId,
		int count,
		@Nullable String instanceId
)
{
}