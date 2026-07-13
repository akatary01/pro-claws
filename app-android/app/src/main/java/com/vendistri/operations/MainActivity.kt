package com.vendistri.operations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vendistri.operations.app.VendistriApp

class MainActivity : ComponentActivity() {
    companion object {
        const val ActionOpenActiveWork = "com.vendistri.operations.OPEN_ACTIVE_WORK"
        const val ExtraStopId = "active_stop_id"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VendistriApp()
        }
    }
}
