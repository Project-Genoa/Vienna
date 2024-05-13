package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerDisconnectedRequest(
		@NotNull String playerId,
		@Nullable InventoryResponse backpackContents
)
{
}