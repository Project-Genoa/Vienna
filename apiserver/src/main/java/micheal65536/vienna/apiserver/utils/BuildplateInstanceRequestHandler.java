package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.apiserver.types.catalog.ItemsCatalog;
import micheal65536.vienna.buildplate.connector.model.InventoryAddItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryRemoveItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventorySetHotbarMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryUpdateItemWearMessage;
import micheal65536.vienna.buildplate.connector.model.PlayerConnectedRequest;
import micheal65536.vienna.buildplate.connector.model.PlayerConnectedResponse;
import micheal65536.vienna.buildplate.connector.model.PlayerDisconnectedRequest;
import micheal65536.vienna.buildplate.connector.model.PlayerDisconnectedResponse;
import micheal65536.vienna.buildplate.connector.model.WorldSavedMessage;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.common.NonStackableItemInstance;
import micheal65536.vienna.db.model.player.Buildplates;
import micheal65536.vienna.db.model.player.Hotbar;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Journal;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestHandler;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Stream;

public final class BuildplateInstanceRequestHandler
{
	public static void start(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull Catalog catalog)
	{
		new BuildplateInstanceRequestHandler(earthDB, eventBusClient, objectStoreClient, catalog);
	}

	private final EarthDB earthDB;
	private final ObjectStoreClient objectStoreClient;
	private final Catalog catalog;
	private final BuildplateInstancesManager buildplateInstancesManager;

	private BuildplateInstanceRequestHandler(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull Catalog catalog)
	{
		this.earthDB = earthDB;
		this.objectStoreClient = objectStoreClient;
		this.catalog = catalog;
		this.buildplateInstancesManager = new BuildplateInstancesManager(eventBusClient);    // TODO: would be nicer to use the same instance as BuildplatesRouter

		RequestHandler requestHandler = eventBusClient.addRequestHandler("buildplates", new RequestHandler.Handler()
		{
			@Override
			@Nullable
			public String request(@NotNull RequestHandler.Request request)
			{
				try
				{
					switch (request.type)
					{
						case "load" ->
						{

							BuildplateLoadRequest buildplateLoadRequest = readRawRequest(request.data, BuildplateLoadRequest.class);
							if (buildplateLoadRequest == null)
							{
								return null;
							}
							BuildplateLoadResponse buildplateLoadResponse = BuildplateInstanceRequestHandler.this.handleLoad(buildplateLoadRequest.playerId, buildplateLoadRequest.buildplateId);
							return buildplateLoadResponse != null ? new Gson().newBuilder().serializeNulls().create().toJson(buildplateLoadResponse) : null;
						}
						case "saved" ->
						{
							RequestWithBuildplateId<WorldSavedMessage> requestWithBuildplateId = readRequest(request.data, WorldSavedMessage.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleSaved(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request.dataBase64(), request.timestamp) ? "" : null;
						}
						case "playerConnected" ->
						{
							RequestWithBuildplateId<PlayerConnectedRequest> requestWithBuildplateId = readRequest(request.data, PlayerConnectedRequest.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							PlayerConnectedResponse playerConnectedResponse = BuildplateInstanceRequestHandler.this.handlePlayerConnected(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request);
							return playerConnectedResponse != null ? new Gson().newBuilder().serializeNulls().create().toJson(playerConnectedResponse) : null;
						}
						case "playerDisconnected" ->
						{
							RequestWithBuildplateId<PlayerDisconnectedRequest> requestWithBuildplateId = readRequest(request.data, PlayerDisconnectedRequest.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							PlayerDisconnectedResponse playerDisconnectedResponse = BuildplateInstanceRequestHandler.this.handlePlayerDisconnected(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request);
							return playerDisconnectedResponse != null ? new Gson().newBuilder().serializeNulls().create().toJson(playerDisconnectedResponse) : null;
						}
						case "inventoryAdd" ->
						{
							RequestWithBuildplateId<InventoryAddItemMessage> requestWithBuildplateId = readRequest(request.data, InventoryAddItemMessage.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventoryAdd(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request, request.timestamp) ? "" : null;
						}
						case "inventoryRemove" ->
						{
							RequestWithBuildplateId<InventoryRemoveItemMessage> requestWithBuildplateId = readRequest(request.data, InventoryRemoveItemMessage.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventoryRemove(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request) ? "" : null;
						}
						case "inventoryUpdateWear" ->
						{
							RequestWithBuildplateId<InventoryUpdateItemWearMessage> requestWithBuildplateId = readRequest(request.data, InventoryUpdateItemWearMessage.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventoryUpdateWear(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request) ? "" : null;
						}
						case "inventorySetHotbar" ->
						{
							RequestWithBuildplateId<InventorySetHotbarMessage> requestWithBuildplateId = readRequest(request.data, InventorySetHotbarMessage.class);
							if (requestWithBuildplateId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventorySetHotbar(requestWithBuildplateId.playerId, requestWithBuildplateId.buildplateId, requestWithBuildplateId.instanceId, requestWithBuildplateId.request) ? "" : null;
						}
						default ->
						{
							return null;
						}
					}
				}
				catch (DatabaseException exception)
				{
					LogManager.getLogger().error("Database error while handling request", exception);
					return null;
				}
			}

			@Override
			public void error()
			{
				LogManager.getLogger().fatal("Buildplates event bus request handler error");
				System.exit(1);
			}
		});
	}

	private record BuildplateLoadRequest(
			@NotNull String playerId,
			@NotNull String buildplateId
	)
	{
	}

	private record BuildplateLoadResponse(
			@NotNull String serverDataBase64
	)
	{
	}

	@Nullable
	private BuildplateLoadResponse handleLoad(@NotNull String playerId, @NotNull String buildplateId) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(false)
				.get("buildplates", playerId, Buildplates.class)
				.execute(this.earthDB);
		Buildplates buildplates = (Buildplates) results.get("buildplates").value();

		Buildplates.Buildplate buildplate = buildplates.getBuildplate(buildplateId);
		if (buildplate == null)
		{
			return null;
		}

		byte[] serverData = this.objectStoreClient.get(buildplate.serverDataObjectId).join();
		if (serverData == null)
		{
			LogManager.getLogger().error("Data object {} for buildplate {} could not be loaded from object store", buildplate.serverDataObjectId, buildplateId);
			return null;
		}
		String serverDataBase64 = Base64.getEncoder().encodeToString(serverData);

		return new BuildplateLoadResponse(serverDataBase64);
	}

	private boolean handleSaved(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull String dataBase64, long timestamp) throws DatabaseException
	{
		byte[] serverData;
		try
		{
			serverData = Base64.getDecoder().decode(dataBase64);
		}
		catch (IllegalArgumentException exception)
		{
			return false;
		}

		EarthDB.Results results = new EarthDB.Query(false)
				.get("buildplates", playerId, Buildplates.class)
				.execute(this.earthDB);
		Buildplates.Buildplate buildplateUnsafeForPreviewGenerator = ((Buildplates) results.get("buildplates").value()).getBuildplate(buildplateId);
		if (buildplateUnsafeForPreviewGenerator == null)
		{
			return false;
		}

		String preview = this.buildplateInstancesManager.getBuildplatePreview(serverData, buildplateUnsafeForPreviewGenerator.night);
		if (preview == null)
		{
			LogManager.getLogger().warn("Could not get preview for buildplate");
		}

		String serverDataObjectId = this.objectStoreClient.store(serverData).join();
		if (serverDataObjectId == null)
		{
			LogManager.getLogger().error("Could not store new data object for buildplate {} in object store", buildplateId);
			return false;
		}
		String previewObjectId;
		if (preview != null)
		{
			previewObjectId = this.objectStoreClient.store(preview.getBytes(StandardCharsets.US_ASCII)).join();
			if (previewObjectId == null)
			{
				LogManager.getLogger().warn("Could not store new preview object for buildplate {} in object store", buildplateId);
			}
		}
		else
		{
			previewObjectId = null;
		}

		try
		{
			EarthDB.Results results1 = new EarthDB.Query(true)
					.get("buildplates", playerId, Buildplates.class)
					.then(results2 ->
					{
						Buildplates buildplates = (Buildplates) results2.get("buildplates").value();
						Buildplates.Buildplate buildplate = buildplates.getBuildplate(buildplateId);
						if (buildplate != null)
						{
							buildplate.lastModified = timestamp;

							String oldServerDataObjectId = buildplate.serverDataObjectId;
							buildplate.serverDataObjectId = serverDataObjectId;

							String oldPreviewObjectId;
							if (previewObjectId != null)
							{
								oldPreviewObjectId = buildplate.previewObjectId;
								buildplate.previewObjectId = previewObjectId;
							}
							else
							{
								oldPreviewObjectId = "";
							}

							return new EarthDB.Query(true)
									.update("buildplates", playerId, buildplates)
									.extra("exists", true)
									.extra("oldServerDataObjectId", oldServerDataObjectId)
									.extra("oldPreviewObjectId", oldPreviewObjectId);
						}
						else
						{
							return new EarthDB.Query(false)
									.extra("exists", false);
						}
					})
					.execute(this.earthDB);

			boolean exists = results1.getExtra("exists");
			if (exists)
			{
				String oldServerDataObjectId = results1.getExtra("oldServerDataObjectId");
				this.objectStoreClient.delete(oldServerDataObjectId);

				String oldPreviewObjectId = results1.getExtra("oldPreviewObjectId");
				if (!oldPreviewObjectId.isEmpty())
				{
					this.objectStoreClient.delete(oldPreviewObjectId);
				}

				LogManager.getLogger().info("Stored new snapshot for buildplate {}", buildplateId);

				return true;
			}
			else
			{
				this.objectStoreClient.delete(serverDataObjectId);
				return false;
			}
		}
		catch (DatabaseException exception)
		{
			this.objectStoreClient.delete(serverDataObjectId);

			throw exception;
		}
	}

	@Nullable
	private PlayerConnectedResponse handlePlayerConnected(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull PlayerConnectedRequest playerConnectedRequest) throws DatabaseException
	{
		// TODO: check join code etc.

		EarthDB.Results results = new EarthDB.Query(false)
				.get("inventory", playerConnectedRequest.uuid(), Inventory.class)
				.get("hotbar", playerConnectedRequest.uuid(), Hotbar.class)
				.execute(this.earthDB);
		Inventory inventory = (Inventory) results.get("inventory").value();
		Hotbar hotbar = (Hotbar) results.get("hotbar").value();

		PlayerConnectedResponse playerConnectedResponse = new PlayerConnectedResponse(
				true,
				new PlayerConnectedResponse.Inventory(
						Stream.concat(
								Arrays.stream(inventory.getStackableItems())
										.map(item -> new PlayerConnectedResponse.Inventory.Item(item.id(), item.count(), null, 0)),
								Arrays.stream(inventory.getNonStackableItems())
										.mapMulti((item, consumer) -> Arrays.stream(item.instances())
												.map(instance -> new PlayerConnectedResponse.Inventory.Item(item.id(), 1, instance.instanceId(), instance.wear()))
												.forEach(consumer))
						).filter(item -> item.count() > 0).toArray(PlayerConnectedResponse.Inventory.Item[]::new),
						Arrays.stream(hotbar.items).map(item -> item != null && item.count() > 0 ? new PlayerConnectedResponse.Inventory.HotbarItem(item.uuid(), item.count(), item.instanceId()) : null).toArray(PlayerConnectedResponse.Inventory.HotbarItem[]::new)
				)
		);

		return playerConnectedResponse;
	}

	@Nullable
	private PlayerDisconnectedResponse handlePlayerDisconnected(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull PlayerDisconnectedRequest playerDisconnectedRequest) throws DatabaseException
	{
		// TODO

		return new PlayerDisconnectedResponse();
	}

	private boolean handleInventoryAdd(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull InventoryAddItemMessage inventoryAddItemMessage, long timestamp) throws DatabaseException
	{
		ItemsCatalog.Item catalogItem = Arrays.stream(this.catalog.itemsCatalog.items()).filter(item -> item.id().equals(inventoryAddItemMessage.itemId())).findFirst().orElse(null);
		if (catalogItem == null)
		{
			return false;
		}
		if (!catalogItem.stacks() && inventoryAddItemMessage.instanceId() == null)
		{
			return false;
		}

		EarthDB.Results results = new EarthDB.Query(true)
				.get("inventory", inventoryAddItemMessage.playerId(), Inventory.class)
				.get("journal", inventoryAddItemMessage.playerId(), Journal.class)
				.then(results1 ->
				{
					Inventory inventory = (Inventory) results1.get("inventory").value();
					Journal journal = (Journal) results1.get("journal").value();

					if (catalogItem.stacks())
					{
						inventory.addItems(inventoryAddItemMessage.itemId(), inventoryAddItemMessage.count());
					}
					else
					{
						inventory.addItems(inventoryAddItemMessage.itemId(), new NonStackableItemInstance[]{new NonStackableItemInstance(inventoryAddItemMessage.instanceId(), inventoryAddItemMessage.wear())});
					}

					journal.touchItem(inventoryAddItemMessage.itemId(), timestamp);

					return new EarthDB.Query(true)
							.update("inventory", inventoryAddItemMessage.playerId(), inventory)
							.update("journal", inventoryAddItemMessage.playerId(), journal);
				})
				.execute(this.earthDB);
		return true;
	}

	private boolean handleInventoryRemove(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull InventoryRemoveItemMessage inventoryRemoveItemMessage) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(true)
				.get("inventory", inventoryRemoveItemMessage.playerId(), Inventory.class)
				.get("hotbar", inventoryRemoveItemMessage.playerId(), Hotbar.class)
				.then(results1 ->
				{
					Inventory inventory = (Inventory) results1.get("inventory").value();
					Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

					if (inventoryRemoveItemMessage.instanceId() != null)
					{
						if (inventory.takeItems(inventoryRemoveItemMessage.itemId(), new String[]{inventoryRemoveItemMessage.instanceId()}) == null)
						{
							LogManager.getLogger().warn("Buildplate instance {} attempted to remove item {} {} from player {} that is not in inventory", instanceId, inventoryRemoveItemMessage.itemId(), inventoryRemoveItemMessage.instanceId(), inventoryRemoveItemMessage.playerId());
						}
					}
					else
					{
						if (!inventory.takeItems(inventoryRemoveItemMessage.itemId(), inventoryRemoveItemMessage.count()))
						{
							int count = inventory.getItemCount(inventoryRemoveItemMessage.itemId());
							if (!inventory.takeItems(inventoryRemoveItemMessage.itemId(), count))
							{
								count = 0;
							}
							LogManager.getLogger().warn("Buildplate instance {} attempted to remove item {} {} from player {} that is not in inventory", instanceId, inventoryRemoveItemMessage.itemId(), inventoryRemoveItemMessage.count() - count, inventoryRemoveItemMessage.playerId());
						}
					}

					hotbar.limitToInventory(inventory);

					return new EarthDB.Query(true)
							.update("inventory", inventoryRemoveItemMessage.playerId(), inventory)
							.update("hotbar", inventoryRemoveItemMessage.playerId(), hotbar);
				})
				.execute(this.earthDB);
		return true;
	}

	private boolean handleInventoryUpdateWear(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull InventoryUpdateItemWearMessage inventoryUpdateItemWearMessage) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(true)
				.get("inventory", inventoryUpdateItemWearMessage.playerId(), Inventory.class)
				.then(results1 ->
				{
					Inventory inventory = (Inventory) results1.get("inventory").value();

					NonStackableItemInstance nonStackableItemInstance = inventory.getItemInstance(inventoryUpdateItemWearMessage.itemId(), inventoryUpdateItemWearMessage.instanceId());
					if (nonStackableItemInstance != null)
					{
						// TODO: make NonStackableItemInstance mutable instead of doing this
						if (inventory.takeItems(inventoryUpdateItemWearMessage.itemId(), new String[]{inventoryUpdateItemWearMessage.instanceId()}) == null)
						{
							throw new AssertionError();
						}
						inventory.addItems(inventoryUpdateItemWearMessage.itemId(), new NonStackableItemInstance[]{new NonStackableItemInstance(inventoryUpdateItemWearMessage.instanceId(), inventoryUpdateItemWearMessage.wear())});
					}
					else
					{
						LogManager.getLogger().warn("Buildplate instance {} attempted to update item wear for item {} {} player {} that is not in inventory", instanceId, inventoryUpdateItemWearMessage.itemId(), inventoryUpdateItemWearMessage.instanceId(), inventoryUpdateItemWearMessage.playerId());
					}

					return new EarthDB.Query(true)
							.update("inventory", inventoryUpdateItemWearMessage.playerId(), inventory);
				})
				.execute(this.earthDB);
		return true;
	}

	private boolean handleInventorySetHotbar(@NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, @NotNull InventorySetHotbarMessage inventorySetHotbarMessage) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(true)
				.get("inventory", inventorySetHotbarMessage.playerId(), Inventory.class)
				.then(results1 ->
				{
					Inventory inventory = (Inventory) results1.get("inventory").value();

					Hotbar hotbar = new Hotbar();
					for (int index = 0; index < hotbar.items.length; index++)
					{
						InventorySetHotbarMessage.Item item = inventorySetHotbarMessage.items()[index];
						hotbar.items[index] = item != null ? new Hotbar.Item(item.itemId(), item.count(), item.instanceId()) : null;
					}

					hotbar.limitToInventory(inventory);

					return new EarthDB.Query(true)
							.update("hotbar", inventorySetHotbarMessage.playerId(), hotbar);
				})
				.execute(this.earthDB);
		return true;
	}

	@Nullable
	private static <T> RequestWithBuildplateId<T> readRequest(@NotNull String string, @NotNull Class<T> requestClass)
	{
		try
		{
			TypeToken<?> typeToken = TypeToken.getParameterized(RequestWithBuildplateId.class, requestClass);
			RequestWithBuildplateId<T> request = (RequestWithBuildplateId<T>) new Gson().fromJson(string, typeToken);
			return request;
		}
		catch (Exception exception)
		{
			LogManager.getLogger().error("Bad JSON in buildplates event bus request", exception);
			return null;
		}
	}

	@Nullable
	private static <T> T readRawRequest(@NotNull String string, @NotNull Class<T> requestClass)
	{
		try
		{
			T request = new Gson().fromJson(string, requestClass);
			return request;
		}
		catch (Exception exception)
		{
			LogManager.getLogger().error("Bad JSON in buildplates event bus request", exception);
			return null;
		}
	}

	private record RequestWithBuildplateId<T>(
			@NotNull String playerId,
			@NotNull String buildplateId,
			@NotNull String instanceId,
			@NotNull T request
	)
	{
	}
}