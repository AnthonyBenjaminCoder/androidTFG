package com.example.tfg2.ui.dashboard

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.tfg2.R
import com.example.tfg2.databinding.FragmentDashboardBinding
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    // lista para ejercicios
    private val originalExerciseList = mutableListOf<String>()
    private val exerciseList = mutableListOf<String>()
    // mapa para agrupar los registros de ejercicios por nombre
    private var exerciseDetailsMap = mutableMapOf<String, List<DocumentSnapshot>>()
    private lateinit var listAdapter: ArrayAdapter<String>

    // barChart para mostrar el grafico de ejercicios semanales
    private lateinit var barChart: BarChart

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barChart = binding.barChart

        listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, exerciseList)
        binding.listViewExercises.adapter = listAdapter

        // searchView para filtrar ejercicios
        binding.searchView.setOnClickListener { binding.searchView.isIconified = false }
        binding.searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterExercises(newText)
                return true
            }
        })

        loadExercises()
        loadChartData()

        // accion del dilogo para editar el registro
        binding.listViewExercises.setOnItemClickListener { _, _, position, _ ->
            val exerciseName = exerciseList[position]
            val records = exerciseDetailsMap[exerciseName]
            if (records != null && records.isNotEmpty()) {
                if (records.size == 1) {
                    showEditDialog(records[0])
                } else {
                    val sortedRecords = records.sortedBy { it.getTimestamp("timestamp")?.toDate() }
                    val items = sortedRecords.map { doc ->
                        doc.getTimestamp("timestamp")?.toDate()?.let { date ->
                            DateFormat.format("dd/MM/yyyy hh:mm", date).toString()
                        } ?: "Sin fecha"
                    }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle("Selecciona un registro para editar")
                        .setItems(items) { _, which ->
                            showEditDialog(sortedRecords[which])
                        }
                        .create().show()
                }
            } else {
                Toast.makeText(requireContext(), "No hay registros para $exerciseName", Toast.LENGTH_SHORT).show()
            }
        }

        //  eliminar los registros
        binding.listViewExercises.setOnItemLongClickListener { _, _, position, _ ->
            val exerciseName = exerciseList[position]
            AlertDialog.Builder(requireContext())
                .setTitle("Eliminar Registros")
                .setMessage("¿Deseas eliminar todos los registros de $exerciseName?")
                .setPositiveButton("Sí") { dialog, _ ->
                    eliminarRegistrosDeCategoria(exerciseName)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .create().show()
            true
        }

    }


    //cargar la lista de ejercicios y los agrupa por nombre.
    private fun loadExercises() {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        db.collection("userTracking")
            .document(userEmail)
            .collection("exercises")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val grouped = querySnapshot.documents.groupBy { it.getString("ejercicio") ?: "Sin nombre" }
                val sortedKeys = grouped.keys.sorted()
                exerciseList.clear()
                originalExerciseList.clear()
                for (key in sortedKeys) {
                    exerciseList.add(key)
                    originalExerciseList.add(key)
                }
                exerciseDetailsMap = grouped.toMutableMap()
                listAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.w("Dashboard", "Error al cargar ejercicios", e)
            }
    }


    //ejercicios segun el texto ingresado en el SearchView.
    private fun filterExercises(query: String?) {
        val filteredList = mutableListOf<String>()
        if (query.isNullOrEmpty()) {
            filteredList.addAll(originalExerciseList)
        } else {
            for (exercise in originalExerciseList) {
                if (exercise.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))) {
                    filteredList.add(exercise)
                }
            }
        }
        exerciseList.clear()
        exerciseList.addAll(filteredList)
        listAdapter.notifyDataSetChanged()
    }


    // cargar los datos en el grafico, agrupando los ejercicios por día de la semana.
    private fun loadChartData() {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        db.collection("userTracking")
            .document(userEmail)
            .collection("exercises")
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Inicializa el mapa con conteo cero para cada día de la semana
                val exerciseCountByDay = mutableMapOf<String, Int>()
                val daysOfWeek = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
                daysOfWeek.forEach { exerciseCountByDay[it] = 0 }
                for (doc in querySnapshot.documents) {
                    val timestamp = doc.getTimestamp("timestamp")
                    if (timestamp != null) {
                        val calendar = Calendar.getInstance()
                        calendar.time = timestamp.toDate()
                        val dayInt = calendar.get(Calendar.DAY_OF_WEEK)
                        val dayName = when (dayInt) {
                            Calendar.MONDAY -> "Lun"
                            Calendar.TUESDAY -> "Mar"
                            Calendar.WEDNESDAY -> "Mié"
                            Calendar.THURSDAY -> "Jue"
                            Calendar.FRIDAY -> "Vie"
                            Calendar.SATURDAY -> "Sáb"
                            Calendar.SUNDAY -> "Dom"
                            else -> "Otro"
                        }
                        exerciseCountByDay[dayName] = (exerciseCountByDay[dayName] ?: 0) + 1
                    }
                }
                updateChart(exerciseCountByDay)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al cargar gráfico: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    //actualiza el barChart usando los datos agrupados.
    private fun updateChart(data: Map<String, Int>) {
        val dayOrder = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        val entries = mutableListOf<BarEntry>()
        dayOrder.forEachIndexed { index, day ->
            val count = data[day] ?: 0
            entries.add(BarEntry(index.toFloat(), count.toFloat()))
        }
        val dataSet = BarDataSet(entries, "Ejercicios por día")
        val barData = BarData(dataSet)
        barChart.data = barData

        //  eje x para que muestre los nombres de los dias
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(dayOrder)
        xAxis.granularity = 1f

        barChart.invalidate()
    }



    //dialogo donde editar un registro
    private fun showEditDialog(doc: DocumentSnapshot) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_exercise_custom, null)
        val npTotalSeries = dialogView.findViewById<NumberPicker>(R.id.npTotalSeries)
        val btnGenerarSets = dialogView.findViewById<TextView>(R.id.btnGenerarSets)
        val containerSeries = dialogView.findViewById<ViewGroup>(R.id.containerSeries)
        val editTextNota = dialogView.findViewById<TextView>(R.id.editTextNota)

        val exerciseName = doc.getString("ejercicio") ?: "Sin nombre"
        val sets = doc.get("sets") as? List<Map<String, Any>> ?: emptyList()
        val nota = doc.getString("nota") ?: ""

        editTextNota.text = nota
        npTotalSeries.minValue = 1
        npTotalSeries.maxValue = 10
        val totalSeries = if (sets.isNotEmpty()) sets.size else 1
        npTotalSeries.value = totalSeries

        generateRows(totalSeries, containerSeries)

        btnGenerarSets.setOnClickListener {
            val total = npTotalSeries.value
            generateRows(total, containerSeries)
        }

        for (i in 0 until containerSeries.childCount) {
            if (i < sets.size) {
                val rowView = containerSeries.getChildAt(i)
                val npRepsDetail = rowView.findViewById<NumberPicker>(R.id.npRepsDetail)
                val editTextWeightDetail = rowView.findViewById<TextView>(R.id.editTextWeightDetail)
                val setData = sets[i]
                val reps = (setData["reps"] as? Number)?.toInt() ?: 10
                val peso = (setData["peso"] as? Number)?.toDouble() ?: 0.0
                npRepsDetail.value = reps
                editTextWeightDetail.text = peso.toString()
                rowView.findViewById<TextView>(R.id.tvSerieNumber).text = "Serie ${i + 1}:"
            }
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setTitle("Editar $exerciseName")
            .setView(dialogView)
            .setPositiveButton("Actualizar", null)
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .create()

        alertDialog.setOnShowListener {
            val updateButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            updateButton.setOnClickListener {
                var allValid = true
                for (i in 0 until containerSeries.childCount) {
                    val rowView = containerSeries.getChildAt(i)
                    val editTextWeightDetail = rowView.findViewById<TextView>(R.id.editTextWeightDetail)
                    val weightStr = editTextWeightDetail.text.toString()
                    if (weightStr.isEmpty() || weightStr.toDoubleOrNull() == null) {
                        allValid = false
                        Toast.makeText(requireContext(), "Completa correctamente el peso en cada serie.", Toast.LENGTH_SHORT).show()
                        break
                    }
                }
                if (allValid) {
                    val updatedSets = mutableListOf<Map<String, Any>>()
                    for (i in 0 until containerSeries.childCount) {
                        val rowView = containerSeries.getChildAt(i)
                        val npRepsDetail = rowView.findViewById<NumberPicker>(R.id.npRepsDetail)
                        val editTextWeightDetail = rowView.findViewById<TextView>(R.id.editTextWeightDetail)
                        val reps = npRepsDetail.value
                        val weight = editTextWeightDetail.text.toString().toDouble()
                        updatedSets.add(mapOf("reps" to reps, "peso" to weight))
                    }
                    val updatedNote = editTextNota.text.toString()
                    doc.reference.update(
                        mapOf(
                            "sets" to updatedSets,
                            "nota" to updatedNote,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(requireContext(), "Registro actualizado", Toast.LENGTH_SHORT).show()
                        loadExercises()
                        loadChartData()
                        alertDialog.dismiss()
                    }.addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Error al actualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        alertDialog.show()
    }


    //crea  filas de series usando el layout row_series_detail.
    private fun generateRows(total: Int, container: ViewGroup) {
        container.removeAllViews()
        for (i in 1..total) {
            val rowView = LayoutInflater.from(requireContext())
                .inflate(R.layout.row_series_detail, container, false)
            val npRepsDetail = rowView.findViewById<NumberPicker>(R.id.npRepsDetail)
            npRepsDetail.minValue = 1
            npRepsDetail.maxValue = 20
            npRepsDetail.value = 10
            rowView.findViewById<TextView>(R.id.tvSerieNumber).text = "Serie $i:"
            val btnCopySerie = rowView.findViewById<android.widget.ImageButton>(R.id.btnCopySerie)
            btnCopySerie.setOnClickListener {
                val repsValue = npRepsDetail.value
                val weightValue = rowView.findViewById<TextView>(R.id.editTextWeightDetail).text.toString()
                for (j in 0 until container.childCount) {
                    val otherRow = container.getChildAt(j)
                    if (otherRow != rowView) {
                        val npOtherReps = otherRow.findViewById<NumberPicker>(R.id.npRepsDetail)
                        val editOtherWeight = otherRow.findViewById<TextView>(R.id.editTextWeightDetail)
                        npOtherReps.value = repsValue
                        editOtherWeight.text = weightValue
                    }
                }
                Toast.makeText(requireContext(), "Valores copiados a todas las series", Toast.LENGTH_SHORT).show()
            }
            val btnDeleteSerie = rowView.findViewById<android.widget.ImageButton>(R.id.btnDeleteSerie)
            btnDeleteSerie.setOnClickListener { container.removeView(rowView) }
            container.addView(rowView)
        }
    }




    //elimina todos los registros de un ejercicio en Firestore.
    private fun eliminarRegistrosDeCategoria(exerciseName: String) {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        val records = exerciseDetailsMap[exerciseName] ?: return
        val batch = db.batch()
        for (doc in records) {
            val docRef = db.collection("userTracking")
                .document(userEmail)
                .collection("exercises")
                .document(doc.id)
            batch.delete(docRef)
        }
        batch.commit().addOnSuccessListener {
            Toast.makeText(requireContext(), "Registros eliminados", Toast.LENGTH_SHORT).show()
            loadExercises()
            loadChartData()
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
