package com.example.tfg2.ui.exercisedetail

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tfg2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class ExerciseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID"
        const val EXTRA_EXPLANATION = "EXTRA_EXPLANATION"
        const val EXTRA_EXERCISE_NAME = "EXTRA_EXERCISE_NAME"
    }

    private lateinit var youTubePlayerView: YouTubePlayerView
    private lateinit var tvExplanation: TextView

    //para registrar el ejercicio
    private lateinit var npTotalSeries: NumberPicker
    private lateinit var btnGenerarSeries: Button
    private lateinit var containerSeries: LinearLayout
    private lateinit var editTextNota: EditText
    private lateinit var btnGuardarEjercicio: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_detail)

        //boton para regresar
        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        youTubePlayerView = findViewById(R.id.youtube_player_view)
        tvExplanation = findViewById(R.id.tvExplanation)
        npTotalSeries = findViewById(R.id.npTotalSeries)
        btnGenerarSeries = findViewById(R.id.btnGenerarSeries)
        containerSeries = findViewById(R.id.containerSeries)
        editTextNota = findViewById(R.id.editTextNota)
        btnGuardarEjercicio = findViewById(R.id.btnGuardarEjercicio)

        lifecycle.addObserver(youTubePlayerView)

        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: run {
            Toast.makeText(this, "No se ha proporcionado video ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val explanation = intent.getStringExtra(EXTRA_EXPLANATION) ?: "Explicación no disponible."
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Ejercicio"

        tvExplanation.text = explanation

        // YouTubePlayer para reproducir el video
        youTubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.cueVideo(videoId, 0f)
            }
        })

        // configuracion del numberPicker para series
        npTotalSeries.minValue = 1
        npTotalSeries.maxValue = 10
        npTotalSeries.value = 1

        btnGenerarSeries.setOnClickListener {
            containerSeries.removeAllViews()
            val totalSeries = npTotalSeries.value
            for (i in 1..totalSeries) {
                val rowView = layoutInflater.inflate(R.layout.row_series_detail, containerSeries, false)
                val tvSerieNumber = rowView.findViewById<TextView>(R.id.tvSerieNumber)
                tvSerieNumber.text = "Serie $i:"
                //NumberPicker para repeticiones
                val npRepsDetail = rowView.findViewById<NumberPicker>(R.id.npRepsDetail)
                npRepsDetail.minValue = 1
                npRepsDetail.maxValue = 20
                npRepsDetail.value = 10

                // boton para copiar valores a otras series
                val btnCopySerie = rowView.findViewById<ImageButton>(R.id.btnCopySerie)
                btnCopySerie.setOnClickListener {
                    val repsValue = npRepsDetail.value
                    val editTextWeightDetail = rowView.findViewById<EditText>(R.id.editTextWeightDetail)
                    val weightValue = editTextWeightDetail.text.toString()
                    for (j in 0 until containerSeries.childCount) {
                        val otherRow = containerSeries.getChildAt(j)
                        if (otherRow != rowView) {
                            val npOtherReps = otherRow.findViewById<NumberPicker>(R.id.npRepsDetail)
                            val editOtherWeight = otherRow.findViewById<EditText>(R.id.editTextWeightDetail)
                            npOtherReps.value = repsValue
                            editOtherWeight.setText(weightValue)
                        }
                    }
                    Toast.makeText(this, "Valores copiados a todas las series", Toast.LENGTH_SHORT).show()
                }

                // boton para borrar la serie
                val btnDeleteSerie = rowView.findViewById<ImageButton>(R.id.btnDeleteSerie)
                btnDeleteSerie.setOnClickListener {
                    containerSeries.removeView(rowView)
                }

                containerSeries.addView(rowView)
            }
        }

        btnGuardarEjercicio.setOnClickListener {
            var allValid = true
            val setsList = mutableListOf<Map<String, Any>>()

            for (i in 0 until containerSeries.childCount) {
                val rowView = containerSeries.getChildAt(i)
                val npRepsDetail = rowView.findViewById<NumberPicker>(R.id.npRepsDetail)
                val editTextWeightDetail = rowView.findViewById<EditText>(R.id.editTextWeightDetail)
                val weightStr = editTextWeightDetail.text.toString()
                if (weightStr.isEmpty() || weightStr.toDoubleOrNull() == null) {
                    allValid = false
                    Toast.makeText(this, "Completa correctamente el peso en cada serie.", Toast.LENGTH_SHORT).show()
                    break
                } else {
                    val reps = npRepsDetail.value
                    val weight = weightStr.toDouble()
                    setsList.add(mapOf("reps" to reps, "peso" to weight))
                }
            }

            if (allValid) {
                val nota = editTextNota.text.toString()
                guardarEjercicioFirebaseAdvanced(exerciseName, setsList, nota)
                Toast.makeText(this, "Ejercicio guardado con éxito", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun guardarEjercicioFirebaseAdvanced(
        nombreEjercicio: String,
        sets: List<Map<String, Any>>,
        nota: String
    ) {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val ejercicioData = hashMapOf(
            "ejercicio" to nombreEjercicio,
            "sets" to sets,
            "nota" to nota,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("userTracking")
            .document(userEmail)
            .collection("exercises")
            .add(ejercicioData)
            .addOnSuccessListener {
                Toast.makeText(this, "$nombreEjercicio registrado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al registrar ejercicio", Toast.LENGTH_SHORT).show()
            }
    }
}
