package micheal65536.vienna.apiserver.routes;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;

import java.util.HashMap;
import java.util.Locale;

public final class SigninRouter extends Router
{
	public SigninRouter()
	{
		this.addHandler(
				new Route.Builder(Request.Method.POST, "/player/profile/signin")
						.addHeaderParameter("sessionId", "Session-Id")
						.build(),
				request ->
				{
					record SigninRequest(String sessionTicket)
					{
					}
					SigninRequest signinRequest = request.getBodyAsJson(SigninRequest.class);

					String[] parts = signinRequest.sessionTicket.split("-", 2);
					if (parts.length != 2)
					{
						return Response.badRequest();
					}

					String userId = parts[0];
					if (!userId.matches("^[0-9A-F]{15,16}$"))
					{
						return Response.badRequest();
					}

					// TODO: check credentials

					// TODO: generate secure session token
					String token = userId.toLowerCase(Locale.ROOT);

					return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>()
							.put("basePath", "/auth")
							.put("authenticationToken", token)
							.put("clientProperties", new HashMap<>())
							.put("mixedReality", null)
							.put("mrToken", null)
							.put("streams", null)
							.put("tokens", new HashMap<>())
							.put("updates", new HashMap<>())
							.getMap()), EarthApiResponse.class);
				}
		);
	}
}
