package com.personalfitnesscoach.data.room

import android.content.Context
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.store.RowStoreContract
import com.personalfitnesscoach.data.core.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The storage contract on the real Room schema (SQLite through the Android driver, under Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomRowStoreTest : RowStoreContract() {
    private val opened = ArrayList<PfcDatabase>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    override fun newStore(): RowStore = RoomRowStore(PfcDatabase.inMemory(context).also { opened += it })

    @After fun close() { opened.forEach { it.close() } }

    @Test fun `foreign keys are on for reader and writer connections of a file database`() = runBlocking {
        val name = "pfc-fk-${System.nanoTime()}.db"
        val db = PfcDatabase.open(context, name)
        val reader = db.useReaderConnection { c -> c.usePrepared("PRAGMA foreign_keys") { it.step(); it.getLong(0) } }
        val writer = db.useWriterConnection { c -> c.usePrepared("PRAGMA foreign_keys") { it.step(); it.getLong(0) } }
        assertEquals(1L, reader)
        assertEquals(1L, writer)
        db.close()
        context.deleteDatabase(name)
        Unit
    }

    @Test fun `a file database keeps its data across reopening`() = runBlocking {
        val name = "pfc-test-${System.nanoTime()}.db"
        val clock = FixedClock(0).also { it.setDay(Fixtures.MONDAY) }
        val db1 = PfcDatabase.open(context, name)
        val d1 = PfcData(RoomRowStore(db1), clock, "test")
        Fixtures.onboard(d1)
        val before = d1.store.snapshot()
        db1.close()
        val db2 = PfcDatabase.open(context, name).also { opened += it }
        val after = RoomRowStore(db2).snapshot()
        assertEquals(before, after)
        assertTrue(after.docs.any { it.type == Profile.type })
        context.deleteDatabase(name)
        Unit
    }
}
