package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public final class Journal
{
	@NotNull
	private final HashMap<String, ItemJournalEntry> items;

	public Journal()
	{
		this.items = new HashMap<>();
	}

	@NotNull
	public Journal copy()
	{
		Journal journal = new Journal();
		journal.items.putAll(this.items);
		return journal;
	}

	@Nullable
	public ItemJournalEntry getItem(@NotNull String uuid)
	{
		return this.items.getOrDefault(uuid, null);
	}

	public void touchItem(@NotNull String uuid, long timestamp)
	{
		// TODO: figure out amountCollected
		ItemJournalEntry itemJournalEntry = this.items.getOrDefault(uuid, null);
		if (itemJournalEntry == null)
		{
			this.items.put(uuid, new ItemJournalEntry(timestamp, timestamp, 0));
		}
		else
		{
			this.items.put(uuid, new ItemJournalEntry(itemJournalEntry.firstSeen, timestamp, itemJournalEntry.amountCollected));
		}
	}

	public record ItemJournalEntry(
			long firstSeen,
			long lastSeen,
			int amountCollected
	)
	{
	}
}