package micheal65536.minecraftearth.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.minecraftearth.db.model.common.Rewards;

import java.util.HashMap;

public final class Tokens
{
	@NotNull
	private final HashMap<String, Token> tokens;

	public Tokens()
	{
		this.tokens = new HashMap<>();
	}

	@NotNull
	public Tokens copy()
	{
		Tokens tokens = new Tokens();
		tokens.tokens.putAll(this.tokens);
		return tokens;
	}

	public record TokenWithId(
			@NotNull String id,
			@NotNull Token token
	)
	{
	}

	@NotNull
	public TokenWithId[] getTokens()
	{
		return this.tokens.entrySet().stream().map(entry -> new TokenWithId(entry.getKey(), entry.getValue())).toArray(TokenWithId[]::new);
	}

	public void addToken(@NotNull String id, @NotNull Token token)
	{
		this.tokens.put(id, token);
	}

	@Nullable
	public Token removeToken(@NotNull String id)
	{
		return this.tokens.remove(id);
	}

	public record Token(
			@NotNull Type type,
			@NotNull Rewards rewards,
			@NotNull Lifetime lifetime,
			@NotNull HashMap<String, String> properties
	)
	{
		public enum Type
		{
			LEVEL_UP
		}

		public enum Lifetime
		{
			PERSISTENT,
			TRANSIENT
		}
	}
}