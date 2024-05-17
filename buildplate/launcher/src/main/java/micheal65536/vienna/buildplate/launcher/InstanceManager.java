package micheal65536.vienna.buildplate.launcher;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.buildplate.connector.model.InventoryType;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.Publisher;
import micheal65536.vienna.eventbus.client.RequestHandler;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class InstanceManager
{
	private final Starter starter;
	private final PreviewGenerator previewGenerator;

	private final Publisher publisher;
	private final RequestHandler requestHandler;
	private int runningInstanceCount = 0;
	private boolean shuttingDown = false;
	private final ReentrantLock lock = new ReentrantLock(true);

	public InstanceManager(@NotNull EventBusClient eventBusClient, @NotNull Starter starter, @NotNull PreviewGenerator previewGenerator)
	{
		this.starter = starter;
		this.previewGenerator = previewGenerator;

		this.publisher = eventBusClient.addPublisher();

		this.requestHandler = eventBusClient.addRequestHandler("buildplates", new RequestHandler.Handler()
		{
			@Override
			@Nullable
			public String request(@NotNull RequestHandler.Request request)
			{
				if (request.type.equals("start"))
				{
					InstanceManager.this.lock.lock();
					if (InstanceManager.this.shuttingDown)
					{
						InstanceManager.this.lock.unlock();
						return null;
					}
					InstanceManager.this.runningInstanceCount += 1;
					InstanceManager.this.lock.unlock();

					enum InstanceType
					{
						BUILD,
						PLAY,
						SHARED_BUILD,
						SHARED_PLAY,
						ENCOUNTER
					}

					record StartRequest(
							@Nullable String playerId,
							@Nullable String encounterId,
							@NotNull String buildplateId,
							boolean night,
							@NotNull InstanceType type,
							long shutdownTime
					)
					{
					}

					record StartNotification(
							@NotNull String instanceId,
							@Nullable String playerId,
							@Nullable String encounterId,
							@NotNull String buildplateId,
							@NotNull String address,
							int port,
							@NotNull InstanceType type
					)
					{
					}

					StartRequest startRequest;
					try
					{
						startRequest = new Gson().fromJson(request.data, StartRequest.class);
					}
					catch (Exception exception)
					{
						LogManager.getLogger().warn("Bad start request", exception);
						return null;
					}

					boolean survival;
					boolean saveEnabled;
					InventoryType inventoryType;
					Instance.BuildplateSource buildplateSource;
					Long shutdownTime;
					switch (startRequest.type)
					{
						case BUILD ->
						{
							survival = false;
							saveEnabled = true;
							inventoryType = InventoryType.SYNCED;
							buildplateSource = Instance.BuildplateSource.PLAYER;
							shutdownTime = null;
						}
						case PLAY ->
						{
							survival = true;
							saveEnabled = false;
							inventoryType = InventoryType.DISCARD;
							buildplateSource = Instance.BuildplateSource.PLAYER;
							shutdownTime = null;
						}
						case SHARED_BUILD ->
						{
							survival = false;
							saveEnabled = false;
							inventoryType = InventoryType.DISCARD;
							buildplateSource = Instance.BuildplateSource.SHARED;
							shutdownTime = null;
						}
						case SHARED_PLAY ->
						{
							survival = true;
							saveEnabled = false;
							inventoryType = InventoryType.DISCARD;
							buildplateSource = Instance.BuildplateSource.SHARED;
							shutdownTime = null;
						}
						case ENCOUNTER ->
						{
							survival = true;
							saveEnabled = false;
							inventoryType = InventoryType.BACKPACK;
							buildplateSource = Instance.BuildplateSource.ENCOUNTER;
							shutdownTime = startRequest.shutdownTime;
						}
						default ->
						{
							LogManager.getLogger().warn("Bad start request");
							return null;
						}
					}
					if (buildplateSource == Instance.BuildplateSource.PLAYER && startRequest.playerId == null)
					{
						LogManager.getLogger().warn("Bad start request");
						return null;
					}

					String instanceId = UUID.randomUUID().toString();

					LogManager.getLogger().info("Starting buildplate instance {}", instanceId);

					Instance instance = InstanceManager.this.starter.startInstance(instanceId, startRequest.playerId, startRequest.buildplateId, buildplateSource, survival, startRequest.night, saveEnabled, inventoryType, shutdownTime);
					if (instance == null)
					{
						LogManager.getLogger().error("Error starting buildplate instance {}", instanceId);
						return null;
					}
					InstanceManager.this.sendEventBusMessageJson("started", new StartNotification(
							instanceId,
							startRequest.playerId,
							startRequest.encounterId,
							startRequest.buildplateId,
							instance.publicAddress,
							instance.port,
							startRequest.type
					));

					new Thread(() ->
					{
						instance.waitForReady();

						InstanceManager.this.sendEventBusMessage("ready", instance.instanceId);

						instance.waitForShutdown();

						InstanceManager.this.sendEventBusMessage("stopped", instance.instanceId);

						InstanceManager.this.lock.lock();
						InstanceManager.this.runningInstanceCount -= 1;
						InstanceManager.this.lock.unlock();
					}).start();

					return instanceId;
				}
				else if (request.type.equals("preview"))
				{
					record PreviewRequest(
							@NotNull String serverDataBase64,
							boolean night
					)
					{
					}

					PreviewRequest previewRequest;
					byte[] serverData;
					try
					{
						previewRequest = new Gson().fromJson(request.data, PreviewRequest.class);
						serverData = Base64.getDecoder().decode(previewRequest.serverDataBase64);
					}
					catch (Exception exception)
					{
						LogManager.getLogger().warn("Bad preview request", exception);
						return null;
					}

					LogManager.getLogger().info("Generating buildplate preview");

					String preview = InstanceManager.this.previewGenerator.generatePreview(serverData, previewRequest.night);
					if (preview == null)
					{
						LogManager.getLogger().warn("Could not generate preview for buildplate");
					}

					return preview;
				}
				else
				{
					return null;
				}
			}

			@Override
			public void error()
			{
				LogManager.getLogger().error("Event bus request handler error");
			}
		});
	}

	private void sendEventBusMessage(@NotNull String type, @NotNull String message)
	{
		InstanceManager.this.publisher.publish("buildplates", type, message).thenAccept(success ->
		{
			if (!success)
			{
				LogManager.getLogger().error("Event bus publisher error");
			}
		});
	}

	private void sendEventBusMessageJson(@NotNull String type, Object messageObject)
	{
		this.sendEventBusMessage(type, new Gson().newBuilder().serializeNulls().create().toJson(messageObject));
	}

	public void shutdown()
	{
		this.requestHandler.close();

		this.lock.lock();
		this.shuttingDown = true;
		LogManager.getLogger().info("Shutdown signal received, no new buildplate instances will be started, waiting for {} instances to finish", this.runningInstanceCount);
		while (this.runningInstanceCount > 0)
		{
			int runningInstanceCount = this.runningInstanceCount;
			this.lock.unlock();

			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException exception)
			{
				// empty
			}

			this.lock.lock();
			if (this.runningInstanceCount != runningInstanceCount)
			{
				LogManager.getLogger().info("Waiting for {} instances to finish", this.runningInstanceCount);
			}
		}
		this.lock.unlock();

		this.publisher.close();
	}
}