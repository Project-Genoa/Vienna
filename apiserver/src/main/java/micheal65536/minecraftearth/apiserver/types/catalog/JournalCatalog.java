package micheal65536.minecraftearth.apiserver.types.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public record JournalCatalog(
		@NotNull HashMap<String, Item> items
)
{
	public record Item(
			@NotNull String referenceId,
			@NotNull String parentCollection,
			int overallOrder,
			int collectionOrder,
			@Nullable String defaultSound,
			boolean deprecated,
			@NotNull String toolsVersion
	)
	{
	}
}