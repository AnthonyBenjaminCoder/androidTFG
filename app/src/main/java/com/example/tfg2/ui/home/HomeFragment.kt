package com.example.tfg2.ui.home

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.tfg2.R
import com.example.tfg2.databinding.FragmentHomeBinding
import com.example.tfg2.ui.chat.ChatActivity
import com.example.tfg2.ui.exercisedetail.ExerciseDetailActivity
import com.example.tfg2.workers.InactivityReminderWorker
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        ViewModelProvider(this).get(HomeViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //programar el OneTimeWorkRequestBuilder cada 5 segundos
        val oneTimeRequest = OneTimeWorkRequestBuilder<InactivityReminderWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(requireContext()).enqueue(oneTimeRequest)

        //programar el workRequest para que se ejecute cada 7 días
        val workRequest = PeriodicWorkRequestBuilder<InactivityReminderWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "inactivityReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        //boton de chat (ia)
        binding.btnIA.setOnClickListener {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            startActivity(intent)
        }

        //searchView
        binding.searchView.isIconified = true
        binding.searchView.setOnClickListener { binding.searchView.isIconified = false }
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                filterExercises(newText)
                return true
            }
        })

        //info de cada tarjeta
        binding.cardPressBanca.setOnClickListener {
            val videoId = "fqsTgdTPRQU"
            val explanation =
                "Para realizar correctamente el press de banca, acuéstate en el banco con la espalda bien apoyada, mantén los pies firmes en el suelo, baja la barra de forma controlada hasta el pecho y empuja con firmeza hacia arriba."
            val intent = Intent(requireContext(), ExerciseDetailActivity::class.java)
            intent.putExtra(ExerciseDetailActivity.EXTRA_VIDEO_ID, videoId)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXPLANATION, explanation)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, "Press de banca")
            startActivity(intent)
        }

        binding.cardJalonAlPecho.setOnClickListener {
            val videoId = "x2Y6Mb41zjY"
            val explanation =
                "Para realizar el jalón al pecho, siéntate con la espalda recta, usa un agarre amplio, baja la barra de manera controlada hasta acercarla a tu pecho y vuelve a la posición inicial sin balancear el cuerpo."
            val intent = Intent(requireContext(), ExerciseDetailActivity::class.java)
            intent.putExtra(ExerciseDetailActivity.EXTRA_VIDEO_ID, videoId)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXPLANATION, explanation)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, "Jalón al pecho")
            startActivity(intent)
        }

        binding.cardPressMilitarMancuernas.setOnClickListener {
            val videoId = "aTVwn3QgcVk"
            val explanation =
                "Para realizar el press militar con mancuernas, siéntate o párate con la espalda recta, eleva las mancuernas a la altura de los hombros y empuja hacia arriba extendiendo los brazos de forma controlada."
            val intent = Intent(requireContext(), ExerciseDetailActivity::class.java)
            intent.putExtra(ExerciseDetailActivity.EXTRA_VIDEO_ID, videoId)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXPLANATION, explanation)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, "Press militar con mancuernas")
            startActivity(intent)
        }

        binding.cardSentadillaLibre.setOnClickListener {
            val videoId = "dsCuiccYNGs"
            val explanation =
                "Para realizar la sentadilla libre, coloca la barra sobre la parte superior de la espalda, separa tus pies al ancho de los hombros, baja controladamente manteniendo la espalda recta y sube empujando desde los talones."
            val intent = Intent(requireContext(), ExerciseDetailActivity::class.java)
            intent.putExtra(ExerciseDetailActivity.EXTRA_VIDEO_ID, videoId)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXPLANATION, explanation)
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, "Sentadilla libre")
            startActivity(intent)
        }
    }


    //filtro de las tarjetas  en el grid en funcion de lo que se busca.
    private fun filterExercises(query: String?) {
        if (query.isNullOrEmpty()) {
            for (i in 0 until binding.gridExercises.childCount) {
                binding.gridExercises.getChildAt(i).visibility = View.VISIBLE
            }
        } else {
            for (i in 0 until binding.gridExercises.childCount) {
                val card = binding.gridExercises.getChildAt(i)
                //obtiene el textView con el titulo del ejercicio, segun el id de la tarjeta.
                val textView: TextView? = when (card.id) {
                    R.id.cardPressBanca -> card.findViewById(R.id.tvPressBanca)
                    R.id.cardJalonAlPecho -> card.findViewById(R.id.tvJalonAlPecho)
                    R.id.cardPressMilitarMancuernas -> card.findViewById(R.id.tvPressMilitarMancuernas)
                    R.id.cardSentadillaLibre -> card.findViewById(R.id.tvSentadillaLibre)
                    else -> null
                }
                if (textView != null) {
                    val cardText = textView.text.toString().lowercase(Locale.getDefault())
                    if (cardText.contains(query.lowercase(Locale.getDefault()))) {
                        card.visibility = View.VISIBLE
                    } else {
                        card.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

