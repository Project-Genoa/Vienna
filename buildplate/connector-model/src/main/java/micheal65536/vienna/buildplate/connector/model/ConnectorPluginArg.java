package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;

public record ConnectorPluginArg(
		@NotNull String eventBusAddress,
		@NotNull String eventBusQueueName,
		boolean saveEnabled,
		@NotNull InventoryType inventoryType
)
{
}