package com.armsx2.exkinet.data

import com.armsx2.GameInfo

/** Contenido inicial del diseño entregado (prototipo ExKinet), solo como punto de partida editable. */
object ExkDefaults {
    private val rogueGalaxy = setOf("SCUS-97490", "SCES-54552", "SCPS-15102", "SCPS-17013", "SCPS-19254", "SCAJ-20162", "SCKA-30005", "SCUS-97572")

    /**
     * Fondo de ejemplo incluido en la APK. El fondo del prototipo (hero.webp) es arte derivado de
     * un juego comercial y este repositorio es público, así que NO se incluye: cada usuario elige
     * su fondo en «Editar ficha». Se deja el gancho por si se añade arte propio con licencia.
     */
    fun backgroundAsset(@Suppress("UNUSED_PARAMETER") game: GameInfo): String? = null

    fun description(game: GameInfo): String? = when {
        game.serial?.uppercase() in rogueGalaxy ->
            "El universo es demasiado grande para quedarse en un solo planeta. Únete a Jaster y comienza tu viaje entre las estrellas."
        else -> null
    }
}
