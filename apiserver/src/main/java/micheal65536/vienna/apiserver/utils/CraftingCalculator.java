package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.model.common.NonStackableItemInstance;
import micheal65536.vienna.db.model.player.workshop.CraftingSlot;
import micheal65536.vienna.db.model.player.workshop.InputItem;
import micheal65536.vienna.staticdata.Catalog;

import java.util.Arrays;
import java.util.LinkedList;

public final class CraftingCalculator
{
	@NotNull
	public static State calculateState(long currentTime, @NotNull CraftingSlot.ActiveJob activeJob, @NotNull Catalog catalog)
	{
		Catalog.RecipesCatalog.CraftingRecipe recipe = catalog.recipesCatalog.getCraftingRecipe(activeJob.recipeId());

		long roundDuration = recipe.duration() * 1000;
		int completedRounds = activeJob.finishedEarly() ? activeJob.totalRounds() : Math.min((int) ((currentTime - activeJob.startTime()) / roundDuration), activeJob.totalRounds());
		int availableRounds = completedRounds - activeJob.collectedRounds();

		LinkedList<InputItem> input = new LinkedList<>();
		if (activeJob.input().length != recipe.ingredients().length)
		{
			throw new AssertionError();
		}
		for (int index = 0; index < recipe.ingredients().length; index++)
		{
			int usedCount = recipe.ingredients()[index].count() * completedRounds;
			InputItem[] inputItems = activeJob.input()[index];
			for (InputItem inputItem : inputItems)
			{
				if (usedCount == 0)
				{
					input.add(inputItem);
				}
				else if (usedCount > inputItem.count())
				{
					usedCount -= inputItem.count();
				}
				else
				{
					if (inputItem.instances().length > 0)
					{
						if (inputItem.instances().length != inputItem.count())
						{
							throw new AssertionError();
						}
						input.add(new InputItem(inputItem.id(), inputItem.count() - usedCount, Arrays.copyOfRange(inputItem.instances(), usedCount, inputItem.instances().length)));
					}
					else
					{
						input.add(new InputItem(inputItem.id(), inputItem.count() - usedCount, new NonStackableItemInstance[0]));
					}
					usedCount = 0;
				}
			}
		}

		return new State(
				completedRounds,
				availableRounds,
				activeJob.totalRounds(),
				input.toArray(InputItem[]::new),
				new State.OutputItem(recipe.output().itemId(), recipe.output().count()),
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