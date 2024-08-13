package micheal65536.vienna.apiserver.types.journal;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.types.common.Rewards;

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
			@NotNull Type scenario,
			@NotNull String eventTime,
			@NotNull Rewards rewards,
			@NotNull HashMap<String, String> properties
	)
	{
		public enum Type
		{
			@SerializedName("LevelUp") LEVEL_UP,
			@SerializedName("TappableCollected") TAPPABLE,
			@SerializedName("JournalContentCollected") JOURNAL_ITEM_UNLOCKED,
			@SerializedName("CraftingJobCompleted") CRAFTING_COMPLETED,
			@SerializedName("SmeltingJobCompleted") SMELTING_COMPLETED,
			@SerializedName("BoostActivated") BOOST_ACTIVATED
		}
	}
}