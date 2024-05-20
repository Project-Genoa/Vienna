package micheal65536.vienna.apiserver.utils;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.staticdata.Catalog;

public final class ItemWear
{
	public static float wearToHealth(@NotNull String itemId, int wear, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		Catalog.ItemsCatalog.Item catalogItem = itemsCatalog.getItem(itemId);
		if (catalogItem == null || catalogItem.toolInfo() == null)
		{
			LogManager.getLogger().warn("Attempt to get item health for non-tool item {}", itemId);
			return 100.0f;
		}
		return ((float) (catalogItem.toolInfo().maxWear() - wear) / (float) catalogItem.toolInfo().maxWear()) * 100.0f;
	}
}