package com.trucdecomptable.garanties

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.trucdecomptable.garanties.notif.Rappels
import com.trucdecomptable.garanties.ui.ApplicationGaranties
import com.trucdecomptable.garanties.ui.GarantiesViewModel
import com.trucdecomptable.garanties.util.Diag

class MainActivity : ComponentActivity() {

    private val vm: GarantiesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        Rappels.creerCanaux(this)
        Rappels.planifier(this)
        lireIntention(intent)
        setContent { ApplicationGaranties(vm) }
        Diag.record("app", "activité créée")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lireIntention(intent)
    }

    /** Une notification porte l'identifiant de l'appareil : elle ouvre sa fiche. */
    private fun lireIntention(intent: Intent?) {
        val appareilId = intent?.getLongExtra("appareil_id", 0L) ?: 0L
        if (appareilId > 0) {
            Diag.record("app", "ouverture demandée pour l'appareil #$appareilId")
            vm.ouvrirFiche(appareilId)
        }
    }
}
