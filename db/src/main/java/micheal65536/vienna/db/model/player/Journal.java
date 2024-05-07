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

	public void touchItem(@NotNull String uuid, long timestamp)
	{
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

	// TODO: find out what is supposed to count as a "collected item" - currently we count items from tappables *and* other rewards (e.g. challenge/level rewards, this also currently includes workshop output), but not from buildplates because that would be really difficult to track
	public void addCollectedItem(@NotNull String uuid, int count)
	{
		if (count < 0)
		{
			throw new IllegalArgumentException();
		}
		ItemJournalEntry itemJournalEntry = this.items.getOrDefault(uuid, null);
		if (itemJournalEntry == null)
		{
			throw new IllegalStateException("Item does not exist in journal, make sure to touch it or otherwise verify that it exists before calling addCollectedItem");
		}
		this.items.put(uuid, new ItemJournalEntry(itemJournalEntry.firstSeen, itemJournalEntry.lastSeen, itemJournalEntry.amountCollected + count));
	}

	public record ItemJournalEntry(
			long firstSeen,
			long lastSeen,
			int amountCollected
	)
	{
	}
}