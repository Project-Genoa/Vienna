package micheal65536.minecraftearth.apiserver.types.catalog;

import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.apiserver.types.common.Rewards;

public record NFCBoost(
		@NotNull String id,
		@NotNull String name,
		@NotNull String type,
		@NotNull Rewards rewards,
		@NotNull BoostMetadata boostMetadata,
		boolean deprecated,
		@NotNull String toolsVersion
)
{
}