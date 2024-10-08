package micheal65536.vienna.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Filter;
import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.utils.BuildplateInstancesManager;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;
import micheal65536.vienna.staticdata.StaticData;

public class AuthenticatedRouter extends Router
{
	public AuthenticatedRouter(@NotNull EarthDB earthDB, @NotNull StaticData staticData, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull BuildplateInstancesManager buildplateInstancesManager)
	{
		Filter authFilter = request ->
		{
			String sessionId = request.getParameter("sessionId");
			String authorization = request.getParameter("authorization");
			String[] parts = authorization.split(" ");
			if (parts.length != 2 || !parts[0].equals("Genoa"))
			{
				return Response.badRequest();
			}
			String sessionToken = parts[1];

			// TODO: check session token and properly get player ID
			String playerId = sessionToken;

			request.addContextData("playerId", playerId);
			return null;
		};
		this.addFilter(
				new Route.Builder(Request.Method.HEAD, "/*").addHeaderParameter("sessionId", "Session-Id").addHeaderParameter("authorization", "Authorization").build(),
				authFilter
		);
		this.addFilter(
				new Route.Builder(Request.Method.GET, "/*").addHeaderParameter("sessionId", "Session-Id").addHeaderParameter("authorization", "Authorization").build(),
				authFilter
		);
		this.addFilter(
				new Route.Builder(Request.Method.POST, "/*").addHeaderParameter("sessionId", "Session-Id").addHeaderParameter("authorization", "Authorization").build(),
				authFilter
		);
		this.addFilter(
				new Route.Builder(Request.Method.PUT, "/*").addHeaderParameter("sessionId", "Session-Id").addHeaderParameter("authorization", "Authorization").build(),
				authFilter
		);
		this.addFilter(
				new Route.Builder(Request.Method.DELETE, "/*").addHeaderParameter("sessionId", "Session-Id").addHeaderParameter("authorization", "Authorization").build(),
				authFilter
		);

		this.addSubRouter("/*", 0, new PlayerRouter(earthDB, eventBusClient, objectStoreClient, buildplateInstancesManager, staticData));
		this.addSubRouter("/*", 0, new CatalogRouter(staticData.catalog));
		this.addSubRouter("/*", 0, new EnvironmentSettingsRouter());
	}
}