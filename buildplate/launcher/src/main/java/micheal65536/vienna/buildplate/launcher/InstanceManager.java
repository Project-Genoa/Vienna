package micheal65536.vienna.buildplate.launcher;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

					record StartRequest(
							@NotNull String playerId,
							@NotNull String buildplateId,
							boolean survival,
							boolean night
					)
					{
					}

					record StartNotification(
							@NotNull String instanceId,
							@NotNull String playerId,
							@NotNull String buildplateId,
							@NotNull String address,
							int port
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

					String instanceId = UUID.randomUUID().toString();

					LogManager.getLogger().info("Starting buildplate instance {} for player {} buildplate {}", instanceId, startRequest.playerId, startRequest.buildplateId);

					Instance instance = InstanceManager.this.starter.startInstance(instanceId, startRequest.playerId, startRequest.buildplateId, startRequest.survival, startRequest.night);
					if (instance == null)
					{
						LogManager.getLogger().error("Error starting buildplate instance {}", instanceId);
						return null;
					}
					InstanceManager.this.sendEventBusMessageJson("started", new StartNotification(
							instanceId,
							startRequest.playerId,
							startRequest.buildplateId,
							instance.publicAddress,
							instance.port
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
		this.publisher.close();

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
	}
}