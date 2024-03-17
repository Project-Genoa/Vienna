package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.db.model.common.NonStackableItemInstance;

import java.util.HashMap;
import java.util.LinkedList;

public final class Inventory
{
	@NotNull
	private final HashMap<String, Integer> stackableItems;
	@NotNull
	private final HashMap<String, HashMap<String, NonStackableItemInstance>> nonStackableItems;

	public Inventory()
	{
		this.stackableItems = new HashMap<>();
		this.nonStackableItems = new HashMap<>();
	}

	@NotNull
	public Inventory copy()
	{
		Inventory inventory = new Inventory();
		inventory.stackableItems.putAll(this.stackableItems);
		HashMap<String, HashMap<String, NonStackableItemInstance>> nonStackableItems = new HashMap<>();
		this.nonStackableItems.forEach((id, instances) -> nonStackableItems.put(id, new HashMap<>(instances)));
		inventory.nonStackableItems.putAll(nonStackableItems);
		return inventory;
	}

	public record StackableItem(
			@NotNull String id,
			int count
	)
	{
	}

	@NotNull
	public StackableItem[] getStackableItems()
	{
		return this.stackableItems.entrySet().stream().map(entry -> new StackableItem(entry.getKey(), entry.getValue())).toArray(StackableItem[]::new);
	}

	public record NonStackableItem(
			@NotNull String id,
			@NotNull NonStackableItemInstance[] instances
	)
	{
	}

	@NotNull
	public NonStackableItem[] getNonStackableItems()
	{
		return this.nonStackableItems.entrySet().stream().map(entry -> new NonStackableItem(entry.getKey(), entry.getValue().values().toArray(NonStackableItemInstance[]::new))).toArray(NonStackableItem[]::new);
	}

	public int getItemCount(@NotNull String id)
	{
		Integer count = this.stackableItems.getOrDefault(id, null);
		if (count != null)
		{
			return count;
		}
		HashMap<String, NonStackableItemInstance> instances = this.nonStackableItems.getOrDefault(id, null);
		if (instances != null)
		{
			return instances.size();
		}
		return 0;
	}

	@NotNull
	public NonStackableItemInstance[] getItemInstances(@NotNull String id)
	{
		HashMap<String, NonStackableItemInstance> instances = this.nonStackableItems.getOrDefault(id, null);
		if (instances != null)
		{
			return instances.values().toArray(NonStackableItemInstance[]::new);
		}
		return new NonStackableItemInstance[0];
	}

	@Nullable
	public NonStackableItemInstance getItemInstance(@NotNull String id, @NotNull String instanceId)
	{
		HashMap<String, NonStackableItemInstance> instances = this.nonStackableItems.getOrDefault(id, null);
		if (instances != null)
		{
			return instances.getOrDefault(instanceId, null);
		}
		return null;
	}

	public void addItems(@NotNull String id, int count)
	{
		if (count < 0)
		{
			throw new IllegalArgumentException();
		}
		this.stackableItems.put(id, this.stackableItems.getOrDefault(id, 0) + count);
	}

	public void addItems(@NotNull String id, @NotNull NonStackableItemInstance[] instances)
	{
		HashMap<String, NonStackableItemInstance> instancesMap = this.nonStackableItems.computeIfAbsent(id, id1 -> new HashMap<>());
		for (NonStackableItemInstance instance : instances)
		{
			instancesMap.put(instance.instanceId(), instance);
		}
	}

	public boolean takeItems(@NotNull String id, int count)
	{
		if (count < 0)
		{
			throw new IllegalArgumentException();
		}
		int currentCount = this.stackableItems.getOrDefault(id, 0);
		if (currentCount < count)
		{
			return false;
		}
		this.stackableItems.put(id, currentCount - count);
		return true;
	}

	public NonStackableItemInstance[] takeItems(@NotNull String id, @NotNull String[] instanceIds)
	{
		HashMap<String, NonStackableItemInstance> instanceMap = this.nonStackableItems.getOrDefault(id, null);
		if (instanceMap == null)
		{
			return null;
		}
		LinkedList<NonStackableItemInstance> instances = new LinkedList<>();
		for (String instanceId : instanceIds)
		{
			NonStackableItemInstance instance = instanceMap.remove(instanceId);
			if (instance == null)
			{
				return null;
			}
			instances.add(instance);
		}
		return instances.toArray(NonStackableItemInstance[]::new);
	}
}