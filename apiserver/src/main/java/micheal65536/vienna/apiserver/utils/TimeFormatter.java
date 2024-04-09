package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;

public final class TimeFormatter
{
	private static final SimpleDateFormat JSON_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
	private static final String JSON_DURATION_FORMAT = "%d:%02d:%02d";

	static
	{
		JSON_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	@NotNull
	public static String formatTime(long time)
	{
		return JSON_DATE_FORMAT.format(Date.from(Instant.ofEpochMilli(time)));
	}

	@NotNull
	public static String formatDuration(long duration)
	{
		return JSON_DURATION_FORMAT.formatted(duration / 3600000, (duration % 3600000) / 60000, (duration % 60000) / 1000);
	}

	public static long parseDuration(@NotNull String duration)
	{
		long duration1 = 0;
		String[] parts = duration.split(":", 3);
		duration1 = ((Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 60 + Long.parseLong(parts[2])) * 1000;
		return duration1;
	}
}