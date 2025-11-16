import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.utilities.Score
import com.surendramaran.yolov8tflite.R
import com.surendramaran.yolov8tflite.UserScore

import java.text.SimpleDateFormat
import java.util.*

class ScoreAdapter(private val scores: List<UserScore>) : RecyclerView.Adapter<ScoreAdapter.ScoreViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_item_score, parent, false)
        return ScoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        val score = scores[position]
        holder.bind(score)
    }

    override fun getItemCount(): Int = scores.size

    class ScoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.txtUserName)
        private val scoreText: TextView = itemView.findViewById(R.id.txtUserScore)
        private val dateText: TextView = itemView.findViewById(R.id.txtScoreDate)

        fun bind(score: UserScore) {
            nameText.text = score.name
            scoreText.text = score.score
            dateText.text = formatDate(score.timestamp)
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}
