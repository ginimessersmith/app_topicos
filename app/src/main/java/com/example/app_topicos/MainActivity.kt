package com.example.app_topicos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import android.os.Build

import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private val uuid = UUID.randomUUID().toString()
    private lateinit var recognizerIntent: Intent
    private var cliente: SessionsClient? = null
    private var sesion: SessionName? = null
    private lateinit var textToSpeech: TextToSpeech
    private val TAG = "MainActivity"
    private lateinit var sessionsClient: SessionsClient
    private lateinit var session: SessionName


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Iniciar el servicio de sacudida
        val shakeServiceIntent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(shakeServiceIntent) // Para Android 8.0 y superior
        } else {
            startService(shakeServiceIntent) // Para versiones anteriores
        }
        // Inicializa TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Inicializa el reconocimiento de voz
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        // Verifica los permisos
        checkAudioPermission()

        // Configura el botón
        val btnSpeak: Button = findViewById(R.id.btnSpeak)
        btnSpeak.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Inicia el reconocimiento de voz
                    startListening()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Detiene el reconocimiento
                    speechRecognizer.stopListening()
                }
            }
            true
        }
    }

    private fun initializeDialogflow() {
        try {
            // Cargar las credenciales desde api.json en res/raw
            val stream = resources.openRawResource(R.raw.api)
            val credentials = GoogleCredentials.fromStream(stream)
            val settings = SessionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            // Crear el cliente de sesión
            sessionsClient = SessionsClient.create(settings)
            session = SessionName.of("tu-project-id", UUID.randomUUID().toString())

            Log.d("Dialogflow", "Inicialización exitosa")
        } catch (e: Exception) {
            Log.e("Dialogflow", "Error al inicializar Dialogflow: ${e.message}")
        }
    }

    // Función para enviar un mensaje a Dialogflow y obtener una respuesta
    fun sendToDialogflow(text: String) {
        try {
            val textInput = TextInput.newBuilder().setText(text).setLanguageCode("es").build()
            val queryInput = QueryInput.newBuilder().setText(textInput).build()

            val response = sessionsClient.detectIntent(session, queryInput)
            val replyText = response.queryResult.fulfillmentText

            Log.d("Dialogflow", "Respuesta: $replyText")
        } catch (e: Exception) {
            Log.e("Dialogflow", "Error al enviar mensaje: ${e.message}")
        }
    }

    private fun startListening() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Habla ahora", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0) ?: ""
                Log.d(TAG, "Texto reconocido: $spokenText")
                sendToDialogflow(spokenText)
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Error en SpeechRecognizer: $error")
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        speechRecognizer.startListening(recognizerIntent)
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("es", "ES")
        } else {
            Log.e(TAG, "Error al inicializar TextToSpeech")
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
    }

    private fun iniciarAsistente() {
        try {
            // Archivo JSON de configuración de la cuenta de Dialogflow (Google Cloud Platform)
            val config = resources.openRawResource(R.raw.api)

            // Leemos las credenciales de la cuenta de Dialogflow (Google Cloud Platform)
            val credenciales = GoogleCredentials.fromStream(config)

            // Leemos el 'projectId' el cual se encuentra en el archivo 'credenciales.json'
            val projectId = (credenciales as ServiceAccountCredentials).projectId

            // Construimos una configuración para acceder al servicio de Dialogflow (Google Cloud Platform)
            val generarConfiguracion = SessionsSettings.newBuilder()

            // Configuramos las sesiones que usaremos en la aplicación
            val configurarSesiones =
                generarConfiguracion.setCredentialsProvider(FixedCredentialsProvider.create(credenciales)).build()
            cliente = SessionsClient.create(configurarSesiones)
            sesion = SessionName.of(projectId, uuid)

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun iniciarAsistenteVoz() {

        textToSpeech = TextToSpeech(applicationContext,object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status != TextToSpeech.ERROR){
                    textToSpeech?.language=Locale("es")
                }
            }

        })

    }

//    private fun enviarMensaje(view: View) {
//
//        // Obtenemos el mensaje de la caja de texto y lo pasamos a String
//        val mensaje = cajadetexto.text.toString()
//
//        // Si el usuario no ha escrito un mensaje en la caja de texto y presiona el botón enviar, le mostramos
//        // un Toast con un mensaje 'Ingresa tu mensaje ...'
//        if (mensaje.trim { it <= ' ' }.isEmpty()) {
//            Toast.makeText(this@MainActivity, getString(R.string.placeholder), Toast.LENGTH_LONG).show()
//        }
//
//        // Si el usuario agrego un mensaje a la caja de texto, llamamos al método agregarTexto()
//        else {
//            agregarTexto(mensaje, USUARIO)
//            cajadetexto.setText("")
//
//            // Enviamos la consulta del usuario al Bot
//            val ingresarConsulta =
//                QueryInput.newBuilder().setText(TextInput.newBuilder().setText(mensaje).setLanguageCode("es")).build()
//            solicitarTarea(this@MainActivity, sesion!!, cliente!!, ingresarConsulta).execute()
//        }
//    }

//    private fun enviarMensajeMicrofono(view:View){
//
//        // Llamamos al intento para reconocer voz del usuario y convertirla a texto
//        val intento = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
//
//        // Definimos los modelos de reconocimiento de voz
//        intento.putExtra(
//            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
//            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
//        )
//
//        // Le decimos que haga el reconocimiento de voz en el idioma local 'Locale.getDefault()'
//        intento.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
//
//        // Si el usuario no habla algo, le mostramos el mensaje 'Di algo en el micrófono ...'
//        intento.putExtra(
//            RecognizerIntent.EXTRA_PROMPT,
//            getString(R.string.mensajedevoz)
//        )
//
//        // Si todo va bien, enviamos el audio del usuario al Bot
//        try {
//            startActivityForResult(intento, ENTRADA_DE_VOZ)
//        }
//
//        // Si el dispositivo del usuario no es compatible con la función del micrófono
//        // Le mostramos el mensaje 'Tu teléfono no es compatible con la función de micrófono ...'
//        // en un Toast
//        catch (a: ActivityNotFoundException) {
//            Toast.makeText(
//                applicationContext,
//                getString(R.string.mensajedevoznoadmitido),
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//
//    }
}
