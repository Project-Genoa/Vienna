package micheal65536.vienna.utils.http.routing;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.function.Function;

public final class Request
{
	public final long timestamp;
	@NotNull
	public final Path path;
	@NotNull
	public final Method method;
	final HashMap<String, String> parameters = new HashMap<>();
	final HashMap<String, Object> contextData = new HashMap<>();
	private final String body;

	Request(long timestamp, @NotNull Path path, @NotNull Method method, @NotNull String body)
	{
		this.timestamp = timestamp;
		this.path = path;
		this.method = method;
		this.body = body;
	}

	public <T> void addContextData(@NotNull String name, @NotNull T value)
	{
		this.contextData.put(name, value);
	}

	@NotNull
	public String getParameter(@NotNull String name) throws BadRequestException
	{
		return this.getParameter(name, true, null);
	}

	public String getParameter(@NotNull String name, @Nullable String defaultValue) throws BadRequestException
	{
		return this.getParameter(name, false, defaultValue);
	}

	public int getParameterInt(@NotNull String name) throws BadRequestException
	{
		return this.getConvertedParameter(name, true, 0, Integer::parseInt);
	}

	public int getParameterInt(@NotNull String name, int defaultValue) throws BadRequestException
	{
		return this.getConvertedParameter(name, false, defaultValue, Integer::parseInt);
	}

	public float getParameterFloat(@NotNull String name) throws BadRequestException
	{
		return this.getConvertedParameter(name, true, 0.0f, Float::parseFloat);
	}

	public float getParameterFloat(@NotNull String name, float defaultValue) throws BadRequestException
	{
		return this.getConvertedParameter(name, false, defaultValue, Float::parseFloat);
	}

	public boolean getParameterBooleanByPresence(@NotNull String name) throws BadRequestException
	{
		return this.parameters.containsKey(name);
	}

	@NotNull
	public <T> T getContextData(@NotNull String name) throws BadRequestException
	{
		T data = (T) this.contextData.getOrDefault(name, null);
		if (data == null)
		{
			throw new BadRequestException("Missing required context data");
		}
		return data;
	}

	@NotNull
	public String getBody() throws BadRequestException
	{
		return this.body;
	}

	@NotNull
	public <T> T getBodyAsJson(@NotNull Class<T> tClass) throws BadRequestException
	{
		try
		{
			return new Gson().fromJson(this.getBody(), tClass);
		}
		catch (Exception exception)
		{
			throw new BadRequestException(exception);
		}
	}

	private String getParameter(String name, boolean required, String defaultValue) throws BadRequestException
	{
		String parameter = this.parameters.getOrDefault(name, null);
		if (parameter == null)
		{
			if (required)
			{
				throw new BadRequestException("Missing required parameter %s".formatted(name));
			}
			else
			{
				return defaultValue;
			}
		}
		return parameter;
	}

	private <T> T getConvertedParameter(String name, boolean required, T defaultValue, Function<String, T> converter) throws BadRequestException
	{
		String parameter = this.getParameter(name, required, null);
		if (parameter == null)
		{
			return defaultValue;
		}
		try
		{
			T converted = converter.apply(parameter);
			if (converted == null)
			{
				throw new BadRequestException("Could not convert parameter %s to requested type".formatted(name));
			}
			return converted;
		}
		catch (Exception exception)
		{
			throw new BadRequestException("Could not convert parameter %s to requested type".formatted(name), exception);
		}
	}

	public enum Method
	{
		HEAD,
		GET,
		POST,
		PUT
	}

	public static final class BadRequestException extends Exception
	{
		private BadRequestException(String message)
		{
			super(message);
		}

		private BadRequestException(String message, Throwable cause)
		{
			super(message, cause);
		}

		private BadRequestException(Throwable cause)
		{
			super(cause);
		}
	}
}