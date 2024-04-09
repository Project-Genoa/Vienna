package micheal65536.vienna.buildplate.connector.plugin;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import micheal65536.fountain.connector.plugin.ConnectorPlugin;
import micheal65536.vienna.buildplate.connector.model.RequestResponseMessage;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.Publisher;
import micheal65536.vienna.eventbus.client.Subscriber;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class EventBusHelper
{
	public static void publishJson(@NotNull Publisher publisher, @NotNull String queueName, @NotNull String type, Object messageObject) throws ConnectorPlugin.ConnectorPluginException
	{
		CompletableFuture<Boolean> completableFuture = publisher.publish(queueName, type, new Gson().newBuilder().serializeNulls().create().toJson(messageObject));
		boolean success;
		for (; ; )
		{
			try
			{
				success = completableFuture.get();
				break;
			}
			catch (ExecutionException exception)
			{
				throw new AssertionError(exception);
			}
			catch (InterruptedException exception)
			{
				continue;
			}
		}
		if (!success)
		{
			throw new ConnectorPlugin.ConnectorPluginException();
		}
	}

	public static <T> T receiveJson(@NotNull String messageString, @NotNull Class<T> messageClass) throws ConnectorPlugin.ConnectorPluginException
	{
		try
		{
			return new Gson().fromJson(messageString, messageClass);
		}
		catch (Exception exception)
		{
			throw new ConnectorPlugin.ConnectorPluginException(exception);
		}
	}

	@NotNull
	public static <T> CompletableFuture<T> doRequestResponse(@NotNull EventBusClient eventBusClient, @NotNull String queueName, @NotNull String requestType, @NotNull String responseType, Object requestObject, @NotNull Class<T> responseClass)
	{
		String requestId = UUID.randomUUID().toString();

		CompletableFuture<T> completableFuture = new CompletableFuture<>();

		Subscriber subscriber = eventBusClient.addSubscriber(queueName, new Subscriber.SubscriberListener()
		{
			@Override
			public void event(@NotNull Subscriber.Event event)
			{
				if (!completableFuture.isDone())
				{
					if (event.type.equals(responseType))
					{
						try
						{
							RequestResponseMessage requestResponseMessage = new Gson().fromJson(event.data, RequestResponseMessage.class);
							if (requestResponseMessage.requestId().equals(requestId))
							{
								completableFuture.complete(new Gson().fromJson(requestResponseMessage.message(), responseClass));
							}
						}
						catch (Exception exception)
						{
							completableFuture.complete(null);
						}
					}
				}
			}

			@Override
			public void error()
			{
				if (!completableFuture.isDone())
				{
					completableFuture.complete(null);
				}
			}
		});

		Publisher publisher = eventBusClient.addPublisher();
		Gson gson = new Gson().newBuilder().serializeNulls().create();
		publisher.publish(queueName, requestType, gson.toJson(new RequestResponseMessage(requestId, gson.toJson(requestObject)))).thenAccept(success ->
		{
			if (!success)
			{
				completableFuture.complete(null);
			}
			publisher.close();
		});

		return completableFuture.thenApply(response ->
		{
			subscriber.close();
			return response;
		});
	}

	@NotNull
	public static <T> T doRequestResponseSync(@NotNull EventBusClient eventBusClient, @NotNull String queueName, @NotNull String requestType, @NotNull String responseType, Object requestObject, @NotNull Class<T> responseClass) throws ConnectorPlugin.ConnectorPluginException
	{
		CompletableFuture<T> completableFuture = EventBusHelper.doRequestResponse(eventBusClient, queueName, requestType, responseType, requestObject, responseClass);
		for (; ; )
		{
			try
			{
				T response = completableFuture.get();
				if (response == null)
				{
					throw new ConnectorPlugin.ConnectorPluginException();
				}
				return response;
			}
			catch (ExecutionException exception)
			{
				throw new AssertionError(exception);
			}
			catch (InterruptedException exception)
			{
				continue;
			}
		}
	}
}