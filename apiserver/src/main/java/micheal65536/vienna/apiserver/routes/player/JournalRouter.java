package micheal65536.vienna.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Journal;

import java.util.HashMap;

public class JournalRouter extends Router
{
	public JournalRouter(@NotNull EarthDB earthDB)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/journal").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			Journal journalModel;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("journal", playerId, Journal.class)
						.execute(earthDB);
				journalModel = (Journal) results.get("journal").value();
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			HashMap<String, micheal65536.vienna.apiserver.types.journal.Journal.InventoryJournalEntry> inventoryJournal = new HashMap<>();
			journalModel.getItems().forEach((uuid, itemJournalEntry) -> inventoryJournal.put(uuid, new micheal65536.vienna.apiserver.types.journal.Journal.InventoryJournalEntry(
					TimeFormatter.formatTime(itemJournalEntry.firstSeen()),
					TimeFormatter.formatTime(itemJournalEntry.lastSeen()),
					itemJournalEntry.amountCollected()
			)));

			// TODO
			micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry[] activityLog = new micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry[0];

			return Response.okFromJson(new EarthApiResponse<>(new micheal65536.vienna.apiserver.types.journal.Journal(inventoryJournal, activityLog)), EarthApiResponse.class);
		});
	}
}