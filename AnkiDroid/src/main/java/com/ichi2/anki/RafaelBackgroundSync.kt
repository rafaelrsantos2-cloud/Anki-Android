// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.utils.NetworkUtils
import timber.log.Timber

/**
 * AnkiDroid Rafael: sincroniza em segundo plano sempre que o app sai da tela
 * (home, troca de app, tela bloqueada), para o PC encontrar a colecao atualizada.
 */
object RafaelBackgroundSync : DefaultLifecycleObserver {
    private var ultimoInicioMs = 0L
    private var saiuEmMs = 0L
    private var voltouEmMs = 0L

    /** tempo minimo fora do app para a revisao buscar as mudancas do PC ao voltar */
    private const val AUSENCIA_MINIMA_MS = 20_000L

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val agora = System.currentTimeMillis()
        voltouEmMs = if (saiuEmMs != 0L && agora - saiuEmMs >= AUSENCIA_MINIMA_MS) agora else 0L
        saiuEmMs = 0L
    }

    /**
     * Verdadeiro uma unica vez, logo depois de o app voltar de um periodo em segundo plano.
     * A tela de revisao usa isto para sincronizar ao voltar.
     */
    fun consumirRetorno(): Boolean {
        val voltou = voltouEmMs != 0L && System.currentTimeMillis() - voltouEmMs < 5_000L
        voltouEmMs = 0L
        return voltou
    }

    override fun onStop(owner: LifecycleOwner) {
        saiuEmMs = System.currentTimeMillis()
        try {
            val contexto = AnkiDroidApp.instance.applicationContext
            val agora = System.currentTimeMillis()
            val intervalo =
                PreferenceManager
                    .getDefaultSharedPreferences(contexto)
                    .getString("rafaelAutoSyncInterval", null)
            val motivo =
                when {
                    !Prefs.isAutoSyncEnabled -> "sincronizacao automatica desligada"
                    intervalo == "0" -> "intervalo = nao sincronizar"
                    !isLoggedIn() -> "sem login"
                    !NetworkUtils.isOnline -> "sem internet"
                    MeteredSyncPolicy.shouldBlock() -> "rede limitada"
                    agora - ultimoInicioMs < 60_000L -> "sincronizou ha menos de 1 min"
                    else -> null
                }
            if (motivo != null) {
                Timber.d("sync ao sair: ignorado (%s)", motivo)
                return
            }
            val auth = syncAuth() ?: return
            ultimoInicioMs = agora
            Timber.i("sync ao sair: iniciando em segundo plano")
            SyncWorker.start(contexto, auth, shouldFetchMedia())
        } catch (e: Exception) {
            Timber.w(e, "sync ao sair: falhou ao iniciar")
        }
    }
}