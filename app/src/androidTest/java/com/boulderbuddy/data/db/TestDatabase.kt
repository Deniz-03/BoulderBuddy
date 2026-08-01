package com.boulderbuddy.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/**
 * Baut eine flüchtige In-Memory-[BoulderBuddyDatabase] für DAO-/Repository-Tests (Phase 7.6).
 * Daten leben nur im Prozess; jeder Test bekommt eine frische DB und schließt sie im Teardown.
 */
fun createInMemoryDatabase(): BoulderBuddyDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        BoulderBuddyDatabase::class.java,
    ).build()
