package micheal65536.vienna.db.model.player;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.model.common.Rewards;

import java.util.LinkedList;

public final class ActivityLog
{
	@NotNull
	private final LinkedList<Entry> entries;

	public ActivityLog()
	{
		this.entries = new LinkedList<>();
	}

	@NotNull
	public ActivityLog copy()
	{
		ActivityLog activityLog = new ActivityLog();
		activityLog.entries.addAll(this.entries);
		return activityLog;
	}

	@NotNull
	public Entry[] getEntries()
	{
		return this.entries.toArray(Entry[]::new);
	}

	public void addEntry(@NotNull Entry entry)
	{
		this.entries.add(entry);
	}

	public static abstract class Entry
	{
		public final long timestamp;
		@NotNull
		public final Type type;

		private Entry(long timestamp, @NotNull Type type)
		{
			this.timestamp = timestamp;
			this.type = type;
		}

		public enum Type
		{
			LEVEL_UP,
			TAPPABLE,
			JOURNAL_ITEM_UNLOCKED,
			CRAFTING_COMPLETED,
			SMELTING_COMPLETED
		}

		public static class Deserializer implements JsonDeserializer<Entry>
		{
			private static class BaseEntry extends Entry
			{
				private BaseEntry(long timestamp, @NotNull Type type)
				{
					super(timestamp, type);
				}
			}

			@Override
			public Entry deserialize(JsonElement jsonElement, java.lang.reflect.Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException
			{
				BaseEntry baseEntry = jsonDeserializationContext.deserialize(jsonElement, BaseEntry.class);
				return jsonDeserializationContext.deserialize(jsonElement, switch (baseEntry.type)
				{
					case LEVEL_UP -> LevelUpEntry.class;
					case TAPPABLE -> TappableEntry.class;
					case JOURNAL_ITEM_UNLOCKED -> JournalItemUnlockedEntry.class;
					case CRAFTING_COMPLETED -> CraftingCompletedEntry.class;
					case SMELTING_COMPLETED -> SmeltingCompletedEntry.class;
				});
			}
		}
	}

	public static final class LevelUpEntry extends Entry
	{
		public final int level;

		public LevelUpEntry(long timestamp, int level)
		{
			super(timestamp, Type.LEVEL_UP);
			this.level = level;
		}
	}

	public static final class TappableEntry extends Entry
	{
		@NotNull
		public final Rewards rewards;

		public TappableEntry(long timestamp, @NotNull Rewards rewards)
		{
			super(timestamp, Type.TAPPABLE);
			this.rewards = rewards;
		}
	}

	public static final class JournalItemUnlockedEntry extends Entry
	{
		@NotNull
		public final String itemId;

		public JournalItemUnlockedEntry(long timestamp, @NotNull String itemId)
		{
			super(timestamp, Type.JOURNAL_ITEM_UNLOCKED);
			this.itemId = itemId;
		}
	}

	public static final class CraftingCompletedEntry extends Entry
	{
		@NotNull
		public final Rewards rewards;

		public CraftingCompletedEntry(long timestamp, @NotNull Rewards rewards)
		{
			super(timestamp, Type.CRAFTING_COMPLETED);
			this.rewards = rewards;
		}
	}

	public static final class SmeltingCompletedEntry extends Entry
	{
		@NotNull
		public final Rewards rewards;

		public SmeltingCompletedEntry(long timestamp, @NotNull Rewards rewards)
		{
			super(timestamp, Type.SMELTING_COMPLETED);
			this.rewards = rewards;
		}
	}
}