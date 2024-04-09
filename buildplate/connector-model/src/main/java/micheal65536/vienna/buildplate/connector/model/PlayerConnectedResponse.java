package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerConnectedResponse(
		boolean accepted,
		@Nullable Inventory inventory
)
{
	public record Inventory(
			@NotNull Item[] items,
			HotbarItem[] hotbar
	)
	{
		public record Item(
				@NotNull String id,
				int count,
				@Nullable String instanceId,
				int wear
		)
		{
		}

		public record HotbarItem(
				@NotNull String id,
				int count,
				@Nullable String instanceId
		)
		{
		}
	}
}