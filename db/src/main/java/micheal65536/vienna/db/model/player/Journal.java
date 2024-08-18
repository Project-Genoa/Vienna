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

	@NotNull
	public HashMap<String, ItemJournalEntry> getItems()
	{
		return new HashMap<>(this.items);
	}

	@Nullable
	public ItemJournalEntry getItem(@NotNull String uuid)
	{
		return this.items.getOrDefault(uuid, null);
	}

	public int addCollectedItem(@NotNull String uuid, long timestamp, int count)
	{
		if (count < 0)
		{
			throw new IllegalArgumentException();
		}
		ItemJournalEntry itemJournalEntry = this.items.getOrDefault(uuid, null);
		if (itemJournalEntry == null)
		{
			this.items.put(uuid, new ItemJournalEntry(timestamp, timestamp, count));
			return 0;
		}
		else
		{
			this.items.put(uuid, new ItemJournalEntry(itemJournalEntry.firstSeen, itemJournalEntry.lastSeen, itemJournalEntry.amountCollected + count));
			return itemJournalEntry.amountCollected;
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