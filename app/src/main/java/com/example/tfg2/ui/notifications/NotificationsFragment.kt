package com.example.tfg2.ui.notifications

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.tfg2.R
import com.example.tfg2.databinding.FragmentNotificationsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val db = Firebase.firestore

    companion object {
        private const val PREF_NAME = "MyPREFERENCES"
        private const val COLLECTION_USERS = "usersRegister"
        private val BASE_BUTTON_COLOR = Color.parseColor("#03A9F4")
        private const val RC_SIGN_IN = 9001
    }

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root = binding.root

        sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        firebaseAuth = FirebaseAuth.getInstance()

        //configuracion del GoogleSignInOptions usando el id del cliente en strings.xml
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        val storedUser = sharedPreferences.getString("usuario", "")
        val storedPassword = sharedPreferences.getString("contrasena", "")

        with(binding) {
            buttonLogin.setOnClickListener { loginProcess() }
            buttonLogOut.setOnClickListener { logoutProcess() }
            buttonRegister.setOnClickListener { registerProcess() }

            //boton para restablecer contraseña
            buttonResetPassword.setOnClickListener {
                val email = emailText.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendPasswordReset(email)
                } else {
                    Toast.makeText(
                        context,
                        "Por favor, ingresa un correo válido",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            imageGoogleLoginButton.setOnClickListener { signInWithGoogle() }
        }

        //verificar si el usuario ya esta registrado
        checkUserLogged(storedUser)

        return root
    }

    //metodo para restablecer la contraseña
    private fun sendPasswordReset(email: String) {
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        context,
                        "Se ha enviado un correo para restablecer tu contraseña.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Error al enviar el correo: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    //registrar un usuario utilizando firebase autentication
    private fun registerProcess() {
        val email = binding.emailText.text.toString().trim()
        val password = binding.passwordText.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {

            binding.emailText.setBackgroundColor(Color.TRANSPARENT)
            binding.passwordText.setBackgroundColor(Color.TRANSPARENT)


            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Usuario registrado exitosamente",
                            Toast.LENGTH_SHORT
                        ).show()

                        val userData = hashMapOf("email" to email)
                        val uid = firebaseAuth.currentUser?.uid ?: email
                        db.collection(COLLECTION_USERS)
                            .document(email)
                            .set(userData)
                            .addOnSuccessListener {
                                Log.d("NotificationsFragment", "Datos adicionales guardados en Firestore")
                            }
                            .addOnFailureListener { e ->
                                Log.w("NotificationsFragment", "Error al guardar datos en Firestore", e)
                            }

                        binding.emailText.text.clear()
                        binding.passwordText.text.clear()
                    } else {
                        Toast.makeText(
                            context,
                            "Error al registrar usuario: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        } else {
            binding.emailText.setBackgroundColor(Color.RED)
            binding.passwordText.setBackgroundColor(Color.RED)
            Toast.makeText(context, "Indique un usuario y contraseña", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutProcess() {
        sharedPreferences.edit().clear().apply()

        binding.buttonLogOut.visibility = View.INVISIBLE
        binding.buttonLogin.apply {
            setBackgroundColor(BASE_BUTTON_COLOR)
            text = "Iniciar sesión"
        }
        binding.emailText.setText("")
        binding.passwordText.setText("")
        binding.buttonRegister.visibility = View.VISIBLE

        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }

    //logica del login que se pone en rojo si fallas
    private fun loginProcess() {
        val emailInput = binding.emailText.text.toString().trim()
        val passwordInput = binding.passwordText.text.toString().trim()

        if (emailInput.isEmpty() || passwordInput.isEmpty()) {
            binding.emailText.setBackgroundColor(Color.RED)
            binding.passwordText.setBackgroundColor(Color.RED)
            Toast.makeText(context, "Indique un usuario y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        binding.emailText.setBackgroundColor(Color.TRANSPARENT)
        binding.passwordText.setBackgroundColor(Color.TRANSPARENT)

        firebaseAuth.signInWithEmailAndPassword(emailInput, passwordInput)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    binding.emailText.setText(emailInput)
                    binding.passwordText.setText("")

                    sharedPreferences.edit().putString("usuario", emailInput).apply()

                    binding.buttonLogin.apply {
                        text = "Sesión iniciada"
                        setBackgroundColor(Color.GREEN)
                    }
                    binding.buttonLogOut.visibility = View.VISIBLE
                    binding.buttonRegister.visibility = View.INVISIBLE
                    binding.buttonLogOut.setTextColor(Color.BLACK)

                    Toast.makeText(context, "Sesión iniciada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error en la autenticación: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    //comprueba si el usuario esta logueado
    private fun checkUserLogged(storedUser: String?) {
        if (!storedUser.isNullOrEmpty()) {
            binding.emailText.setText("Hola, :3")
            binding.buttonLogin.apply {
                setBackgroundColor(Color.GREEN)
                text = "Sesión iniciada"
            }
            binding.buttonLogOut.visibility = View.VISIBLE
            binding.buttonLogOut.setTextColor(Color.BLACK)
            binding.passwordText.setText("")
            binding.emailText.setBackgroundColor(Color.TRANSPARENT)
            binding.passwordText.setBackgroundColor(Color.TRANSPARENT)
            binding.buttonRegister.visibility = View.INVISIBLE

            db.collection(COLLECTION_USERS)
                .get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val userDB = document.get("email")?.toString()
                        if (storedUser == userDB) {

                            return@addOnSuccessListener
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("NotificationsFragment", "Error al obtener documentos.", exception)
                }
        }
    }

    //inicicar sesion con google
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("NotificationsFragment", "Google sign in failed", e)
                Toast.makeText(context, "Error en el inicio de sesión con Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    Log.d("NotificationsFragment", "signInWithCredential:success")
                    val user = firebaseAuth.currentUser
                    Toast.makeText(context, "Sesión iniciada con Google", Toast.LENGTH_SHORT).show()
                    user?.email?.let { email ->
                        binding.emailText.setText(email)
                        binding.passwordText.setText("")
                        binding.buttonLogin.text = "Sesión iniciada"
                        binding.buttonLogin.setBackgroundColor(Color.GREEN)
                        binding.buttonLogOut.visibility = View.VISIBLE
                        binding.buttonRegister.visibility = View.INVISIBLE

                        sharedPreferences.edit().putString("usuario", email).apply()
                    }
                } else {
                    Log.w("NotificationsFragment", "signInWithCredential:failure", task.exception)
                    Toast.makeText(context, "Error al autenticar con Google", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
