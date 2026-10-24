package app.tsunayomi.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/** Runs several DAO calls as one database transaction: all of them apply, or none. */
interface DatabaseTransactions {
    suspend fun <T> run(block: suspend () -> T): T
}

internal class RoomTransactions @Inject constructor(private val db: TsunayomiDatabase) : DatabaseTransactions {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
