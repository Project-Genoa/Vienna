package micheal65536.minecraftearth.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hotbar
{
	public final Item[] items;

	public Hotbar()
	{
		this.items = new Item[7];
	}

	public void limitToInventory(@NotNull Inventory inventory)
	{
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
					continue;
				}
				else
				{
					item = null;
				}
			}
			else
			{
				int inventoryCount = inventory.getItemCount(item.uuid());
				if (inventoryCount > 0)
				{
					if (inventoryCount < item.count())
					{
						item = new Item(item.uuid(), inventoryCount, null);
					}
					else
					{
						continue;
					}
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