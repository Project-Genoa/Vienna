package micheal65536.vienna.apiserver.types.journal;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public record Journal(
		@NotNull HashMap<String, InventoryJournalEntry> inventoryJournal,
		@NotNull ActivityLogEntry[] activityLog
)
{
	public record InventoryJournalEntry(
			@NotNull String firstSeen,
			@NotNull String lastSeen,
			int amountCollected
	)
	{
	}

	public record ActivityLogEntry(
			// TODO
	)
	{
	}
}