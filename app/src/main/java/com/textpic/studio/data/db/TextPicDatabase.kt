package com.textpic.studio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, TemplateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TextPicDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun templateDao(): TemplateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_createdAt ON messages(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_status ON messages(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_pinned ON messages(pinned)")
            }
        }

        fun build(context: Context): TextPicDatabase =
            Room.databaseBuilder(context, TextPicDatabase::class.java, "textpic.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
