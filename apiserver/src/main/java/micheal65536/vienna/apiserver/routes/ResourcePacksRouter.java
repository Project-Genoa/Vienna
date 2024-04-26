package micheal65536.vienna.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;

public class ResourcePacksRouter extends Router
{
	public ResourcePacksRouter()
	{
		// TODO: make this configurable
		record ResourcePack(
				int order,
				@NotNull String resourcePackId,
				@NotNull String resourcePackVersion,
				int[] parsedResourcePackVersion,
				@NotNull String relativePath
		)
		{
		}
		this.addHandler(new Route.Builder(Request.Method.GET, "/resourcepacks/$buildNumber/default").build(), request -> Response.okFromJson(new EarthApiResponse<>(new ResourcePack[]{
				new ResourcePack(
						0,
						"dba38e59-091a-4826-b76a-a08d7de5a9e2",
						"2020.1214.04",
						new int[]{2020, 1214, 4},
						"availableresourcepack/resourcepacks/dba38e59-091a-4826-b76a-a08d7de5a9e2-1301b0c257a311678123b9e7325d0d6c61db3c35"
				)
		}), EarthApiResponse.class));
	}
}