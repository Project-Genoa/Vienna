package micheal65536.vienna.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.inventory.HotbarItem;
import micheal65536.vienna.apiserver.types.inventory.NonStackableInventoryItem;
import micheal65536.vienna.apiserver.types.inventory.StackableInventoryItem;
import micheal65536.vienna.apiserver.utils.BoostUtils;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.ItemWear;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.apiserver.utils.TokenUtils;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.common.NonStackableItemInstance;
import micheal65536.vienna.db.model.player.Boosts;
import micheal65536.vienna.db.model.player.Hotbar;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Journal;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.db.model.player.Tokens;
import micheal65536.vienna.staticdata.Catalog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class InventoryRouter extends Router
{
	public InventoryRouter(@NotNull EarthDB earthDB, @NotNull Catalog catalog)
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

			HashMap<String, Integer> hotbarItemCounts = new HashMap<>();
			Arrays.stream(hotbarModel.items).forEach(item ->
			{
				if (item != null)
				{
					hotbarItemCounts.put(item.uuid(), hotbarItemCounts.getOrDefault(item.uuid(), 0) + item.count());
				}
			});
			HashSet<String> hotbarItemInstances = new HashSet<>();
			Arrays.stream(hotbarModel.items).forEach(item ->
			{
				if (item != null && item.instanceId() != null)
				{
					hotbarItemInstances.add(item.instanceId());
				}
			});
			micheal65536.vienna.apiserver.types.inventory.Inventory inventory = new micheal65536.vienna.apiserver.types.inventory.Inventory(
					Arrays.stream(hotbarModel.items).map(item -> item != null ? new HotbarItem(
							item.uuid(),
							item.count(),
							item.instanceId(),
							item.instanceId() != null ? ItemWear.wearToHealth(item.uuid(), inventoryModel.getItemInstance(item.uuid(), item.instanceId()).wear(), catalog.itemsCatalog) : 0.0f
					) : null).toArray(HotbarItem[]::new),
					Arrays.stream(inventoryModel.getStackableItems()).map(item ->
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
					}).toArray(StackableInventoryItem[]::new),
					Arrays.stream(inventoryModel.getNonStackableItems()).map(item ->
					{
						String uuid = item.id();
						Journal.ItemJournalEntry itemJournalEntry = journalModel.getItem(uuid);
						String firstSeen = TimeFormatter.formatTime(itemJournalEntry.firstSeen());
						String lastSeen = TimeFormatter.formatTime(itemJournalEntry.lastSeen());
						return new NonStackableInventoryItem(
								uuid,
								Arrays.stream(item.instances()).filter(instance -> !hotbarItemInstances.contains(instance.instanceId())).map(instance -> new NonStackableInventoryItem.Instance(instance.instanceId(), ItemWear.wearToHealth(item.id(), instance.wear(), catalog.itemsCatalog))).toArray(NonStackableInventoryItem.Instance[]::new),
								1,
								new NonStackableInventoryItem.On(firstSeen),
								new NonStackableInventoryItem.On(lastSeen)
						);
					}).toArray(NonStackableInventoryItem[]::new)
			);

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
					item.instanceId() != null ? ItemWear.wearToHealth(item.uuid(), inventoryModel.getItemInstance(item.uuid(), item.instanceId()).wear(), catalog.itemsCatalog) : 0.0f
			) : null).toArray(HotbarItem[]::new);
			return Response.okFromJson(hotbarItems, HotbarItem[].class);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/inventory/survival/$itemId/consume").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			String itemId = request.getParameter("itemId");

			Catalog.ItemsCatalog.Item item = catalog.itemsCatalog.getItem(itemId);
			if (item == null || item.consumeInfo() == null)
			{
				return Response.badRequest();
			}

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.get("journal", playerId, Journal.class)
						.get("profile", playerId, Profile.class)
						.get("boosts", playerId, Boosts.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();
							Profile profile = (Profile) results1.get("profile").value();
							Boosts boosts = (Boosts) results1.get("boosts").value();

							EarthDB.Query query = new EarthDB.Query(true);

							if (!inventory.takeItems(itemId, 1))
							{
								return new EarthDB.Query(false);
							}

							String returnItemId = item.consumeInfo().returnItemId();
							if (returnItemId != null)
							{
								Catalog.ItemsCatalog.Item returnItem = catalog.itemsCatalog.getItem(returnItemId);
								if (returnItem.stackable())
								{
									inventory.addItems(returnItemId, 1);
								}
								else
								{
									inventory.addItems(returnItemId, new NonStackableItemInstance[]{new NonStackableItemInstance(UUID.randomUUID().toString(), 0)});
								}
								if (journal.addCollectedItem(returnItemId, request.timestamp, 1) == 0)
								{
									if (returnItem.journalEntry() != null)
									{
										query.then(TokenUtils.addToken(playerId, new Tokens.JournalItemUnlockedToken(returnItemId)));
									}
								}
							}

							int maxPlayerHealth = BoostUtils.getMaxPlayerHealth(boosts, request.timestamp, catalog.itemsCatalog);
							profile.health += item.consumeInfo().heal();
							if (profile.health > maxPlayerHealth)
							{
								profile.health = maxPlayerHealth;
							}

							query.update("inventory", playerId, inventory).update("journal", playerId, journal).update("profile", playerId, profile);

							return query;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(null, new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
	}
}