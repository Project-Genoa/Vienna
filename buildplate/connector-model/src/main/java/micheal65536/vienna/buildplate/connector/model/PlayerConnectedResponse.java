package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.Nullable;

public record PlayerConnectedResponse(
		boolean accepted,
		@Nullable InventoryResponse initialInventoryContents
)
{
}