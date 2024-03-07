package micheal65536.minecraftearth.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.minecraftearth.apiserver.routing.Request;
import micheal65536.minecraftearth.apiserver.routing.Response;
import micheal65536.minecraftearth.apiserver.routing.Router;
import micheal65536.minecraftearth.apiserver.routing.ServerErrorException;
import micheal65536.minecraftearth.apiserver.types.inventory.HotbarItem;
import micheal65536.minecraftearth.apiserver.types.inventory.NonStackableInventoryItem;
import micheal65536.minecraftearth.apiserver.types.inventory.StackableInventoryItem;
import micheal65536.minecraftearth.apiserver.utils.EarthApiResponse;
import micheal65536.minecraftearth.apiserver.utils.TimeFormatter;
import micheal65536.minecraftearth.db.DatabaseException;
import micheal65536.minecraftearth.db.EarthDB;
import micheal65536.minecraftearth.db.model.player.Hotbar;
import micheal65536.minecraftearth.db.model.player.Inventory;
import micheal65536.minecraftearth.db.model.player.Journal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class InventoryRouter extends Router
{
	public InventoryRouter(@NotNull EarthDB earthDB)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/inventory/survival").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			Inventory inventoryModel;
			Hotbar hotbarModel;
			Journal journalModel;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("inventory", playerId, Inventory.class)
						.get("hotbar", playerId, Hotbar.class)
						.get("journal", playerId, Journal.class)
						.execute(earthDB);
				inventoryModel = (Inventory) results.get("inventory").value();
				hotbarModel = (Hotbar) results.get("hotbar").value();
				journalModel = (Journal) results.get("journal").value();
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			HashMap<String, Object> inventory = new HashMap<>();
			HotbarItem[] hotbarItems = Arrays.stream(hotbarModel.items).map(item -> item != null ? new HotbarItem(
					item.uuid(),
					item.count(),
					item.instanceId(),
					item.instanceId() != null ? inventoryModel.getItemInstance(item.uuid(), item.instanceId()).health() : 0.0f
			) : null).toArray(HotbarItem[]::new);
			inventory.put("hotbar", hotbarItems);
			HashMap<String, Integer> hotbarItemCounts = new HashMap<>();
			Arrays.stream(hotbarModel.items).forEach(item ->
			{
				if (item != null)
				{
					hotbarItemCounts.put(item.uuid(), hotbarItemCounts.getOrDefault(item.uuid(), 0) + item.count());
				}
			});
			StackableInventoryItem[] stackableItems = Arrays.stream(inventoryModel.getStackableItems()).map(item ->
			{
				String uuid = item.id();
				int count = item.count() - hotbarItemCounts.getOrDefault(uuid, 0);
				Journal.ItemJournalEntry itemJournalEntry = journalModel.getItem(uuid);
				String firstSeen = TimeFormatter.formatTime(itemJournalEntry.firstSeen());
				String lastSeen = TimeFormatter.formatTime(itemJournalEntry.lastSeen());
				return new StackableInventoryItem(
						uuid,
						count,
						1,
						new StackableInventoryItem.On(firstSeen),
						new StackableInventoryItem.On(lastSeen)
				);
			}).toArray(StackableInventoryItem[]::new);
			inventory.put("stackableItems", stackableItems);
			HashSet<String> hotbarItemInstances = new HashSet<>();
			Arrays.stream(hotbarModel.items).forEach(item ->
			{
				if (item != null && item.instanceId() != null)
				{
					hotbarItemInstances.add(item.instanceId());
				}
			});
			NonStackableInventoryItem[] nonStackableItems = Arrays.stream(inventoryModel.getNonStackableItems()).map(item ->
			{
				String uuid = item.id();
				Journal.ItemJournalEntry itemJournalEntry = journalModel.getItem(uuid);
				String firstSeen = TimeFormatter.formatTime(itemJournalEntry.firstSeen());
				String lastSeen = TimeFormatter.formatTime(itemJournalEntry.lastSeen());
				return new NonStackableInventoryItem(
						uuid,
						Arrays.stream(item.instances()).filter(instance -> !hotbarItemInstances.contains(instance.instanceId())).map(instance -> new NonStackableInventoryItem.Instance(instance.instanceId(), instance.health())).toArray(NonStackableInventoryItem.Instance[]::new),
						1,
						new NonStackableInventoryItem.On(firstSeen),
						new NonStackableInventoryItem.On(lastSeen)
				);
			}).toArray(NonStackableInventoryItem[]::new);
			inventory.put("nonStackableItems", nonStackableItems);

			return Response.okFromJson(new EarthApiResponse<>(inventory), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.PUT, "/inventory/survival/hotbar").build(), request ->
		{
			record SetHotbarRequestItem(
					@NotNull String id,
					int count,
					@Nullable String instanceId
			)
			{
			}
			SetHotbarRequestItem[] setHotbarRequestItems = request.getBodyAsJson(SetHotbarRequestItem[].class);
			if (setHotbarRequestItems.length != 7)
			{
				return Response.badRequest();
			}

			Inventory inventoryModel;
			Hotbar hotbarModel;
			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.then(results1 ->
						{
							Hotbar hotbar = new Hotbar();
							for (int index = 0; index < hotbar.items.length; index++)
							{
								SetHotbarRequestItem item = setHotbarRequestItems[index];
								hotbar.items[index] = item != null ? new Hotbar.Item(item.id, item.count, item.instanceId) : null;
							}
							hotbar.limitToInventory((Inventory) results1.get("inventory").value());
							return new EarthDB.Query(true)
									.update("hotbar", playerId, hotbar)
									.get("inventory", playerId, Inventory.class)
									.get("hotbar", playerId, Hotbar.class);
						})
						.execute(earthDB);
				inventoryModel = (Inventory) results.get("inventory").value();
				hotbarModel = (Hotbar) results.get("hotbar").value();
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			HotbarItem[] hotbarItems = Arrays.stream(hotbarModel.items).map(item -> item != null ? new HotbarItem(
					item.uuid(),
					item.count(),
					item.instanceId(),
					item.instanceId() != null ? inventoryModel.getItemInstance(item.uuid(), item.instanceId()).health() : 0.0f
			) : null).toArray(HotbarItem[]::new);
			return Response.okFromJson(hotbarItems, HotbarItem[].class);
		});
	}
}