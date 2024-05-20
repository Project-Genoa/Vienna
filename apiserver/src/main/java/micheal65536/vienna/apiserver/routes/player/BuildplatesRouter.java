package micheal65536.vienna.apiserver.routes.player;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.buildplates.BuildplateInstance;
import micheal65536.vienna.apiserver.types.buildplates.Dimension;
import micheal65536.vienna.apiserver.types.buildplates.Offset;
import micheal65536.vienna.apiserver.types.buildplates.OwnedBuildplate;
import micheal65536.vienna.apiserver.types.buildplates.SharedBuildplate;
import micheal65536.vienna.apiserver.types.buildplates.SurfaceOrientation;
import micheal65536.vienna.apiserver.types.common.Coordinate;
import micheal65536.vienna.apiserver.types.inventory.HotbarItem;
import micheal65536.vienna.apiserver.types.inventory.NonStackableInventoryItem;
import micheal65536.vienna.apiserver.types.inventory.StackableInventoryItem;
import micheal65536.vienna.apiserver.utils.BuildplateInstancesManager;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.ItemWear;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.TappablesManager;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.global.EncounterBuildplates;
import micheal65536.vienna.db.model.global.SharedBuildplates;
import micheal65536.vienna.db.model.player.Buildplates;
import micheal65536.vienna.db.model.player.Hotbar;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;
import micheal65536.vienna.staticdata.Catalog;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class BuildplatesRouter extends Router
{
	private final EarthDB earthDB;
	private final BuildplateInstancesManager buildplateInstancesManager;

	public BuildplatesRouter(@NotNull EarthDB earthDB, @NotNull ObjectStoreClient objectStoreClient, @NotNull BuildplateInstancesManager buildplateInstancesManager, @NotNull TappablesManager tappablesManager, @NotNull Catalog catalog)
	{
		this.earthDB = earthDB;
		this.buildplateInstancesManager = buildplateInstancesManager;

		this.addHandler(new Route.Builder(Request.Method.GET, "/buildplates").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			Buildplates buildplatesModel;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("buildplates", playerId, Buildplates.class)
						.execute(earthDB);
				buildplatesModel = (Buildplates) results.get("buildplates").value();
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			OwnedBuildplate[] ownedBuildplates = Arrays.stream(buildplatesModel.getBuildplates()).map(buildplateEntry ->
			{
				byte[] previewData = objectStoreClient.get(buildplateEntry.buildplate().previewObjectId).join();
				if (previewData == null)
				{
					LogManager.getLogger().error("Preview object {} for buildplate {} could not be loaded from object store", buildplateEntry.buildplate().previewObjectId, buildplateEntry.id());
					return null;
				}
				String model = new String(previewData, StandardCharsets.US_ASCII);

				return new OwnedBuildplate(
						buildplateEntry.id(),
						"00000000-0000-0000-0000-000000000000",
						new Dimension(buildplateEntry.buildplate().size, buildplateEntry.buildplate().size),
						new Offset(0, buildplateEntry.buildplate().offset, 0),
						buildplateEntry.buildplate().scale,
						OwnedBuildplate.Type.SURVIVAL,
						SurfaceOrientation.HORIZONTAL,
						model,
						0,    // TODO
						false,    // TODO
						0,    // TODO
						false,    // TODO
						TimeFormatter.formatTime(buildplateEntry.buildplate().lastModified),
						0,    // TODO
						""
				);
			}).filter(ownedBuildplate -> ownedBuildplate != null).toArray(OwnedBuildplate[]::new);

			return Response.okFromJson(new EarthApiResponse<>(ownedBuildplates), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/multiplayer/buildplate/$buildplateId/instances").build(), request ->
		{
			// TODO: coordinates etc.

			String playerId = request.getContextData("playerId");
			String buildplateId = request.getParameter("buildplateId");

			return this.getNewBuildplateInstanceResponse(playerId, buildplateId, BuildplateInstancesManager.InstanceType.BUILD);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/multiplayer/buildplate/$buildplateId/play/instances").build(), request ->
		{
			// TODO: coordinates etc.

			String playerId = request.getContextData("playerId");
			String buildplateId = request.getParameter("buildplateId");

			return this.getNewBuildplateInstanceResponse(playerId, buildplateId, BuildplateInstancesManager.InstanceType.PLAY);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/buildplates/$buildplateId/share").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			String buildplateId = request.getParameter("buildplateId");

			Inventory inventory;
			Hotbar hotbar;
			Buildplates.Buildplate buildplate;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("inventory", playerId, Inventory.class)
						.get("hotbar", playerId, Hotbar.class)
						.get("buildplates", playerId, Buildplates.class)
						.execute(earthDB);
				inventory = (Inventory) results.get("inventory").value();
				hotbar = (Hotbar) results.get("hotbar").value();
				buildplate = ((Buildplates) results.get("buildplates").value()).getBuildplate(buildplateId);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
			if (buildplate == null)
			{
				return Response.notFound();
			}

			byte[] serverData = objectStoreClient.get(buildplate.serverDataObjectId).join();
			if (serverData == null)
			{
				LogManager.getLogger().error("Data object {} for buildplate {} could not be loaded from object store", buildplate.serverDataObjectId, buildplateId);
				return Response.serverError();
			}
			String sharedBuildplateServerDataObjectId = objectStoreClient.store(serverData).join();
			if (sharedBuildplateServerDataObjectId == null)
			{
				LogManager.getLogger().error("Could not store data object for shared buildplate in object store");
				return Response.serverError();
			}

			String sharedBuildplateId = UUID.randomUUID().toString();
			SharedBuildplates.SharedBuildplate sharedBuildplate = new SharedBuildplates.SharedBuildplate(
					playerId,
					buildplate.size,
					buildplate.offset,
					buildplate.scale,
					buildplate.night,
					request.timestamp,
					buildplate.lastModified,
					sharedBuildplateServerDataObjectId
			);
			for (int index = 0; index < 7; index++)
			{
				Hotbar.Item item = hotbar.items[index];
				SharedBuildplates.SharedBuildplate.HotbarItem sharedBuildplateHotbarItem;
				if (item == null)
				{
					sharedBuildplateHotbarItem = null;
				}
				else if (item.instanceId() == null)
				{
					sharedBuildplateHotbarItem = new SharedBuildplates.SharedBuildplate.HotbarItem(item.uuid(), item.count(), null, 0);
				}
				else
				{
					sharedBuildplateHotbarItem = new SharedBuildplates.SharedBuildplate.HotbarItem(item.uuid(), 1, item.instanceId(), inventory.getItemInstance(item.uuid(), item.instanceId()).wear());
				}
				sharedBuildplate.hotbar[index] = sharedBuildplateHotbarItem;
			}

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("sharedBuildplates", "", SharedBuildplates.class)
						.then(results1 ->
						{
							SharedBuildplates sharedBuildplates = (SharedBuildplates) results1.get("sharedBuildplates").value();

							sharedBuildplates.addSharedBuildplate(sharedBuildplateId, sharedBuildplate);

							return new EarthDB.Query(true)
									.update("sharedBuildplates", "", sharedBuildplates);
						})
						.execute(earthDB);
			}
			catch (DatabaseException exception)
			{
				objectStoreClient.delete(sharedBuildplateServerDataObjectId);
				throw new ServerErrorException(exception);
			}

			return Response.okFromJson(new EarthApiResponse<>("minecraftearth://sharedbuildplate?id=%s".formatted(sharedBuildplateId)), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.GET, "/buildplates/shared/$sharedBuildplateId").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			String sharedBuildplateId = request.getParameter("sharedBuildplateId");

			SharedBuildplates.SharedBuildplate sharedBuildplate;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("sharedBuildplates", "", SharedBuildplates.class)
						.execute(earthDB);
				SharedBuildplates sharedBuildplates = (SharedBuildplates) results.get("sharedBuildplates").value();
				sharedBuildplate = sharedBuildplates.getSharedBuildplate(sharedBuildplateId);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
			if (sharedBuildplate == null)
			{
				return Response.notFound();
			}

			byte[] serverData = objectStoreClient.get(sharedBuildplate.serverDataObjectId).join();
			if (serverData == null)
			{
				LogManager.getLogger().error("Data object {} for shared buildplate {} could not be loaded from object store", sharedBuildplate.serverDataObjectId, sharedBuildplateId);
				return Response.serverError();
			}

			String preview = buildplateInstancesManager.getBuildplatePreview(serverData, sharedBuildplate.night);
			if (preview == null)
			{
				LogManager.getLogger().error("Could not get preview for buildplate");
				return Response.serverError();
			}

			return Response.okFromJson(new EarthApiResponse<>(new SharedBuildplate(
					sharedBuildplate.playerId,    // TODO: supposed to return username here, not player ID
					TimeFormatter.formatTime(sharedBuildplate.created),
					new SharedBuildplate.BuildplateData(
							new Dimension(sharedBuildplate.size, sharedBuildplate.size),
							new Offset(0, sharedBuildplate.offset, 0),
							sharedBuildplate.scale,
							SharedBuildplate.BuildplateData.Type.SURVIVAL,
							SurfaceOrientation.HORIZONTAL,
							preview,
							0
					),
					new micheal65536.vienna.apiserver.types.inventory.Inventory(
							Arrays.stream(sharedBuildplate.hotbar).map(item -> item != null ? new HotbarItem(
									item.uuid(),
									item.count(),
									item.instanceId(),
									item.instanceId() != null ? ItemWear.wearToHealth(item.uuid(), item.wear(), catalog.itemsCatalog) : 0.0f
							) : null).toArray(HotbarItem[]::new),
							Arrays.stream(sharedBuildplate.hotbar)
									.filter(item -> item != null && item.instanceId() == null)
									.map(item -> item.uuid())
									.distinct()
									.map(uuid -> new StackableInventoryItem(
											uuid,
											0,
											1,
											// TODO: what unlocked/last seen timestamp are we supposed to use here - the player who shared the buildplate or the player who is viewing the buildplate?
											new StackableInventoryItem.On(TimeFormatter.formatTime(0)),
											new StackableInventoryItem.On(TimeFormatter.formatTime(0))
									))
									.toArray(StackableInventoryItem[]::new),
							Arrays.stream(sharedBuildplate.hotbar)
									.filter(item -> item != null && item.instanceId() != null)
									.map(item -> item.uuid())
									.distinct()
									.map(uuid -> new NonStackableInventoryItem(
											uuid,
											new NonStackableInventoryItem.Instance[0],
											1,
											// TODO: what unlocked/last seen timestamp are we supposed to use here - the player who shared the buildplate or the player who is viewing the buildplate?
											new NonStackableInventoryItem.On(TimeFormatter.formatTime(0)),
											new NonStackableInventoryItem.On(TimeFormatter.formatTime(0))
									))
									.toArray(NonStackableInventoryItem[]::new)
					)
			)), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/multiplayer/buildplate/shared/$sharedBuildplateId/play/instances").build(), request ->
		{
			// TODO: coordinates etc.

			String playerId = request.getContextData("playerId");
			String sharedBuildplateId = request.getParameter("sharedBuildplateId");

			record SharedBuildplateInstanceRequest(
					boolean fullSize
			)
			{
			}
			SharedBuildplateInstanceRequest sharedBuildplateInstanceRequest = request.getBodyAsJson(SharedBuildplateInstanceRequest.class);

			return this.getNewSharedBuildplateInstanceResponse(playerId, sharedBuildplateId, sharedBuildplateInstanceRequest.fullSize ? BuildplateInstancesManager.InstanceType.SHARED_PLAY : BuildplateInstancesManager.InstanceType.SHARED_BUILD);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/multiplayer/encounters/$encounterId/instances").build(), request ->
		{
			// TODO: coordinates etc.

			String playerId = request.getContextData("playerId");
			String encounterId = request.getParameter("encounterId");

			record EncounterInstanceRequest(
					@NotNull String tileId
			)
			{
			}
			EncounterInstanceRequest encounterInstanceRequest = request.getBodyAsJson(EncounterInstanceRequest.class);

			return this.getNewEncounterBuildplateInstanceResponse(encounterId, encounterInstanceRequest.tileId, tappablesManager);
		});

		// TODO: should we restrict this to matching player ID?
		this.addHandler(new Route.Builder(Request.Method.GET, "/multiplayer/partitions/$partitionId/instances/$instanceId").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			String instanceId = request.getParameter("instanceId");

			BuildplateInstancesManager.InstanceInfo instanceInfo = buildplateInstancesManager.getInstanceInfo(instanceId);
			if (instanceInfo == null)
			{
				return Response.notFound();
			}

			// TODO: the client is supposed to poll until the buildplate server is ready, but instead it just crashes if we tell it that the buildplate server is not ready yet
			// TODO: so instead we just stall the request until it's ready, this is really ugly and eventually we need to figure out why it's crashing and implement this properly
			// TODO: this also relies on the buildplate server starting in less than ~20 seconds as the client will eventually time out the HTTP request and crash anyway
			//BuildplateInstance buildplateInstance = this.instanceInfoToApiResponse(instanceInfo);
			BuildplateInstancesManager.InstanceInfo instanceInfo1;
			int waitCount = 0;
			do
			{
				instanceInfo1 = buildplateInstancesManager.getInstanceInfo(instanceId);
				if (instanceInfo1 == null)
				{
					return Response.notFound();
				}

				if (!instanceInfo1.ready())
				{
					try
					{
						Thread.sleep(1000);
					}
					catch (InterruptedException exception)
					{
						continue;
					}
					waitCount++;
				}
			}
			while (!instanceInfo1.ready() && waitCount < 30);
			BuildplateInstance buildplateInstance = this.instanceInfoToApiResponse(instanceInfo1);
			if (buildplateInstance == null)
			{
				return Response.notFound();
			}

			return Response.okFromJson(new EarthApiResponse<>(buildplateInstance), EarthApiResponse.class);
		});
	}

	private Response getNewBuildplateInstanceResponse(@NotNull String playerId, @NotNull String buildplateId, @NotNull BuildplateInstancesManager.InstanceType type) throws ServerErrorException
	{
		Buildplates.Buildplate buildplate;
		try
		{
			EarthDB.Results results = new EarthDB.Query(false)
					.get("buildplates", playerId, Buildplates.class)
					.execute(this.earthDB);
			buildplate = ((Buildplates) results.get("buildplates").value()).getBuildplate(buildplateId);
		}
		catch (DatabaseException exception)
		{
			throw new ServerErrorException(exception);
		}
		if (buildplate == null)
		{
			return Response.notFound();
		}

		String instanceId = this.buildplateInstancesManager.requestBuildplateInstance(playerId, null, buildplateId, type, 0, buildplate.night);
		if (instanceId == null)
		{
			return Response.serverError();
		}

		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return Response.serverError();
		}

		BuildplateInstance buildplateInstance = this.instanceInfoToApiResponse(instanceInfo);
		if (buildplateInstance == null)
		{
			return Response.serverError();
		}

		return Response.okFromJson(new EarthApiResponse<>(buildplateInstance), EarthApiResponse.class);
	}

	private Response getNewSharedBuildplateInstanceResponse(@NotNull String playerId, @NotNull String sharedBuildplateId, @NotNull BuildplateInstancesManager.InstanceType type) throws ServerErrorException
	{
		SharedBuildplates.SharedBuildplate sharedBuildplate;
		try
		{
			EarthDB.Results results = new EarthDB.Query(false)
					.get("sharedBuildplates", "", SharedBuildplates.class)
					.execute(this.earthDB);
			sharedBuildplate = ((SharedBuildplates) results.get("sharedBuildplates").value()).getSharedBuildplate(sharedBuildplateId);
		}
		catch (DatabaseException exception)
		{
			throw new ServerErrorException(exception);
		}
		if (sharedBuildplate == null)
		{
			return Response.notFound();
		}

		String instanceId = this.buildplateInstancesManager.requestBuildplateInstance(playerId, null, sharedBuildplateId, type, 0, sharedBuildplate.night);
		if (instanceId == null)
		{
			return Response.serverError();
		}

		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return Response.serverError();
		}

		BuildplateInstance buildplateInstance = this.instanceInfoToApiResponse(instanceInfo);
		if (buildplateInstance == null)
		{
			return Response.serverError();
		}

		return Response.okFromJson(new EarthApiResponse<>(buildplateInstance), EarthApiResponse.class);
	}

	private Response getNewEncounterBuildplateInstanceResponse(@NotNull String encounterId, @NotNull String tileId, @NotNull TappablesManager tappablesManager) throws ServerErrorException
	{
		TappablesManager.Encounter encounter = tappablesManager.getEncounterWithId(encounterId, tileId);
		if (encounter == null)
		{
			return Response.notFound();
		}

		String instanceId = this.buildplateInstancesManager.requestBuildplateInstance(null, encounterId, encounter.encounterBuildplateId(), BuildplateInstancesManager.InstanceType.ENCOUNTER, encounter.spawnTime() + encounter.validFor(), false);
		if (instanceId == null)
		{
			return Response.serverError();
		}

		BuildplateInstancesManager.InstanceInfo instanceInfo = this.buildplateInstancesManager.getInstanceInfo(instanceId);
		if (instanceInfo == null)
		{
			return Response.serverError();
		}

		BuildplateInstance buildplateInstance = this.instanceInfoToApiResponse(instanceInfo);
		if (buildplateInstance == null)
		{
			return Response.serverError();
		}

		return Response.okFromJson(new EarthApiResponse<>(buildplateInstance), EarthApiResponse.class);
	}

	@Nullable
	private BuildplateInstance instanceInfoToApiResponse(@NotNull BuildplateInstancesManager.InstanceInfo instanceInfo) throws ServerErrorException
	{
		enum Source
		{
			PLAYER,
			SHARED,
			ENCOUNTER
		}
		boolean fullsize;
		BuildplateInstance.GameplayMetadata.GameplayMode gameplayMode;
		Source source;
		switch (instanceInfo.type())
		{
			case BUILD ->
			{
				fullsize = false;
				gameplayMode = BuildplateInstance.GameplayMetadata.GameplayMode.BUILDPLATE;
				source = Source.PLAYER;
			}
			case PLAY ->
			{
				fullsize = true;
				gameplayMode = BuildplateInstance.GameplayMetadata.GameplayMode.BUILDPLATE_PLAY;
				source = Source.PLAYER;
			}
			case SHARED_BUILD ->
			{
				fullsize = false;
				gameplayMode = BuildplateInstance.GameplayMetadata.GameplayMode.SHARED_BUILDPLATE_PLAY;
				source = Source.SHARED;
			}
			case SHARED_PLAY ->
			{
				fullsize = true;
				gameplayMode = BuildplateInstance.GameplayMetadata.GameplayMode.SHARED_BUILDPLATE_PLAY;
				source = Source.SHARED;
			}
			case ENCOUNTER ->
			{
				fullsize = true;
				gameplayMode = BuildplateInstance.GameplayMetadata.GameplayMode.ENCOUNTER;
				source = Source.ENCOUNTER;
			}
			default ->
			{
				throw new AssertionError();
			}
		}

		int size;
		int offset;
		int scale;
		switch (source)
		{
			case PLAYER ->
			{
				Buildplates.Buildplate buildplate;
				try
				{
					EarthDB.Results results = new EarthDB.Query(false)
							.get("buildplates", instanceInfo.playerId(), Buildplates.class)
							.execute(this.earthDB);
					buildplate = ((Buildplates) results.get("buildplates").value()).getBuildplate(instanceInfo.buildplateId());
				}
				catch (DatabaseException exception)
				{
					throw new ServerErrorException(exception);
				}
				if (buildplate == null)
				{
					return null;
				}
				size = buildplate.size;
				offset = buildplate.offset;
				scale = buildplate.scale;
			}
			case SHARED ->
			{
				SharedBuildplates.SharedBuildplate sharedBuildplate;
				try
				{
					EarthDB.Results results = new EarthDB.Query(false)
							.get("sharedBuildplates", "", SharedBuildplates.class)
							.execute(this.earthDB);
					sharedBuildplate = ((SharedBuildplates) results.get("sharedBuildplates").value()).getSharedBuildplate(instanceInfo.buildplateId());
				}
				catch (DatabaseException exception)
				{
					throw new ServerErrorException(exception);
				}
				if (sharedBuildplate == null)
				{
					return null;
				}
				size = sharedBuildplate.size;
				offset = sharedBuildplate.offset;
				scale = sharedBuildplate.scale;
			}
			case ENCOUNTER ->
			{
				EncounterBuildplates.EncounterBuildplate encounterBuildplate;
				try
				{
					EarthDB.Results results = new EarthDB.Query(false)
							.get("encounterBuildplates", "", EncounterBuildplates.class)
							.execute(this.earthDB);
					encounterBuildplate = ((EncounterBuildplates) results.get("encounterBuildplates").value()).getEncounterBuildplate(instanceInfo.buildplateId());
				}
				catch (DatabaseException exception)
				{
					throw new ServerErrorException(exception);
				}
				if (encounterBuildplate == null)
				{
					return null;
				}
				size = encounterBuildplate.size;
				offset = encounterBuildplate.offset;
				scale = encounterBuildplate.scale;
			}
			default ->
			{
				throw new AssertionError();
			}
		}

		return new BuildplateInstance(
				instanceInfo.instanceId(),
				"00000000-0000-0000-0000-000000000000",
				"d.projectearth.dev",    // TODO
				instanceInfo.address(),
				instanceInfo.port(),
				instanceInfo.ready(),
				instanceInfo.ready() ? BuildplateInstance.ApplicationStatus.READY : BuildplateInstance.ApplicationStatus.UNKNOWN,
				instanceInfo.ready() ? BuildplateInstance.ServerStatus.RUNNING : BuildplateInstance.ServerStatus.RUNNING,
				new Gson().toJson(new MapBuilder<>().put("buildplateid", instanceInfo.buildplateId()).getMap()),
				new BuildplateInstance.GameplayMetadata(
						instanceInfo.buildplateId(),
						"00000000-0000-0000-0000-000000000000",
						instanceInfo.playerId(),
						"2020.1217.02",
						"CK06Yzm2",    // TODO
						new Dimension(size, size),
						new Offset(0, offset, 0),
						!fullsize ? scale : 1,
						fullsize,
						gameplayMode,
						SurfaceOrientation.HORIZONTAL,
						null,
						null,    // TODO
						new HashMap<>()
				),
				"776932eeeb69",
				//new Coordinate(50.99636722700025f, -0.7234904312500047f)
				new Coordinate(0.0f, 0.0f)    // TODO
		);
	}
}