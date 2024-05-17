package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestSender;
import micheal65536.vienna.eventbus.client.Subscriber;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;

public final class BuildplateInstancesManager
{
	private final EventBusClient eventBusClient;
	private final Subscriber subscriber;
	private final RequestSender requestSender;

	private final HashMap<String, CompletableFuture<Boolean>> pendingInstances = new HashMap<>();
	private final HashMap<String, InstanceInfo> instances = new HashMap<>();
	private final HashMap<String, LinkedHashSet<String>> instancesByBuildplateId = new HashMap<>();

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
	public String requestBuildplateInstance(@Nullable String playerId, @Nullable String encounterId, @NotNull String buildplateId, @NotNull InstanceType type, long shutdownTime, boolean night)
	{
		if (playerId == null && type != InstanceType.ENCOUNTER)
		{
			throw new IllegalArgumentException();
		}
		if (encounterId != null && type != InstanceType.ENCOUNTER)
		{
			throw new IllegalArgumentException();
		}

		if (playerId != null && encounterId != null)
		{
			LogManager.getLogger().info("Finding buildplate instance for buildplate {} type {} encounter {} player {}", buildplateId, type, encounterId, playerId);
		}
		else if (playerId != null)
		{
			LogManager.getLogger().info("Finding buildplate instance for buildplate {} type {} player {}", buildplateId, type, playerId);
		}
		else if (encounterId != null)
		{
			LogManager.getLogger().info("Finding buildplate instance for buildplate {} type {} encounter {}", buildplateId, type, encounterId);
		}
		else
		{
			LogManager.getLogger().info("Finding buildplate instance for buildplate {} type {}", buildplateId, type);
		}

		synchronized (this.instances)
		{
			LinkedHashSet<String> instanceIds = this.instancesByBuildplateId.getOrDefault(buildplateId, null);
			if (instanceIds != null)
			{
				for (String instanceId : instanceIds)
				{
					InstanceInfo instanceInfo = this.instances.getOrDefault(instanceId, null);
					if (instanceInfo != null)
					{
						if (
								instanceInfo.type == type &&
										((instanceInfo.playerId == null && playerId == null) || (instanceInfo.playerId != null && playerId != null && instanceInfo.playerId.equals(playerId))) &&
										((instanceInfo.encounterId == null && encounterId == null) || (instanceInfo.encounterId != null && encounterId != null && instanceInfo.encounterId.equals(encounterId)))
						)
						{
							LogManager.getLogger().info("Found existing buildplate instance {}", instanceId);
							return instanceId;
						}
					}
				}
			}
		}

		LogManager.getLogger().info("Did not find existing instance, starting new instance");
		String instanceId = this.requestSender.request("buildplates", "start", new Gson().toJson(new StartRequest(playerId, encounterId, buildplateId, night, type, shutdownTime))).join();
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

	@Nullable
	public String getBuildplatePreview(byte[] serverData, boolean night)
	{
		LogManager.getLogger().info("Requesting buildplate preview");

		String preview = this.requestSender.request("buildplates", "preview", new Gson().toJson(new PreviewRequest(Base64.getEncoder().encodeToString(serverData), night))).join();
		if (preview == null)
		{
			LogManager.getLogger().error("Preview request was rejected/ignored");
		}

		return preview;
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
					if (startNotification.playerId == null && startNotification.type != InstanceType.ENCOUNTER)
					{
						LogManager.getLogger().warn("Bad start notification");
						return;
					}

					synchronized (this.instances)
					{
						LogManager.getLogger().info("Buildplate instance {} has started", startNotification.instanceId);
						this.instances.put(startNotification.instanceId, new InstanceInfo(
								startNotification.type,
								startNotification.instanceId,
								startNotification.playerId,
								startNotification.encounterId,
								startNotification.buildplateId,
								startNotification.address,
								startNotification.port,
								false
						));
						this.instancesByBuildplateId.computeIfAbsent(startNotification.buildplateId, buildplateId -> new LinkedHashSet<>()).add(startNotification.instanceId);
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
								instanceInfo.type,
								instanceInfo.instanceId,
								instanceInfo.playerId,
								instanceInfo.encounterId,
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
					InstanceInfo instanceInfo = this.instances.remove(instanceId);
					if (instanceInfo != null)
					{
						LogManager.getLogger().info("Buildplate instance {} has stopped", instanceId);

						LinkedHashSet<String> instanceIds = this.instancesByBuildplateId.getOrDefault(instanceInfo.buildplateId, null);
						if (instanceIds != null)
						{
							instanceIds.remove(instanceInfo.instanceId);
						}
					}
				}
			}
		}
	}

	private record StartRequest(
			@Nullable String playerId,
			@Nullable String encounterId,
			@NotNull String buildplateId,
			boolean night,
			@NotNull InstanceType type,
			long shutdownTime
	)
	{
	}

	private record PreviewRequest(
			@NotNull String serverDataBase64,
			boolean night
	)
	{
	}

	private record StartNotification(
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

	public enum InstanceType
	{
		BUILD,
		PLAY,
		SHARED_BUILD,
		SHARED_PLAY,
		ENCOUNTER
	}

	public record InstanceInfo(
			@NotNull InstanceType type,

			@NotNull String instanceId,

			@Nullable String playerId,
			@Nullable String encounterId,
			@NotNull String buildplateId,

			@NotNull String address,
			int port,

			boolean ready
	)
	{
	}
}