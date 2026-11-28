// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.lifecycleScope
import anki.decks.Deck
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AnkiDroid Rafael: abre pelo link do Caderno de Metas do site.
 *
 * ankirafael://filtro?busca=<busca do Anki>&nome=<baralho>&limite=<n>&ordem=<aleatorio|lapsos|adicionados|vencimento>
 *
 * Cria (ou refaz, se ja existir) o baralho filtrado com essa busca e abre a revisao dele.
 * So mexe em baralho filtrado: se ja houver um baralho normal com o mesmo nome, nao faz nada.
 */
class RafaelFiltroActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch { montarEAbrir(uri) }
    }

    private suspend fun montarEAbrir(uri: Uri) {
        val busca = uri.getQueryParameter("busca")?.trim().orEmpty()
        val nome = uri.getQueryParameter("nome")?.trim()?.takeIf { it.isNotEmpty() } ?: NOME_PADRAO
        val limite = uri.getQueryParameter("limite")?.toIntOrNull()?.coerceIn(1, 99_999) ?: 9_999
        val ordem =
            when (uri.getQueryParameter("ordem")) {
                "lapsos" -> 4
                "adicionados" -> 5
                "vencimento" -> 6
                else -> 1 // aleatoria
            }
        if (busca.isEmpty()) {
            aviso("O link n\u00e3o trouxe a busca do Anki")
            abrirListaDeBaralhos()
            return
        }
        Timber.i("filtro do site: nome=%s limite=%d ordem=%d busca=%s", nome, limite, ordem, busca)
        val resultado =
            try {
                withCol {
                    val existente = decks.idForName(nome)
                    if (existente != null && !decks.isFiltered(existente)) {
                        return@withCol Resultado.erro("J\u00e1 existe um baralho normal chamado \"$nome\"")
                    }
                    val base = sched.getOrCreateFilteredDeck(existente ?: 0L)
                    val termo =
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch(busca)
                            .setLimit(limite)
                            .setOrder(Deck.Filtered.SearchTerm.Order.forNumber(ordem))
                            .build()
                    val atualizado =
                        base
                            .toBuilder()
                            .setName(nome)
                            .setAllowEmpty(true)
                            .setConfig(
                                base.config
                                    .toBuilder()
                                    .clearSearchTerms()
                                    .addSearchTerms(termo)
                                    .setReschedule(true)
                                    .build(),
                            ).build()
                    val did = sched.addOrUpdateFilteredDeck(atualizado).id
                    decks.select(did)
                    Resultado(did, findCards("deck:\"${nome.replace("\"", "")}\"").size, null)
                }
            } catch (e: Exception) {
                Timber.w(e, "filtro do site: falhou")
                Resultado.erro("N\u00e3o consegui montar o filtro: ${e.message ?: e.javaClass.simpleName}")
            }
        if (resultado.erro != null) {
            aviso(resultado.erro)
            abrirListaDeBaralhos()
            return
        }
        if (resultado.cards == 0) {
            aviso("Nenhum card nesta busca agora")
            abrirListaDeBaralhos()
            return
        }
        aviso("$nome: ${resultado.cards} cards")
        val revisao =
            if (Prefs.isNewStudyScreenEnabled) {
                ReviewerFragment.getIntent(this)
            } else {
                Intent(this, Reviewer::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        TaskStackBuilder
            .create(applicationContext)
            .addNextIntent(Intent(this, DeckPicker::class.java))
            .addNextIntent(revisao)
            .startActivities()
        finish()
    }

    private fun abrirListaDeBaralhos() {
        startActivity(Intent(this, DeckPicker::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun aviso(texto: String) {
        Toast.makeText(applicationContext, texto, Toast.LENGTH_LONG).show()
    }

    private class Resultado(
        val did: Long,
        val cards: Int,
        val erro: String?,
    ) {
        companion object {
            fun erro(texto: String) = Resultado(0, 0, texto)
        }
    }

    companion object {
        const val NOME_PADRAO = "00.Meta do site"
    }
}