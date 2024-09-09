package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.buildplate.connector.model.InitialPlayerStateResponse;
import micheal65536.vienna.buildplate.connector.model.InventoryAddItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryRemoveItemRequest;
import micheal65536.vienna.buildplate.connector.model.InventoryResponse;
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
import micheal65536.vienna.db.model.global.EncounterBuildplates;
import micheal65536.vienna.db.model.global.SharedBuildplates;
import micheal65536.vienna.db.model.player.Boosts;
import micheal65536.vienna.db.model.player.Buildplates;
import micheal65536.vienna.db.model.player.Hotbar;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Journal;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.db.model.player.Tokens;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestHandler;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;
import micheal65536.vienna.staticdata.Catalog;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Stream;

public final class BuildplateInstanceRequestHandler
{
	public static void start(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull BuildplateInstancesManager buildplateInstancesManager, @NotNull Catalog catalog)
	{
		new BuildplateInstanceRequestHandler(earthDB, eventBusClient, objectStoreClient, buildplateInstancesManager, catalog);
	}

	private final EarthDB earthDB;
	private final ObjectStoreClient objectStoreClient;
	private final BuildplateInstancesManager buildplateInstancesManager;
	private final Catalog catalog;

	private BuildplateInstanceRequestHandler(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull BuildplateInstancesManager buildplateInstancesManager, @NotNull Catalog catalog)
	{
		this.earthDB = earthDB;
		this.objectStoreClient = objectStoreClient;
		this.buildplateInstancesManager = buildplateInstancesManager;
		this.catalog = catalog;

		RequestHandler requestHandler = eventBusClient.addRequestHandler("buildplates", new RequestHandler.Handler()
		{
			@Override
			@Nullable
			public String request(@NotNull RequestHandler.Request request)
			{
				try
				{
					Gson gson = new Gson().newBuilder().serializeNulls().create();
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
							return buildplateLoadResponse != null ? gson.toJson(buildplateLoadResponse) : null;
						}
						case "loadShared" ->
						{
							SharedBuildplateLoadRequest sharedBuildplateLoadRequest = readRawRequest(request.data, SharedBuildplateLoadRequest.class);
							if (sharedBuildplateLoadRequest == null)
							{
								return null;
							}
							BuildplateLoadResponse buildplateLoadResponse = BuildplateInstanceRequestHandler.this.handleLoadShared(sharedBuildplateLoadRequest.sharedBuildplateId);
							return buildplateLoadResponse != null ? gson.toJson(buildplateLoadResponse) : null;
						}
						case "loadEncounter" ->
						{
							EncounterBuildplateLoadRequest encounterBuildplateLoadRequest = readRawRequest(request.data, EncounterBuildplateLoadRequest.class);
							if (encounterBuildplateLoadRequest == null)
							{
								return null;
							}
							BuildplateLoadResponse buildplateLoadResponse = BuildplateInstanceRequestHandler.this.handleLoadEncounter(encounterBuildplateLoadRequest.encounterBuildplateId);
							return buildplateLoadResponse != null ? gson.toJson(buildplateLoadResponse) : null;
						}
						case "saved" ->
						{
							RequestWithInstanceId<WorldSavedMessage> requestWithInstanceId = readRequest(request.data, WorldSavedMessage.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleSaved(requestWithInstanceId.instanceId, requestWithInstanceId.request.dataBase64(), request.timestamp) ? "" : null;
						}
						case "playerConnected" ->
						{
							RequestWithInstanceId<PlayerConnectedRequest> requestWithInstanceId = readRequest(request.data, PlayerConnectedRequest.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							PlayerConnectedResponse playerConnectedResponse = BuildplateInstanceRequestHandler.this.handlePlayerConnected(requestWithInstanceId.instanceId, requestWithInstanceId.request);
							return playerConnectedResponse != null ? gson.toJson(playerConnectedResponse) : null;
						}
						case "playerDisconnected" ->
						{
							RequestWithInstanceId<PlayerDisconnectedRequest> requestWithInstanceId = readRequest(request.data, PlayerDisconnectedRequest.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							PlayerDisconnectedResponse playerDisconnectedResponse = BuildplateInstanceRequestHandler.this.handlePlayerDisconnected(requestWithInstanceId.instanceId, requestWithInstanceId.request, request.timestamp);
							return playerDisconnectedResponse != null ? gson.toJson(playerDisconnectedResponse) : null;
						}
						case "getInitialPlayerState" ->
						{
							RequestWithInstanceId<String> requestWithInstanceId = readRequest(request.data, String.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							InitialPlayerStateResponse initialPlayerStateResponse = BuildplateInstanceRequestHandler.this.handleGetInitialPlayerState(requestWithInstanceId.instanceId, requestWithInstanceId.request, request.timestamp);
							return initialPlayerStateResponse != null ? gson.toJson(initialPlayerStateResponse) : null;
						}
						case "getInventory" ->
						{
							RequestWithInstanceId<String> requestWithInstanceId = readRequest(request.data, String.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							InventoryResponse inventoryResponse = BuildplateInstanceRequestHandler.this.handleGetInventory(requestWithInstanceId.instanceId, requestWithInstanceId.request);
							return inventoryResponse != null ? gson.toJson(inventoryResponse) : null;
						}
						case "inventoryAdd" ->
						{
							RequestWithInstanceId<InventoryAddItemMessage> requestWithInstanceId = readRequest(request.data, InventoryAddItemMessage.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventoryAdd(requestWithInstanceId.instanceId, requestWithInstanceId.request, request.timestamp) ? "" : null;
						}
						case "inventoryRemove" ->
						{
							RequestWithInstanceId<InventoryRemoveItemRequest> requestWithInstanceId = readRequest(request.data, InventoryRemoveItemRequest.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							Object response = BuildplateInstanceRequestHandler.this.handleInventoryRemove(requestWithInstanceId.instanceId, requestWithInstanceId.request);
							return response != null ? new Gson().toJson(response) : null;
						}
						case "inventoryUpdateWear" ->
						{
							RequestWithInstanceId<InventoryUpdateItemWearMessage> requestWithInstanceId = readRequest(request.data, InventoryUpdateItemWearMessage.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventoryUpdateWear(requestWithInstanceId.instanceId, requestWithInstanceId.request) ? "" : null;
						}
						case "inventorySetHotbar" ->
						{
							RequestWithInstanceId<InventorySetHotbarMessage> requestWithInstanceId = readRequest(request.data, InventorySetHotbarMessage.class);
							if (requestWithInstanceId == null)
							{
								return null;
							}
							return BuildplateInstanceRequestHandler.this.handleInventorySetHotbar(requestWithInstanceId.instanceId, requestWithInstanceId.request) ? "" : null;
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

	private record SharedBuildplateLoadRequest(
			@NotNull String sharedBuildplateId
	)
	{
	}

	private record EncounterBuildplateLoadRequest(
			@NotNull String encounterBuildplateId
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

	@Nullable
	private BuildplateLoadResponse handleLoadShared(@NotNull String sharedBuildplateId) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(false)
				.get("sharedBuildplates", "", SharedBuildplates.class)
				.execute(this.earthDB);
		SharedBuildplates sharedBuildplates = (SharedBuildplates) results.get("sharedBuildplates").value();

		SharedBuildplates.SharedBuildplate sharedBuildplate = sharedBuildplates.getSharedBuildplate(sharedBuildplateId);
		if (sharedBuildplate == null)
		{
			return null;
		}

		byte[] serverData = this.objectStoreClient.get(sharedBuildplate.serverDataObjectId).join();
		if (serverData == null)
		{
			LogManager.getLogger().error("Data object {} for shared buildplate {} could not be loaded from object store", sharedBuildplate.serverDataObjectId, sharedBuildplateId);
			return null;
		}
		String serverDataBase64 = Base64.getEncoder().encodeToString(serverData);

		return new BuildplateLoadResponse(serverDataBase64);
	}

	@Nullable
	private BuildplateLoadResponse handleLoadEncounter(@NotNull String encounterBuildplateId) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(false)
				.get("encounterBuildplates", "", EncounterBuildplates.class)
				.execute(this.earthDB);
		EncounterBuildplates encounterBuildplates = (EncounterBuildplates) results.get("encounterBuildplates").value();

		EncounterBuildplates.EncounterBuildplate encounterBuildplate = encounterBuildplates.getEncounterBuildplate(encounterBuildplateId);
		if (encounterBuildplate == null)
		{
			return null;
		}

		byte[] serverData = this.objectStoreClient.get(encounterBuildplate.serverDataObjectId).join();
		if (serverData == null)
		{
			LogManager.getLogger().error("Data object {} for encounter buildplate {} could not be loaded from object store", encounterBuildplate.serverDataObjectId, encounterBuildplateId);
			return null;
		}
		String serverDataBase64 = Base64.getEncoder().encodeToString(serverData);

		return new BuildplateLoadResponse(serverDataBase64);
	}

	private boolean handleSaved(@NotNull String instanceId, @NotNull String dataBase64, long timestamp) throws DatabaseException
	{
		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return false;
		}
		if (instanceInfo.type() != BuildplateInstancesManager.InstanceType.BUILD)
		{
			return false;
		}
		String playerId = instanceInfo.playerId();
		String buildplateId = instanceInfo.buildplateId();

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
				if (previewObjectId != null)
				{
					this.objectStoreClient.delete(previewObjectId);
				}
				return false;
			}
		}
		catch (DatabaseException exception)
		{
			this.objectStoreClient.delete(serverDataObjectId);
			if (previewObjectId != null)
			{
				this.objectStoreClient.delete(previewObjectId);
			}

			throw exception;
		}
	}

	@Nullable
	private PlayerConnectedResponse handlePlayerConnected(@NotNull String instanceId, @NotNull PlayerConnectedRequest playerConnectedRequest) throws DatabaseException
	{
		// TODO: check join code etc.

		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return null;
		}

		InventoryResponse initialInventoryContents;
		switch (instanceInfo.type())
		{
			case BUILD ->
			{
				initialInventoryContents = null;
			}
			case PLAY ->
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("inventory", playerConnectedRequest.uuid(), Inventory.class)
						.get("hotbar", playerConnectedRequest.uuid(), Hotbar.class)
						.execute(this.earthDB);
				Inventory inventory = (Inventory) results.get("inventory").value();
				Hotbar hotbar = (Hotbar) results.get("hotbar").value();

				initialInventoryContents = new InventoryResponse(
						Stream.concat(
								Arrays.stream(inventory.getStackableItems())
										.map(item -> new InventoryResponse.Item(item.id(), item.count(), null, 0)),
								Arrays.stream(inventory.getNonStackableItems())
										.mapMulti((item, consumer) -> Arrays.stream(item.instances())
												.map(instance -> new InventoryResponse.Item(item.id(), 1, instance.instanceId(), instance.wear()))
												.forEach(consumer))
						).filter(item -> item.count() > 0).toArray(InventoryResponse.Item[]::new),
						Arrays.stream(hotbar.items).map(item -> item != null && item.count() > 0 ? new InventoryResponse.HotbarItem(item.uuid(), item.count(), item.instanceId()) : null).toArray(InventoryResponse.HotbarItem[]::new)
				);
			}
			case SHARED_BUILD, SHARED_PLAY ->
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("sharedBuildplates", "", SharedBuildplates.class)
						.execute(this.earthDB);
				SharedBuildplates sharedBuildplates = (SharedBuildplates) results.get("sharedBuildplates").value();
				SharedBuildplates.SharedBuildplate sharedBuildplate = sharedBuildplates.getSharedBuildplate(instanceInfo.buildplateId());
				if (sharedBuildplate == null)
				{
					return null;
				}

				initialInventoryContents = new InventoryResponse(
						Stream.concat(
								Arrays.stream(sharedBuildplate.hotbar)
										.filter(item -> item != null && item.count() > 0)
										.filter(item -> item.instanceId() == null)
										.collect(HashMap<String, Integer>::new, (hashMap, hotbarItem) ->
										{
											hashMap.put(hotbarItem.uuid(), hashMap.getOrDefault(hotbarItem.uuid(), 0) + hotbarItem.count());
										}, (hashMap1, hashMap2) ->
										{
											hashMap2.forEach((uuid, count) -> hashMap1.merge(uuid, count, Integer::sum));
										}).entrySet().stream()
										.map(entry -> new InventoryResponse.Item(entry.getKey(), entry.getValue(), null, 0)),
								Arrays.stream(sharedBuildplate.hotbar)
										.filter(item -> item != null && item.count() > 0)
										.filter(item -> item.instanceId() != null)
										.map(item -> new InventoryResponse.Item(item.uuid(), 1, item.instanceId(), item.wear()))
						).toArray(InventoryResponse.Item[]::new),
						Arrays.stream(sharedBuildplate.hotbar).map(item -> item != null && item.count() > 0 ? new InventoryResponse.HotbarItem(item.uuid(), item.count(), item.instanceId()) : null).toArray(InventoryResponse.HotbarItem[]::new)
				);
			}
			case ENCOUNTER ->
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerConnectedRequest.uuid(), Inventory.class)
						.get("hotbar", playerConnectedRequest.uuid(), Hotbar.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

							InventoryResponse.HotbarItem[] inventoryResponseHotbar = new InventoryResponse.HotbarItem[7];
							HashMap<String, Integer> inventoryResponseStackableItems = new HashMap<>();
							LinkedList<InventoryResponse.Item> inventoryResponseNonStackableItems = new LinkedList<>();
							for (int index = 0; index < 7; index++)
							{
								Hotbar.Item item = hotbar.items[index];
								if (item != null)
								{
									if (item.instanceId() == null)
									{
										inventory.takeItems(item.uuid(), item.count());
										inventoryResponseStackableItems.put(item.uuid(), inventoryResponseStackableItems.getOrDefault(item.uuid(), 0) + item.count());
										inventoryResponseHotbar[index] = new InventoryResponse.HotbarItem(item.uuid(), item.count(), null);
									}
									else
									{
										int wear = inventory.takeItems(item.uuid(), new String[]{item.instanceId()})[0].wear();
										inventoryResponseNonStackableItems.add(new InventoryResponse.Item(item.uuid(), 1, item.instanceId(), wear));
										inventoryResponseHotbar[index] = new InventoryResponse.HotbarItem(item.uuid(), 1, item.instanceId());
									}
								}
							}
							hotbar.limitToInventory(inventory);

							InventoryResponse inventoryResponse = new InventoryResponse(
									Stream.concat(
											inventoryResponseStackableItems.entrySet().stream().map(entry -> new InventoryResponse.Item(entry.getKey(), entry.getValue(), null, 0)),
											inventoryResponseNonStackableItems.stream()
									).toArray(InventoryResponse.Item[]::new),
									inventoryResponseHotbar
							);

							return new EarthDB.Query(true)
									.update("inventory", playerConnectedRequest.uuid(), inventory)
									.update("hotbar", playerConnectedRequest.uuid(), hotbar)
									.extra("inventoryResponse", inventoryResponse);
						})
						.execute(this.earthDB);

				initialInventoryContents = (InventoryResponse) results.getExtra("inventoryResponse");
			}
			default ->
			{
				// shouldn't happen, safe default
				initialInventoryContents = new InventoryResponse(new InventoryResponse.Item[0], new InventoryResponse.HotbarItem[7]);
			}
		}

		PlayerConnectedResponse playerConnectedResponse = new PlayerConnectedResponse(
				true,
				initialInventoryContents
		);

		return playerConnectedResponse;
	}

	@Nullable
	private PlayerDisconnectedResponse handlePlayerDisconnected(@NotNull String instanceId, @NotNull PlayerDisconnectedRequest playerDisconnectedRequest, long timestamp) throws DatabaseException
	{
		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return null;
		}

		boolean usesBackpack = instanceInfo.type() == BuildplateInstancesManager.InstanceType.ENCOUNTER;
		if (usesBackpack)
		{
			InventoryResponse backpackContents = playerDisconnectedRequest.backpackContents();
			if (backpackContents == null)
			{
				LogManager.getLogger().error("Expected backpack contents in player disconnected request");
				return null;
			}

			EarthDB.Results results = new EarthDB.Query(true)
					.get("inventory", playerDisconnectedRequest.playerId(), Inventory.class)
					.get("journal", playerDisconnectedRequest.playerId(), Journal.class)
					.then(results1 ->
					{
						Inventory inventory = (Inventory) results1.get("inventory").value();
						Journal journal = (Journal) results1.get("journal").value();

						LinkedList<String> unlockedJournalItems = new LinkedList<>();
						for (InventoryResponse.Item item : backpackContents.items())
						{
							Catalog.ItemsCatalog.Item catalogItem = this.catalog.itemsCatalog.getItem(item.id());
							if (catalogItem == null)
							{
								LogManager.getLogger().error("Backpack contents contained item that is not in item catalog");
								continue;
							}
							if (!catalogItem.stackable() && item.instanceId() == null)
							{
								LogManager.getLogger().error("Backpack contents contained non-stackable item without instance ID");
								continue;
							}

							if (catalogItem.stackable())
							{
								inventory.addItems(item.id(), item.count());
							}
							else
							{
								inventory.addItems(item.id(), new NonStackableItemInstance[]{new NonStackableItemInstance(item.instanceId(), item.wear())});
							}

							if (journal.addCollectedItem(item.id(), timestamp, item.count()) == 0)
							{
								if (catalogItem.journalEntry() != null)
								{
									unlockedJournalItems.add(item.id());
								}
							}
						}

						Hotbar hotbar = new Hotbar();
						for (int index = 0; index < 7; index++)
						{
							InventoryResponse.HotbarItem hotbarItem = backpackContents.hotbar()[index];
							if (hotbarItem != null)
							{
								hotbar.items[index] = new Hotbar.Item(hotbarItem.id(), hotbarItem.count(), hotbarItem.instanceId());
							}
						}
						hotbar.limitToInventory(inventory);

						EarthDB.Query query = new EarthDB.Query(true)
								.update("inventory", playerDisconnectedRequest.playerId(), inventory)
								.update("hotbar", playerDisconnectedRequest.playerId(), hotbar)
								.update("journal", playerDisconnectedRequest.playerId(), journal);
						for (String itemId : unlockedJournalItems)
						{
							query.then(TokenUtils.addToken(playerDisconnectedRequest.playerId(), new Tokens.JournalItemUnlockedToken(itemId)));
						}
						return query;
					})
					.execute(this.earthDB);
		}

		return new PlayerDisconnectedResponse();
	}

	@Nullable
	private InitialPlayerStateResponse handleGetInitialPlayerState(@NotNull String instanceId, @NotNull String playerId, long currentTime) throws DatabaseException
	{
		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return null;
		}

		boolean useHealth;
		boolean useBoosts;
		switch (instanceInfo.type())
		{
			case BUILD ->
			{
				useHealth = false;
				useBoosts = false;
			}
			case PLAY ->
			{
				useHealth = false;
				useBoosts = true;
			}
			case SHARED_BUILD ->
			{
				useHealth = false;
				useBoosts = false;
			}
			case SHARED_PLAY ->
			{
				useHealth = false;
				useBoosts = true;
			}
			case ENCOUNTER ->
			{
				useHealth = true;
				useBoosts = true;
			}
			default ->
			{
				useHealth = false;
				useBoosts = false;
			}
		}

		if (!useHealth && !useBoosts)
		{
			return new InitialPlayerStateResponse(20.0f, new InitialPlayerStateResponse.BoostStatusEffect[0]);
		}
		else
		{
			if (!useBoosts)
			{
				throw new AssertionError();
			}

			EarthDB.Results results = new EarthDB.Query(false)
					.get("profile", playerId, Profile.class)
					.get("boosts", playerId, Boosts.class)
					.execute(this.earthDB);
			Profile profile = (Profile) results.get("profile").value();
			Boosts boosts = (Boosts) results.get("boosts").value();

			float maxHealth = BoostUtils.getMaxPlayerHealth(boosts, currentTime, this.catalog.itemsCatalog);

			record EffectInfo(
					long endTime,
					Catalog.ItemsCatalog.Item.BoostInfo.Effect effect
			)
			{
			}
			return new InitialPlayerStateResponse(
					useHealth ? Math.min(profile.health, maxHealth) : maxHealth,
					Arrays.stream(boosts.activeBoosts)
							.filter(activeBoost -> activeBoost != null)
							.filter(activeBoost -> activeBoost.startTime() + activeBoost.duration() >= currentTime)
							.flatMap(activeBoost -> Arrays.stream(this.catalog.itemsCatalog.getItem(activeBoost.itemId()).boostInfo().effects()).map(effect -> new EffectInfo(activeBoost.startTime() + activeBoost.duration(), effect)))
							.filter(effectInfo -> switch (effectInfo.effect.type())
							{
								case ADVENTURE_XP, DEFENSE, EATING, HEALTH, MINING_SPEED, STRENGTH -> true;
								default -> false;
							})
							.map(effectInfo -> new InitialPlayerStateResponse.BoostStatusEffect(
									switch (effectInfo.effect.type())
									{
										case ADVENTURE_XP -> InitialPlayerStateResponse.BoostStatusEffect.Type.ADVENTURE_XP;
										case DEFENSE -> InitialPlayerStateResponse.BoostStatusEffect.Type.DEFENSE;
										case EATING -> InitialPlayerStateResponse.BoostStatusEffect.Type.EATING;
										case HEALTH -> InitialPlayerStateResponse.BoostStatusEffect.Type.HEALTH;
										case MINING_SPEED -> InitialPlayerStateResponse.BoostStatusEffect.Type.MINING_SPEED;
										case STRENGTH -> InitialPlayerStateResponse.BoostStatusEffect.Type.STRENGTH;
										default -> throw new AssertionError();
									},
									effectInfo.effect.value(),
									effectInfo.endTime - currentTime
							))
							.toArray(InitialPlayerStateResponse.BoostStatusEffect[]::new)
			);
		}
	}

	@Nullable
	private InventoryResponse handleGetInventory(@NotNull String instanceId, @NotNull String requestedInventoryPlayerId) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(false)
				.get("inventory", requestedInventoryPlayerId, Inventory.class)
				.get("hotbar", requestedInventoryPlayerId, Hotbar.class)
				.execute(this.earthDB);
		Inventory inventory = (Inventory) results.get("inventory").value();
		Hotbar hotbar = (Hotbar) results.get("hotbar").value();

		return new InventoryResponse(
				Stream.concat(
						Arrays.stream(inventory.getStackableItems())
								.map(item -> new InventoryResponse.Item(item.id(), item.count(), null, 0)),
						Arrays.stream(inventory.getNonStackableItems())
								.mapMulti((item, consumer) -> Arrays.stream(item.instances())
										.map(instance -> new InventoryResponse.Item(item.id(), 1, instance.instanceId(), instance.wear()))
										.forEach(consumer))
				).filter(item -> item.count() > 0).toArray(InventoryResponse.Item[]::new),
				Arrays.stream(hotbar.items).map(item -> item != null && item.count() > 0 ? new InventoryResponse.HotbarItem(item.uuid(), item.count(), item.instanceId()) : null).toArray(InventoryResponse.HotbarItem[]::new)
		);
	}

	private boolean handleInventoryAdd(@NotNull String instanceId, @NotNull InventoryAddItemMessage inventoryAddItemMessage, long timestamp) throws DatabaseException
	{
		Catalog.ItemsCatalog.Item catalogItem = this.catalog.itemsCatalog.getItem(inventoryAddItemMessage.itemId());
		if (catalogItem == null)
		{
			return false;
		}
		if (!catalogItem.stackable() && inventoryAddItemMessage.instanceId() == null)
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

					if (catalogItem.stackable())
					{
						inventory.addItems(inventoryAddItemMessage.itemId(), inventoryAddItemMessage.count());
					}
					else
					{
						inventory.addItems(inventoryAddItemMessage.itemId(), new NonStackableItemInstance[]{new NonStackableItemInstance(inventoryAddItemMessage.instanceId(), inventoryAddItemMessage.wear())});
					}

					boolean journalItemUnlocked = false;
					if (journal.addCollectedItem(inventoryAddItemMessage.itemId(), timestamp, inventoryAddItemMessage.count()) == 0)
					{
						if (catalogItem.journalEntry() != null)
						{
							journalItemUnlocked = true;
						}
					}

					EarthDB.Query query = new EarthDB.Query(true)
							.update("inventory", inventoryAddItemMessage.playerId(), inventory)
							.update("journal", inventoryAddItemMessage.playerId(), journal);
					if (journalItemUnlocked)
					{
						query.then(TokenUtils.addToken(inventoryAddItemMessage.playerId(), new Tokens.JournalItemUnlockedToken(inventoryAddItemMessage.itemId())));
					}
					return query;
				})
				.execute(this.earthDB);
		return true;
	}

	@Nullable
	private Object handleInventoryRemove(@NotNull String instanceId, @NotNull InventoryRemoveItemRequest inventoryRemoveItemRequest) throws DatabaseException
	{
		EarthDB.Results results = new EarthDB.Query(true)
				.get("inventory", inventoryRemoveItemRequest.playerId(), Inventory.class)
				.get("hotbar", inventoryRemoveItemRequest.playerId(), Hotbar.class)
				.then(results1 ->
				{
					Inventory inventory = (Inventory) results1.get("inventory").value();
					Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

					Object result;
					if (inventoryRemoveItemRequest.instanceId() != null)
					{
						if (inventory.takeItems(inventoryRemoveItemRequest.itemId(), new String[]{inventoryRemoveItemRequest.instanceId()}) == null)
						{
							LogManager.getLogger().warn("Buildplate instance {} attempted to remove item {} {} from player {} that is not in inventory", instanceId, inventoryRemoveItemRequest.itemId(), inventoryRemoveItemRequest.instanceId(), inventoryRemoveItemRequest.playerId());
							result = false;
						}
						else
						{
							result = true;
						}
					}
					else
					{
						if (inventory.takeItems(inventoryRemoveItemRequest.itemId(), inventoryRemoveItemRequest.count()))
						{
							result = inventoryRemoveItemRequest.count();
						}
						else
						{
							int count = inventory.getItemCount(inventoryRemoveItemRequest.itemId());
							if (!inventory.takeItems(inventoryRemoveItemRequest.itemId(), count))
							{
								count = 0;
							}
							LogManager.getLogger().warn("Buildplate instance {} attempted to remove item {} {} from player {} that is not in inventory", instanceId, inventoryRemoveItemRequest.itemId(), inventoryRemoveItemRequest.count() - count, inventoryRemoveItemRequest.playerId());
							result = count;
						}
					}

					hotbar.limitToInventory(inventory);

					return new EarthDB.Query(true)
							.update("inventory", inventoryRemoveItemRequest.playerId(), inventory)
							.update("hotbar", inventoryRemoveItemRequest.playerId(), hotbar)
							.extra("result", result);
				})
				.execute(this.earthDB);
		return results.getExtra("result");
	}

	private boolean handleInventoryUpdateWear(@NotNull String instanceId, @NotNull InventoryUpdateItemWearMessage inventoryUpdateItemWearMessage) throws DatabaseException
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

	private boolean handleInventorySetHotbar(@NotNull String instanceId, @NotNull InventorySetHotbarMessage inventorySetHotbarMessage) throws DatabaseException
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
	private static <T> BuildplateInstanceRequestHandler.RequestWithInstanceId<T> readRequest(@NotNull String string, @NotNull Class<T> requestClass)
	{
		try
		{
			TypeToken<?> typeToken = TypeToken.getParameterized(RequestWithInstanceId.class, requestClass);
			RequestWithInstanceId<T> request = (RequestWithInstanceId<T>) new Gson().fromJson(string, typeToken);
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

	private record RequestWithInstanceId<T>(
			@NotNull String instanceId,
			@NotNull T request
	)
	{
	}
}