package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestSender;
import micheal65536.vienna.eventbus.client.Subscriber;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public final class BuildplateInstancesManager
{
	private final EventBusClient eventBusClient;
	private final Subscriber subscriber;
	private final RequestSender requestSender;

	private final HashMap<String, CompletableFuture<Boolean>> pendingInstances = new HashMap<>();
	private final HashMap<String, InstanceInfo> instances = new HashMap<>();

	public BuildplateInstancesManager(@NotNull EventBusClient eventBusClient)
	{
		this.eventBusClient = eventBusClient;
		this.subscriber = eventBusClient.addSubscriber("buildplates", new Subscriber.SubscriberListener()
		{
			@Override
			public void event(@NotNull Subscriber.Event event)
			{
				BuildplateInstancesManager.this.handleEvent(event);
			}

			@Override
			public void error()
			{
				LogManager.getLogger().fatal("Buildplates event bus subscriber error");
				System.exit(1);
			}
		});
		this.requestSender = eventBusClient.addRequestSender();
	}

	@Nullable
	public String startBuildplateInstance(@NotNull String playerId, @NotNull String buildplateId, boolean night)
	{
		LogManager.getLogger().info("Requesting buildplate instance for player {} buildplate {}", playerId, buildplateId);

		String instanceId = this.requestSender.request("buildplates", "start", new Gson().toJson(new StartRequest(playerId, buildplateId, false, night))).join();
		if (instanceId == null)
		{
			LogManager.getLogger().error("Buildplate start request was rejected/ignored");
			return null;
		}

		CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
		synchronized (this.instances)
		{
			if (this.instances.containsKey(instanceId))
			{
				completableFuture.complete(true);
			}
			else
			{
				synchronized (this.pendingInstances)
				{
					this.pendingInstances.put(instanceId, completableFuture);
				}
			}
		}

		if (!completableFuture.join())
		{
			LogManager.getLogger().warn("Could not start buildplate instance {}", instanceId);
			return null;
		}
		return instanceId;
	}

	@Nullable
	public InstanceInfo getInstanceInfo(@NotNull String instanceId)
	{
		synchronized (this.instances)
		{
			return this.instances.getOrDefault(instanceId, null);
		}
	}

	private void handleEvent(@NotNull Subscriber.Event event)
	{
		switch (event.type)
		{
			case "started" ->
			{
				StartNotification startNotification;
				try
				{
					startNotification = new Gson().fromJson(event.data, StartNotification.class);

					synchronized (this.instances)
					{
						LogManager.getLogger().info("Buildplate instance {} has started", startNotification.instanceId);
						this.instances.put(startNotification.instanceId, new InstanceInfo(
								startNotification.instanceId,
								startNotification.playerId,
								startNotification.buildplateId,
								startNotification.address,
								startNotification.port,
								false
						));
					}

					synchronized (this.pendingInstances)
					{
						CompletableFuture<Boolean> completableFuture = this.pendingInstances.remove(startNotification.instanceId);
						if (completableFuture != null)
						{
							completableFuture.complete(true);
						}
					}
				}
				catch (Exception exception)
				{
					LogManager.getLogger().warn("Bad start notification", exception);
				}
			}
			case "ready" ->
			{
				String instanceId = event.data;
				synchronized (this.instances)
				{
					InstanceInfo instanceInfo = this.instances.getOrDefault(instanceId, null);
					if (instanceInfo != null)
					{
						LogManager.getLogger().info("Buildplate instance {} is ready", instanceId);
						this.instances.put(instanceId, new InstanceInfo(
								instanceInfo.instanceId,
								instanceInfo.playerId,
								instanceInfo.buildplateId,
								instanceInfo.address,
								instanceInfo.port,
								true
						));
					}
				}
			}
			case "stopped" ->
			{
				String instanceId = event.data;
				synchronized (this.instances)
				{
					if (this.instances.remove(instanceId) != null)
					{
						LogManager.getLogger().info("Buildplate instance {} has stopped", instanceId);
					}
				}
			}
		}
	}

	private record StartRequest(
			@NotNull String playerId,
			@NotNull String buildplateId,
			boolean survival,
			boolean night
	)
	{
	}

	private record StartNotification(
			@NotNull String instanceId,
			@NotNull String playerId,
			@NotNull String buildplateId,
			@NotNull String address,
			int port
	)
	{
	}

	public record InstanceInfo(
			@NotNull String instanceId,

			@NotNull String playerId,
			@NotNull String buildplateId,

			@NotNull String address,
			int port,

			boolean ready
	)
	{
	}
}