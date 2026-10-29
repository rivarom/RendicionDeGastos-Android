package com.invap.rendiciondegastos

// import android.content.Intent // Eliminado
import android.os.Bundle
// import android.widget.Toast // Eliminado
import androidx.appcompat.app.AppCompatActivity
// import androidx.core.view.ViewCompat // Eliminado
// import androidx.core.view.WindowCompat // Eliminado
// import androidx.core.view.WindowInsetsCompat // Eliminado
// import com.google.firebase.auth.FirebaseAuth // Eliminado
// import com.google.firebase.auth.ktx.auth // Eliminado
// import com.google.firebase.ktx.Firebase // Eliminado
import com.invap.rendiciondegastos.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    // private lateinit var auth: FirebaseAuth // Eliminado


    // Dentro de la clase LoginActivity

    public override fun onStart() {
        super.onStart()
        // Contenido eliminado, ya no es la actividad de inicio
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // auth = Firebase.auth // Eliminado

        // Todo el contenido de onCreate (listeners, etc.) ha sido eliminado
        // ya que esta Activity no se usará.
    }
}