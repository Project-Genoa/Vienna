package micheal65536.minecraftearth.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.apiserver.Catalog;
import micheal65536.minecraftearth.apiserver.types.catalog.RecipesCatalog;
import micheal65536.minecraftearth.db.model.common.NonStackableItemInstance;
import micheal65536.minecraftearth.db.model.player.workshop.CraftingSlot;
import micheal65536.minecraftearth.db.model.player.workshop.InputItem;

import java.util.Arrays;

public final class CraftingCalculator
{
	@NotNull
	public static State calculateState(long currentTime, @NotNull CraftingSlot.ActiveJob activeJob, @NotNull Catalog catalog)
	{
		RecipesCatalog.CraftingRecipe recipe = Arrays.stream(catalog.recipesCatalog.crafting()).filter(craftingRecipe -> craftingRecipe.id().equals(activeJob.recipeId())).findFirst().orElseThrow();

		long roundDuration = TimeFormatter.parseDuration(recipe.duration());
		int completedRounds = activeJob.finishedEarly() ? activeJob.totalRounds() : Math.min((int) ((currentTime - activeJob.startTime()) / roundDuration), activeJob.totalRounds());
		int availableRounds = completedRounds - activeJob.collectedRounds();

		InputItem[] input = new InputItem[recipe.ingredients().length];
		if (activeJob.input().length != recipe.ingredients().length)
		{
			throw new AssertionError();
		}
		for (int index = 0; index < recipe.ingredients().length; index++)
		{
			int usedCount = recipe.ingredients()[index].quantity() * completedRounds;
			InputItem inputItem = activeJob.input()[index];
			if (inputItem.instances().length > 0)
			{
				if (inputItem.instances().length != inputItem.count())
				{
					throw new AssertionError();
				}
				input[index] = new InputItem(inputItem.id(), inputItem.count() - usedCount, Arrays.copyOfRange(inputItem.instances(), usedCount, inputItem.instances().length));
			}
			else
			{
				input[index] = new InputItem(inputItem.id(), inputItem.count() - usedCount, new NonStackableItemInstance[0]);
			}
		}

		return new State(
				completedRounds,
				availableRounds,
				activeJob.totalRounds(),
				input,
				new State.OutputItem(recipe.output().itemId(), recipe.output().quantity()),
				activeJob.startTime() + roundDuration * (completedRounds + 1),
				activeJob.startTime() + roundDuration * activeJob.totalRounds(),
				completedRounds == activeJob.totalRounds()
		);
	}

	public record State(
			int completedRounds,
			int availableRounds,
			int totalRounds,
			@NotNull InputItem[] input,
			@NotNull OutputItem output,
			long nextCompletionTime,
			long totalCompletionTime,
			boolean completed
	)
	{
		public record OutputItem(
				@NotNull String id,
				int count
		)
		{
		}
	}

	// TODO: make this configurable
	@NotNull
	public static FinishPrice calculateFinishPrice(int remainingTime)
	{
		if (remainingTime < 0)
		{
			throw new IllegalArgumentException();
		}

		int periods = remainingTime / 10000;
		if (remainingTime % 10000 > 0)
		{
			periods = periods + 1;
		}
		int price = periods * 5;
		int changesAt = (periods - 1) * 10000;
		int validFor = remainingTime - changesAt;

		return new FinishPrice(price, validFor);
	}

	public record FinishPrice(
			int price,
			int validFor
	)
	{
	}

	// TODO: make this configurable
	public static int calculateUnlockPrice(int slotIndex)
	{
		if (slotIndex < 1 || slotIndex > 3)
		{
			throw new IllegalArgumentException();
		}
		return slotIndex * 5;
	}
}