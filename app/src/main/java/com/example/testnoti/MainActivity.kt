package com.example.testnoti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var pendingIntent: android.app.PendingIntent

    // ← Sæt dit centers koordinater + radius her
    private val gymLat = 55.6761
    private val gymLon = 12.5683
    private val gymRadiusMeters = 150f

    private val reqPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            maybeStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout())

        geofencingClient = LocationServices.getGeofencingClient(this)

        val intent = Intent(this, GeofenceReceiver::class.java)
        pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        findViewById<Button>(1002).setOnClickListener {
            val msg = findViewById<EditText>(1001).text.toString().ifBlank { "Husk at tjekke ind!" }
            getSharedPreferences("app", MODE_PRIVATE).edit().putString("custom_msg", msg).apply()
            maybeStart()
        }
    }

    private fun maybeStart() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            needed.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val toAsk = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toAsk.isNotEmpty()) {
            reqPermissions.launch(toAsk.toTypedArray())
            return
        }
        addOrReplaceGeofence()
    }

    private fun addOrReplaceGeofence() {
        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            val geofence = Geofence.Builder()
                .setRequestId("gym-geofence")
                .setCircularRegion(gymLat, gymLon, gymRadiusMeters)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setLoiteringDelay(0)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                val uri = Uri.fromParts("package", packageName, null)
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }

            geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener { toast("Geofence aktivt") }
                .addOnFailureListener { e -> toast("Kunne ikke starte: ${e.message}") }
        }
    }

    // Minimal UI: tekstfelt + knap
    private fun layout(): android.view.View {
        val dp = resources.displayMetrics.density
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        val edit = EditText(this).apply {
            id = 1001
            hint = "Skriv din besked (fx: Husk at scanne ind)"
        }
        val btn = Button(this).apply {
            id = 1002
            text = "Aktivér geofence"
        }
        root.addView(edit)
        root.addView(btn)
        return root
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
