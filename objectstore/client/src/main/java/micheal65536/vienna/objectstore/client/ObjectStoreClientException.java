package micheal65536.vienna.objectstore.client;

public class ObjectStoreClientException extends Exception
{
	ObjectStoreClientException(String message)
	{
		super(message);
	}

	ObjectStoreClientException(String message, Throwable cause)
	{
		super(message, cause);
	}
}