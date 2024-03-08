package micheal65536.minecraftearth.eventbus.client;

import org.jetbrains.annotations.NotNull;

public final class Subscriber
{
	private final EventBusClient client;
	final int channelId;

	final String queueName;

	private final SubscriberListener listener;

	Subscriber(@NotNull EventBusClient client, int channelId, @NotNull String queueName, @NotNull SubscriberListener listener)
	{
		this.client = client;
		this.channelId = channelId;
		this.queueName = queueName;
		this.listener = listener;
	}

	public void close()
	{
		this.client.removeSubscriber(this.channelId);
		this.client.sendMessage(this.channelId, "CLOSE");
	}

	boolean handleMessage(@NotNull String message)
	{
		if (message.equals("ERR"))
		{
			this.close();
			this.listener.error();
			return true;
		}
		else
		{
			String[] fields = message.split(":", 3);
			if (fields.length != 3)
			{
				return false;
			}

			String timestampString = fields[0];
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
			String type = fields[1];
			String data = fields[2];

			this.listener.event(new Event(timestamp, type, data));

			return true;
		}
	}

	void error()
	{
		this.listener.error();
	}

	public interface SubscriberListener
	{
		void event(@NotNull Event event);

		void error();
	}

	public static final class Event
	{
		public final long timestamp;
		@NotNull
		public final String type;
		@NotNull
		public final String data;

		private Event(long timestamp, @NotNull String type, @NotNull String data)
		{
			this.timestamp = timestamp;
			this.type = type;
			this.data = data;
		}
	}
}