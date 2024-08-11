package micheal65536.vienna.apiserver.types.boosts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.types.common.Effect;

import java.util.HashMap;

public record Boosts(
		Potion[] potions,
		MiniFig[] miniFigs,
		@NotNull ActiveEffect[] activeEffects,
		@NotNull HashMap<String, ScenarioBoost[]> scenarioBoosts,
		@NotNull StatusEffects statusEffects,
		@NotNull HashMap<String, MiniFigRecord> miniFigRecords,
		@Nullable String expiration
)
{
	public record Potion(
			boolean enabled,
			@NotNull String itemId,
			@NotNull String instanceId,
			@NotNull String expiration
	)
	{
	}

	public record MiniFig(
			// TODO
	)
	{
	}

	public record ActiveEffect(
			@NotNull Effect effect,
			@NotNull String expiration
	)
	{
	}

	public record ScenarioBoost(
			boolean enabled,
			@NotNull String instanceId,
			@NotNull Effect[] effects,
			@NotNull String expiration
	)
	{
	}

	public record StatusEffects(
			@Nullable Integer tappableInteractionRadius,
			@Nullable Integer experiencePointRate,
			@Nullable Integer itemExperiencePointRates,
			@Nullable Integer attackDamageRate,
			@Nullable Integer playerDefenseRate,
			@Nullable Integer blockDamageRate,
			@Nullable Integer maximumPlayerHealth,
			@Nullable Integer craftingSpeed,
			@Nullable Integer smeltingFuelIntensity,
			@Nullable Integer foodHealthRate
	)
	{
	}

	public record MiniFigRecord(
			// TODO
	)
	{
	}
}