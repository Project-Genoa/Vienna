package micheal65536.vienna.eventbus.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public final class RequestHandler
{
	private final EventBusClient client;
	private final int channelId;

	private final String queueName;

	private final Handler handler;

	private volatile boolean closed = false;

	RequestHandler(@NotNull EventBusClient client, int channelId, @NotNull String queueName, @NotNull Handler handler)
	{
		this.client = client;
		this.channelId = channelId;
		this.queueName = queueName;
		this.handler = handler;
	}

	public void close()
	{
		this.closed = true;
		this.client.removeSubscriber(this.channelId);
		this.client.sendMessage(this.channelId, "CLOSE");
	}

	boolean handleMessage(@NotNull String message)
	{
		if (message.equals("ERR"))
		{
			this.close();
			this.handler.error();
			return true;
		}
		else
		{
			String[] fields = message.split(":", 4);
			if (fields.length != 4)
			{
				return false;
			}

			String requestIdString = fields[0];
			int requestId;
			try
			{
				requestId = Integer.parseInt(requestIdString);
			}
			catch (NumberFormatException exception)
			{
				return false;
			}
			if (requestId <= 0)
			{
				return false;
			}
			String timestampString = fields[1];
			long timestamp;
			try
			{
				timestamp = Long.parseLong(timestampString);
			}
			catch (NumberFormatException exception)
			{
				return false;
			}
			if (timestamp < 0)
			{
				return false;
			}
			String type = fields[2];
			String data = fields[3];

			CompletableFuture<String> responseCompletableFuture = this.handler.requestAsync(new Request(timestamp, type, data));
			responseCompletableFuture.thenAccept(response ->
			{
				if (!this.closed)
				{
					if (response != null)
					{
						this.client.sendMessage(this.channelId, "REP " + requestId + ":" + response);
					}
					else
					{
						this.client.sendMessage(this.channelId, "NREP " + requestId);
					}
				}
			});

			return true;
		}
	}

	void error()
	{
		this.closed = true;
		this.handler.error();
	}

	public interface Handler
	{
		@NotNull
		default CompletableFuture<String> requestAsync(@NotNull Request request)
		{
			CompletableFuture<String> completableFuture = new CompletableFuture<>();
			new Thread(() ->
			{
				completableFuture.complete(this.request(request));
			}).start();
			return completableFuture;
		}

		@Nullable
		String request(@NotNull Request request);

		void error();
	}

	public static final class Request
	{
		public final long timestamp;
		@NotNull
		public final String type;
		@NotNull
		public final String data;

		private Request(long timestamp, @NotNull String type, @NotNull String data)
		{
			this.timestamp = timestamp;
			this.type = type;
			this.data = data;
		}
	}
}