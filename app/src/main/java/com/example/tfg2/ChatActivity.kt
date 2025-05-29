package com.example.tfg2.ui.chat

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg2.R
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

class ChatActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var btnSend: Button
    private lateinit var editMessage: EditText
    private lateinit var recycler: RecyclerView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: ChatAdapter

    private val apiKey = "AIzaSyCQiKqfI2E0wJp8is59gt-AX_oX_Oo9hTY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        //vistas del layout
        btnBack = findViewById(R.id.btnBack)
        btnSend = findViewById(R.id.btnSend)
        editMessage = findViewById(R.id.editMessage)
        recycler = findViewById(R.id.recyclerMessages)

        //boton para volver atras
        btnBack.setOnClickListener { finish() }

        //recyclerview con su adaptador
        adapter = ChatAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // al enviar mensaje, lo añadimos y consultamos la api
        btnSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                addMessage(Message(text, true))
                editMessage.text.clear()
                sendQuery(text)
            }
        }
    }

    // añade el mensaje
    private fun addMessage(message: Message) {
        messages.add(message)
        adapter.notifyDataSetChanged()
        recycler.scrollToPosition(messages.size - 1)
    }

    //envaa la consulta usando retrofit
    private fun sendQuery(userText: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(GeminiApi::class.java)

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(userText)),
                    role = "user"
                )
            )
        )

        api.generateContent(apiKey, request).enqueue(object : Callback<GeminiResponse> {
            override fun onResponse(call: Call<GeminiResponse>, response: Response<GeminiResponse>) {
                if (response.isSuccessful) {
                    //obtenemos la respuesta y añadimos el mensaje
                    val text = response.body()?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull()?.text ?: "sin respuesta"
                    addMessage(Message(text, false))
                } else {
                    Toast.makeText(this@ChatActivity, "error: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GeminiResponse>, t: Throwable) {
                Toast.makeText(this@ChatActivity, "fallo en la conexión: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // modelos de datos para la comunicarnos con la api de gemini
    data class Message(val text: String, val isUser: Boolean)
    data class GeminiRequest(val contents: List<Content>)
    data class Content(val parts: List<Part>, val role: String)
    data class Part(val text: String)
    data class GeminiResponse(val candidates: List<Candidate>)
    data class Candidate(val content: Content)

    interface GeminiApi {
        @POST("v1beta/models/gemini-1.5-flash:generateContent")
        fun generateContent(
            @Query("key") apiKey: String,
            @Body request: GeminiRequest
        ): Call<GeminiResponse>
    }

    //adaptador que muestra mensajes a la derecha o izquierda
    inner class ChatAdapter(private val messages: List<Message>) :
        RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(val container: FrameLayout, val textView: TextView) :
            RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {

            val container = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val tv = TextView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16,16,16,16)
            }
            container.addView(tv)
            return ChatViewHolder(container, tv)
        }

        override fun getItemCount() = messages.size

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val msg = messages[position]
            holder.textView.text = msg.text
            //colores de los mensajes
            holder.textView.setBackgroundColor(
                if (msg.isUser) 0xFFD1E7DD.toInt() else 0xFFF8D7DA.toInt()
            )
            //mensaje dercha o izquierda
            val lp = holder.textView.layoutParams as FrameLayout.LayoutParams
            lp.gravity = if (msg.isUser) Gravity.END else Gravity.START
            holder.textView.layoutParams = lp
        }
    }
}
