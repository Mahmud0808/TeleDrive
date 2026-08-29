package com.drdisagree.teledrive.data.local.database

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

suspend fun <R> RoomDatabase.inImmediateTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
