package micheal65536.vienna.db.model.player;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

	public static abstract class Token
	{
		@NotNull
		public final Type type;

		private Token(@NotNull Type type)
		{
			this.type = type;
		}

		public enum Type
		{
			LEVEL_UP,
			JOURNAL_ITEM_UNLOCKED
		}

		public static class Deserializer implements JsonDeserializer<Token>
		{
			private static class BaseToken extends Token
			{
				private BaseToken(@NotNull Type type)
				{
					super(type);
				}
			}

			@Override
			public Token deserialize(JsonElement jsonElement, java.lang.reflect.Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException
			{
				BaseToken baseToken = jsonDeserializationContext.deserialize(jsonElement, BaseToken.class);
				return jsonDeserializationContext.deserialize(jsonElement, switch (baseToken.type)
				{
					case LEVEL_UP -> LevelUpToken.class;
					case JOURNAL_ITEM_UNLOCKED -> JournalItemUnlockedToken.class;
				});
			}
		}
	}

	public static final class LevelUpToken extends Token
	{
		public final int level;

		public LevelUpToken(int level)
		{
			super(Type.LEVEL_UP);
			this.level = level;
		}
	}

	public static final class JournalItemUnlockedToken extends Token
	{
		@NotNull
		public final String itemId;

		public JournalItemUnlockedToken(@NotNull String itemId)
		{
			super(Type.JOURNAL_ITEM_UNLOCKED);
			this.itemId = itemId;
		}
	}
}