package micheal65536.vienna.eventbus.client;

public class EventBusClientException extends Exception
{
	EventBusClientException(String message)
	{
		super(message);
	}

	EventBusClientException(String message, Throwable cause)
	{
		super(message, cause);
	}
}