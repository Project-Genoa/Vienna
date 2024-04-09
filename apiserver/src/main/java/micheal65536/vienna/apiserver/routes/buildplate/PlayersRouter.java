package micheal65536.vienna.apiserver.routes.buildplate;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.catalog.ItemsCatalog;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.common.NonStackableItemInstance;
import micheal65536.vienna.db.model.player.Hotbar;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Journal;

import java.util.Arrays;
import java.util.stream.Stream;

public class PlayersRouter extends Router
{
	public PlayersRouter(@NotNull EarthDB earthDB, @NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.POST, "/join/$instanceId").build(), request ->
		{
			record JoinRequest(
					@NotNull String uuid,
					@NotNull String joinCode
			)
			{
			}

			record JoinResponse(
					boolean accepted,
					Inventory inventory
			)
			{
				public record Inventory(
						@NotNull Item[] items,
						HotbarItem[] hotbar
				)
				{
					public record Item(
							@NotNull String id,
							int count,
							@Nullable String instanceId,
							int wear
					)
					{
					}

					public record HotbarItem(
							@NotNull String id,
							int count,
							@Nullable String instanceId
					)
					{
					}
				}
			}

			String instanceId = request.getParameter("instanceId");
			JoinRequest joinRequest = request.getBodyAsJson(JoinRequest.class);
			String playerId = joinRequest.uuid;

			// TODO: check join code etc.

			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("inventory", playerId, Inventory.class)
						.get("hotbar", playerId, Hotbar.class)
						.execute(earthDB);
				Inventory inventory = (Inventory) results.get("inventory").value();
				Hotbar hotbar = (Hotbar) results.get("hotbar").value();

				JoinResponse joinResponse = new JoinResponse(
						true,
						new JoinResponse.Inventory(
								Stream.concat(
										Arrays.stream(inventory.getStackableItems())
												.map(item -> new JoinResponse.Inventory.Item(item.id(), item.count(), null, 0)),
										Arrays.stream(inventory.getNonStackableItems())
												.mapMulti((item, consumer) -> Arrays.stream(item.instances())
														.map(instance -> new JoinResponse.Inventory.Item(item.id(), 1, instance.instanceId(), instance.wear()))
														.forEach(consumer))
								).filter(item -> item.count > 0).toArray(JoinResponse.Inventory.Item[]::new),
								Arrays.stream(hotbar.items).map(item -> item != null && item.count() > 0 ? new JoinResponse.Inventory.HotbarItem(item.uuid(), item.count(), item.instanceId()) : null).toArray(JoinResponse.Inventory.HotbarItem[]::new)
						)
				);

				return Response.okFromJson(joinResponse, JoinResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/leave/$instanceId/$playerId").build(), request ->
		{
			// TODO

			record LeaveResponse(
					// TODO
			)
			{
			}

			String instanceId = request.getParameter("instanceId");
			String playerId = request.getParameter("playerId");

			return Response.okFromJson(new LeaveResponse(), LeaveResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/inventory/$instanceId/$playerId/add").build(), request ->
		{
			record AddItemRequest(
					@NotNull String itemId,
					int count,
					@Nullable String instanceId,
					int wear
			)
			{
			}

			String instanceId = request.getParameter("instanceId");
			String playerId = request.getParameter("playerId");
			AddItemRequest addItemRequest = request.getBodyAsJson(AddItemRequest.class);

			ItemsCatalog.Item catalogItem = Arrays.stream(catalog.itemsCatalog.items()).filter(item -> item.id().equals(addItemRequest.itemId)).findFirst().orElse(null);
			if (catalogItem == null)
			{
				return Response.badRequest();
			}
			if (!catalogItem.stacks() && addItemRequest.instanceId == null)
			{
				return Response.badRequest();
			}

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.get("journal", playerId, Journal.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (catalogItem.stacks())
							{
								inventory.addItems(addItemRequest.itemId, addItemRequest.count);
							}
							else
							{
								inventory.addItems(addItemRequest.itemId, new NonStackableItemInstance[]{new NonStackableItemInstance(addItemRequest.instanceId, addItemRequest.wear)});
							}

							journal.touchItem(addItemRequest.itemId, request.timestamp);

							return new EarthDB.Query(true)
									.update("inventory", playerId, inventory)
									.update("journal", playerId, journal);
						})
						.execute(earthDB);
				return Response.ok("");
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/inventory/$instanceId/$playerId/remove").build(), request ->
		{
			record RemoveItemRequest(
					@NotNull String itemId,
					int count,
					@Nullable String instanceId
			)
			{
			}

			String instanceId = request.getParameter("instanceId");
			String playerId = request.getParameter("playerId");
			RemoveItemRequest removeItemRequest = request.getBodyAsJson(RemoveItemRequest.class);

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.get("hotbar", playerId, Hotbar.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

							if (removeItemRequest.instanceId != null)
							{
								if (inventory.takeItems(removeItemRequest.itemId, new String[]{removeItemRequest.instanceId}) == null)
								{
									LogManager.getLogger().warn("Buildplate instance {} attempted to remove item {} {} from player {} that is not in inventory", instanceId, removeItemRequest.itemId, removeItemRequest.instanceId, playerId);
								}
							}
							else
							{
								if (!inventory.takeItems(removeItemRequest.itemId, removeItemRequest.count))
								{
									int count = inventory.getItemCount(removeItemRequest.itemId);
									if (!inventory.takeItems(removeItemRequest.itemId, count))
									{
										count = 0;
									}
									LogManager.getLogger().warn("Buildplate instance {} attempted to remove item {} {} from player {} that is not in inventory", instanceId, removeItemRequest.itemId, removeItemRequest.count - count, playerId);
								}
							}

							hotbar.limitToInventory(inventory);

							return new EarthDB.Query(true)
									.update("inventory", playerId, inventory)
									.update("hotbar", playerId, hotbar);
						})
						.execute(earthDB);
				return Response.ok("");
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/inventory/$instanceId/$playerId/updateWear").build(), request ->
		{
			record UpdateWearRequest(
					@NotNull String itemId,
					@NotNull String instanceId,
					int wear
			)
			{
			}

			String instanceId = request.getParameter("instanceId");
			String playerId = request.getParameter("playerId");
			UpdateWearRequest updateWearRequest = request.getBodyAsJson(UpdateWearRequest.class);

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();

							NonStackableItemInstance nonStackableItemInstance = inventory.getItemInstance(updateWearRequest.itemId, updateWearRequest.instanceId);
							if (nonStackableItemInstance != null)
							{
								// TODO: make NonStackableItemInstance mutable instead of doing this
								if (inventory.takeItems(updateWearRequest.itemId, new String[]{updateWearRequest.instanceId}) == null)
								{
									throw new AssertionError();
								}
								inventory.addItems(updateWearRequest.itemId, new NonStackableItemInstance[]{new NonStackableItemInstance(updateWearRequest.instanceId, updateWearRequest.wear)});
							}
							else
							{
								LogManager.getLogger().warn("Buildplate instance {} attempted to update item wear for item {} {} player {} that is not in inventory", instanceId, updateWearRequest.itemId, updateWearRequest.instanceId, playerId);
							}

							return new EarthDB.Query(true)
									.update("inventory", playerId, inventory);
						})
						.execute(earthDB);
				return Response.ok("");
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/inventory/$instanceId/$playerId/hotbar").build(), request ->
		{
			record SetHotbarRequest(
					Item[] items
			)
			{
				public record Item(
						@NotNull String itemId,
						int count,
						@Nullable String instanceId
				)
				{
				}
			}

			String instanceId = request.getParameter("instanceId");
			String playerId = request.getParameter("playerId");
			SetHotbarRequest setHotbarRequest = request.getBodyAsJson(SetHotbarRequest.class);

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();

							Hotbar hotbar = new Hotbar();
							for (int index = 0; index < hotbar.items.length; index++)
							{
								SetHotbarRequest.Item item = setHotbarRequest.items[index];
								hotbar.items[index] = item != null ? new Hotbar.Item(item.itemId, item.count, item.instanceId) : null;
							}

							hotbar.limitToInventory(inventory);

							return new EarthDB.Query(true)
									.update("hotbar", playerId, hotbar);
						})
						.execute(earthDB);
				return Response.ok("");
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
	}
}