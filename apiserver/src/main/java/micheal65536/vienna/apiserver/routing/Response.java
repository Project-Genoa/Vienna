package micheal65536.vienna.apiserver.routing;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;

public final class Response
{
	final int statusCode;
	byte[] body;
	String contentType;
	boolean isText;
	final HashMap<String, String> extraHeaders = new HashMap<>();

	private Response(int statusCode)
	{
		this.statusCode = statusCode;
		this.body = new byte[0];
		this.isText = true;
		this.contentType = "text/plain";
	}

	@NotNull
	public static Response create(int statusCode)
	{
		return new Response(statusCode);
	}

	@NotNull
	public static Response serverError()
	{
		return Response.create(500);
	}

	@NotNull
	public static Response badRequest()
	{
		return Response.create(400);
	}

	@NotNull
	public static Response notFound()
	{
		return Response.create(404);
	}

	@NotNull
	public static Response redirect(@NotNull String location)
	{
		return Response.create(302).header("Location", location);
	}

	@NotNull
	public static Response ok(@NotNull String body)
	{
		return Response.create(200).body(body);
	}

	@NotNull
	public static Response ok(byte[] body, @NotNull String contentType)
	{
		return Response.create(200).body(body, contentType);
	}

	@NotNull
	public static <T> Response okFromJson(@NotNull T body, @NotNull Class<T> tClass)
	{
		return Response.create(200).bodyFromJson(body, tClass);
	}

	@NotNull
	public Response body(@NotNull String body)
	{
		this.body = body.getBytes(StandardCharsets.UTF_8);
		this.contentType = "text/plain";
		this.isText = true;
		return this;
	}

	@NotNull
	public Response body(@NotNull String body, @NotNull String contentType)
	{
		this.body = body.getBytes(StandardCharsets.UTF_8);
		this.contentType = contentType;
		this.isText = true;
		return this;
	}

	@NotNull
	public Response body(byte[] body, @NotNull String contentType)
	{
		this.body = Arrays.copyOf(body, body.length);
		this.contentType = contentType;
		this.isText = false;
		return this;
	}

	@NotNull
	public <T> Response bodyFromJson(@NotNull T body, @NotNull Class<T> tClass)
	{
		String bodyString = new Gson().newBuilder().serializeNulls().create().toJson(body, tClass);
		this.body = bodyString.getBytes(StandardCharsets.UTF_8);
		this.contentType = "application/json";
		this.isText = true;
		return this;
	}

	@NotNull
	public Response contentType(@NotNull String contentType)
	{
		this.contentType = contentType;
		return this;
	}

	@NotNull
	public Response header(@NotNull String name, @NotNull String value)
	{
		this.extraHeaders.put(name, value);
		return this;
	}
}