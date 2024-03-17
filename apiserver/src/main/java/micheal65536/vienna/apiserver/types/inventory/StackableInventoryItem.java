package micheal65536.vienna.apiserver.types.inventory;

import org.jetbrains.annotations.NotNull;

public record StackableInventoryItem(
		@NotNull String id,
		int owned,
		int fragments,
		@NotNull On unlocked,
		@NotNull On seen
)
{
	public record On(
			@NotNull String on
	)
	{
	}
}