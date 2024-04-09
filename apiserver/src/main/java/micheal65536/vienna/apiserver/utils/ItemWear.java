package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.types.catalog.ItemsCatalog;

import java.util.Arrays;

public final class ItemWear
{
	public static float wearToHealth(@NotNull String itemId, int wear, @NotNull ItemsCatalog itemsCatalog)
	{
		ItemsCatalog.Item catalogItem = Arrays.stream(itemsCatalog.items()).filter(item -> item.id().equals(itemId)).findFirst().orElseThrow();
		return ((float) (catalogItem.item().health() - wear) / (float) catalogItem.item().health()) * 100.0f;
	}
}