package micheal65536.minecraftearth.apiserver.types.inventory;

import org.jetbrains.annotations.NotNull;

public record NonStackableInventoryItem(
		@NotNull String id,
		@NotNull Instance[] instances,
		int fragments,
		@NotNull On unlocked,
		@NotNull On seen
)
{
	public record Instance(
			@NotNull String id,
			float health
	)
	{
	}

	public record On(
			@NotNull String on
	)
	{
	}
}