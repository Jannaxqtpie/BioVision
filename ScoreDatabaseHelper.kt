import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class ScoreDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "UserScores.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "scores"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_SCORE = "score"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT,
                $COLUMN_SCORE TEXT,
                $COLUMN_TIMESTAMP TEXT
            )
        """
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_USERNAME TEXT DEFAULT 'Unknown'")
        }
    }

    fun insertScore(username: String, score: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, username)
            put(COLUMN_SCORE, score)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis().toString())
        }
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun getAllScores(): List<String> {
        val scores = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC", null)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())

        while (cursor.moveToNext()) {
            val username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME))
            val score = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCORE))
            val timestampMillis = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)).toLong()
            val formattedDate = dateFormat.format(Date(timestampMillis))
            scores.add("$username - $score\n⏳ $formattedDate")
        }
        cursor.close()
        db.close()
        return scores
    }

    fun clearAllScores() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME")
        db.close()
    }
}
