package micheal65536.vienna.apiserver.routes.player;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.buildplates.BuildplateInstance;
import micheal65536.vienna.apiserver.types.buildplates.Dimension;
import micheal65536.vienna.apiserver.types.buildplates.Offset;
import micheal65536.vienna.apiserver.types.buildplates.OwnedBuildplate;
import micheal65536.vienna.apiserver.types.buildplates.SurfaceOrientation;
import micheal65536.vienna.apiserver.types.common.Coordinate;
import micheal65536.vienna.apiserver.utils.BuildplateInstancesManager;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Buildplates;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;

public class BuildplatesRouter extends Router
{
	public BuildplatesRouter(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient)
	{
		BuildplateInstancesManager buildplateInstancesManager = new BuildplateInstancesManager(eventBusClient);

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

			Buildplates.Buildplate buildplate;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("buildplates", playerId, Buildplates.class)
						.execute(earthDB);
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

			String instanceId = buildplateInstancesManager.startBuildplateInstance(playerId, buildplateId, buildplate.night);
			if (instanceId == null)
			{
				return Response.serverError();
			}

			BuildplateInstancesManager.InstanceInfo instanceInfo = buildplateInstancesManager.getInstanceInfo(instanceId);
			if (instanceInfo == null)
			{
				return Response.serverError();
			}

			BuildplateInstance buildplateInstance = instanceInfoToApiResponse(buildplate, instanceInfo);

			return Response.okFromJson(new EarthApiResponse<>(buildplateInstance), EarthApiResponse.class);
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

			Buildplates.Buildplate buildplate;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("buildplates", playerId, Buildplates.class)
						.execute(earthDB);
				buildplate = ((Buildplates) results.get("buildplates").value()).getBuildplate(instanceInfo.buildplateId());
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
			if (buildplate == null)
			{
				return Response.notFound();
			}

			// TODO: the client is supposed to poll until the buildplate server is ready, but instead it just crashes if we tell it that the buildplate server is not ready yet
			// TODO: so instead we just stall the request until it's ready, this is really ugly and eventually we need to figure out why it's crashing and implement this properly
			// TODO: this also relies on the buildplate server starting in less than ~20 seconds as the client will eventually time out the HTTP request and crash anyway
			//BuildplateInstance buildplateInstance = instanceInfoToApiResponse(buildplate, instanceInfo);
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
			BuildplateInstance buildplateInstance = instanceInfoToApiResponse(buildplate, instanceInfo1);

			return Response.okFromJson(new EarthApiResponse<>(buildplateInstance), EarthApiResponse.class);
		});
	}

	private static BuildplateInstance instanceInfoToApiResponse(@NotNull Buildplates.Buildplate buildplate, @NotNull BuildplateInstancesManager.InstanceInfo instanceInfo)
	{
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
						new Dimension(buildplate.size, buildplate.size),
						new Offset(0, buildplate.offset, 0),
						buildplate.scale,
						false,    // TODO
						BuildplateInstance.GameplayMetadata.GameplayMode.BUILDPLATE,    // TODO
						SurfaceOrientation.HORIZONTAL,
						null,
						null,    // TODO
						new BuildplateInstance.GameplayMetadata.ShutdownBehavior[]{BuildplateInstance.GameplayMetadata.ShutdownBehavior.ALL_PLAYERS_QUIT, BuildplateInstance.GameplayMetadata.ShutdownBehavior.HOST_PLAYER_QUITS},
						new BuildplateInstance.GameplayMetadata.SnapshotOptions(
								BuildplateInstance.GameplayMetadata.SnapshotOptions.SnapshotWorldStorage.BUILDPLATE,
								new BuildplateInstance.GameplayMetadata.SnapshotOptions.SaveState(
										false,
										false,
										false,
										true,
										true,
										true
								),
								BuildplateInstance.GameplayMetadata.SnapshotOptions.SnapshotTriggerConditions.NONE,
								new BuildplateInstance.GameplayMetadata.SnapshotOptions.TriggerCondition[]{BuildplateInstance.GameplayMetadata.SnapshotOptions.TriggerCondition.INTERVAL, BuildplateInstance.GameplayMetadata.SnapshotOptions.TriggerCondition.PLAYER_EXITS},
								TimeFormatter.formatDuration(30 * 1000)
						),
						new HashMap<>()
				),
				"776932eeeb69",
				//new Coordinate(50.99636722700025f, -0.7234904312500047f)
				new Coordinate(0.0f, 0.0f)    // TODO
		);
	}
}