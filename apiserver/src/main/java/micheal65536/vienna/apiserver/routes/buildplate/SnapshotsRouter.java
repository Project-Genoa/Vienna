package micheal65536.vienna.apiserver.routes.buildplate;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.utils.BuildplatePreviewGenerator;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Buildplates;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SnapshotsRouter extends Router
{
	public SnapshotsRouter(@NotNull EarthDB earthDB, @NotNull ObjectStoreClient objectStoreClient, @NotNull String buildplatePreviewGeneratorCommand)
	{
		BuildplatePreviewGenerator buildplatePreviewGenerator = new BuildplatePreviewGenerator(buildplatePreviewGeneratorCommand);

		this.addHandler(new Route.Builder(Request.Method.GET, "/snapshot/$playerId/$buildplateId").build(), request ->
		{
			String playerId = request.getParameter("playerId");
			String buildplateId = request.getParameter("buildplateId");

			Buildplates buildplates;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("buildplates", playerId, Buildplates.class)
						.execute(earthDB);
				buildplates = (Buildplates) results.get("buildplates").value();
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			Buildplates.Buildplate buildplate = buildplates.getBuildplate(buildplateId);

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

			return Response.ok(serverData, "application/octet-stream");
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/snapshot/$playerId/$buildplateId").build(), request ->
		{
			String playerId = request.getParameter("playerId");
			String buildplateId = request.getParameter("buildplateId");

			// TODO: it would be nicer to just send the data in binary/bytes form rather than as base64, but HttpServletRequest apparently doesn't provide that???
			byte[] serverData;
			try
			{
				serverData = Base64.getDecoder().decode(request.getBody());
			}
			catch (IllegalArgumentException exception)
			{
				return Response.badRequest();
			}

			Buildplates.Buildplate buildplateUnsafeForPreviewGenerator;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("buildplates", playerId, Buildplates.class)
						.execute(earthDB);
				buildplateUnsafeForPreviewGenerator = ((Buildplates) results.get("buildplates").value()).getBuildplate(buildplateId);
				if (buildplateUnsafeForPreviewGenerator == null)
				{
					return Response.notFound();
				}
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			String preview = buildplatePreviewGenerator.generatePreview(buildplateUnsafeForPreviewGenerator, serverData);
			if (preview == null)
			{
				LogManager.getLogger().warn("Could not generate preview for buildplate");
			}

			String serverDataObjectId = objectStoreClient.store(serverData).join();
			if (serverDataObjectId == null)
			{
				LogManager.getLogger().error("Could not store new data object for buildplate {} in object store", buildplateId);
				return Response.serverError();
			}
			String previewObjectId;
			if (preview != null)
			{
				previewObjectId = objectStoreClient.store(preview.getBytes(StandardCharsets.US_ASCII)).join();
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
				EarthDB.Results results = new EarthDB.Query(true)
						.get("buildplates", playerId, Buildplates.class)
						.then(results1 ->
						{
							Buildplates buildplates = (Buildplates) results1.get("buildplates").value();
							Buildplates.Buildplate buildplate = buildplates.getBuildplate(buildplateId);
							if (buildplate != null)
							{
								buildplate.lastModified = request.timestamp;

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
						.execute(earthDB);

				boolean exists = results.getExtra("exists");
				if (exists)
				{
					String oldServerDataObjectId = results.getExtra("oldServerDataObjectId");
					objectStoreClient.delete(oldServerDataObjectId);

					String oldPreviewObjectId = results.getExtra("oldPreviewObjectId");
					if (!oldPreviewObjectId.isEmpty())
					{
						objectStoreClient.delete(oldPreviewObjectId);
					}

					LogManager.getLogger().info("Stored new snapshot for buildplate {}", buildplateId);

					return Response.ok("");
				}
				else
				{
					objectStoreClient.delete(serverDataObjectId);
					return Response.notFound();
				}
			}
			catch (DatabaseException exception)
			{
				objectStoreClient.delete(serverDataObjectId);

				throw new ServerErrorException(exception);
			}
		});
	}
}