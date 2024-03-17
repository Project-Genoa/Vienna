package micheal65536.vienna.apiserver.types.catalog;

import org.jetbrains.annotations.NotNull;

public record RecipesCatalog(
		@NotNull CraftingRecipe[] crafting,
		@NotNull SmeltingRecipe[] smelting
)
{
	public record CraftingRecipe(
			@NotNull String id,
			@NotNull String category,
			@NotNull String duration,
			@NotNull Ingredient[] ingredients,
			@NotNull Output output,
			@NotNull ReturnItem[] returnItems,
			boolean deprecated
	)
	{
		public record Ingredient(
				@NotNull String[] items,
				int quantity
		)
		{
		}

		public record Output(
				@NotNull String itemId,
				int quantity
		)
		{
		}

		public record ReturnItem(
				@NotNull String id,
				int amount
		)
		{
		}
	}

	public record SmeltingRecipe(
			@NotNull String id,
			int heatRequired,
			@NotNull String inputItemId,
			@NotNull Output output,
			@NotNull ReturnItem[] returnItems,
			boolean deprecated
	)
	{
		public record Output(
				@NotNull String itemId,
				int quantity
		)
		{
		}

		public record ReturnItem(
				@NotNull String id,
				int amount
		)
		{
		}
	}
}