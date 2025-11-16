import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class ScoreDatabaseHelper4(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "UserScores3.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "scores3"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_SCORE = "score"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT NOT NULL,
                $COLUMN_SCORE TEXT NOT NULL,
                $COLUMN_TIMESTAMP TEXT NOT NULL
            )
        """
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertScore(userName: String, score: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, userName)
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
