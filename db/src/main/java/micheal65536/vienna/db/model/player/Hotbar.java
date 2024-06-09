package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;

public final class Hotbar
{
	public final Item[] items;

	public Hotbar()
	{
		this.items = new Item[7];
	}

	public void limitToInventory(@NotNull Inventory inventory)
	{
		HashMap<String, Integer> usedStackableItemCounts = new HashMap<>();
		HashMap<String, HashSet<String>> usedNonStackableItemInstances = new HashMap<>();
		for (int index = 0; index < this.items.length; index++)
		{
			Item item = this.items[index];
			if (item == null)
			{
				continue;
			}
			if (item.instanceId() != null)
			{
				if (inventory.getItemInstance(item.uuid(), item.instanceId()) != null)
				{
					HashSet<String> usedItemInstances = usedNonStackableItemInstances.computeIfAbsent(item.uuid(), uuid -> new HashSet<>());
					if (!usedItemInstances.add(item.instanceId()))
					{
						item = null;
					}
				}
				else
				{
					item = null;
				}
			}
			else
			{
				int inventoryCount = inventory.getItemCount(item.uuid());
				int usedCount = usedStackableItemCounts.getOrDefault(item.uuid(), 0);
				if (inventoryCount - usedCount > 0)
				{
					if (inventoryCount - usedCount < item.count())
					{
						item = new Item(item.uuid(), inventoryCount - usedCount, null);
					}
					usedCount += item.count();
					usedStackableItemCounts.put(item.uuid(), usedCount);
				}
				else
				{
					item = null;
				}
			}
			this.items[index] = item;
		}
	}

	public record Item(
			@NotNull String uuid,
			@NotNull int count,
			@Nullable String instanceId
	)
	{
	}
}