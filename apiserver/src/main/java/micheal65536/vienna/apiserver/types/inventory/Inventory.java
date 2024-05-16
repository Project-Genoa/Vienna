package micheal65536.vienna.apiserver.types.inventory;

import org.jetbrains.annotations.NotNull;

public record Inventory(
		HotbarItem[] hotbar,
		@NotNull StackableInventoryItem[] stackableItems,
		@NotNull NonStackableInventoryItem[] nonStackableItems
)
{
}