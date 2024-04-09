package micheal65536.vienna.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.apiserver.routes.buildplate.PlayersRouter;
import micheal65536.vienna.apiserver.routes.buildplate.SnapshotsRouter;
import micheal65536.vienna.apiserver.routing.Filter;
import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;

public class BuildplateInstanceRouter extends Router
{
	public BuildplateInstanceRouter(@NotNull EarthDB earthDB, @NotNull ObjectStoreClient objectStoreClient, @NotNull Catalog catalog, @NotNull String buildplatePreviewGeneratorCommand)
	{
		Filter authFilter = request ->
		{
			String token = request.getParameter("token");

			// TODO: validate token

			return null;
		};
		this.addFilter(
				new Route.Builder(Request.Method.GET, "/*").addHeaderParameter("token", "Vienna-Buildplate-Instance-Token").build(),
				authFilter
		);
		this.addFilter(
				new Route.Builder(Request.Method.POST, "/*").addHeaderParameter("token", "Vienna-Buildplate-Instance-Token").build(),
				authFilter
		);

		this.addSubRouter("/*", 0, new PlayersRouter(earthDB, catalog));
		this.addSubRouter("/*", 0, new SnapshotsRouter(earthDB, objectStoreClient, buildplatePreviewGeneratorCommand));
	}
}