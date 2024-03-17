package micheal65536.vienna.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;

public class CatalogRouter extends Router
{
	public CatalogRouter(@NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/inventory/catalogv3").build(), request -> Response.okFromJson(new EarthApiResponse<>(catalog.itemsCatalog), EarthApiResponse.class));
		this.addHandler(new Route.Builder(Request.Method.GET, "/recipes").build(), request -> Response.okFromJson(new EarthApiResponse<>(catalog.recipesCatalog), EarthApiResponse.class));
		this.addHandler(new Route.Builder(Request.Method.GET, "/journal/catalog").build(), request -> Response.okFromJson(new EarthApiResponse<>(catalog.journalCatalog), EarthApiResponse.class));
		this.addHandler(new Route.Builder(Request.Method.GET, "/products/catalog").build(), request -> Response.okFromJson(new EarthApiResponse<>(catalog.nfcBoostsCatalog), EarthApiResponse.class));
	}
}