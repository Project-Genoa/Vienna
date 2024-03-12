package micheal65536.minecraftearth.objectstore.client;

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