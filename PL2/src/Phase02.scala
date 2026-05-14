import scala.annotation.tailrec

/**
 * Fase 02: busqueda de vuelos por retraso o adelanto en llegada.
 *
 * Recorre `ARR_DELAY` y, para cada coincidencia, muestra tambien la matricula
 * `TAIL_NUM`. La estructura es parecida a Fase 01, pero el item Cloud incluye
 * informacion del avion.
 */
object Phase02 {
  /**
   * Ejecuta la Fase 02 completa.
   *
   * Pide un umbral firmado, filtra la columna `ARR_DELAY`, imprime resultados y
   * construye un `PhaseResult` preparado para la subida opcional.
   *
   * @param dataset dataset actualmente cargado.
   * @param itemLimit maximo de items detallados que se guardan para Cloud.
   * @return `Some(PhaseResult)` si hay ejecucion, o `None` si se cancela.
   */
  def run(dataset: Dataset, itemLimit: Int): Option[PhaseResult] = {
    println()
    println("Fase 02 - ARR_DELAY + TAIL_NUM")
    AppUtils.readSignedThreshold("Umbral (positivo=retraso, negativo=adelanto, X para volver): ") match {
      case Some(threshold) =>
        // La etiqueta diferencia retrasos de llegada frente a adelantos de llegada.
        val label = if (threshold >= 0) "Retraso (llegada)" else "Adelanto (llegada)"
        val state = printArrivalDelayMatches(dataset.flights, threshold, label, itemLimit, ItemState(0, 0, Nil))
        val summary = s"Coincidencias encontradas: ${state.totalItems}"
        println()
        println("Resultados CPU:")
        println(s"Se han encontrado ${state.totalItems} aviones")
        println(summary)
        Some(
          PhaseResult(
            "PHASE_02",
            "Fase 02 - Retraso en llegada",
            s"""{"threshold":$threshold,"delayColumn":"ARR_DELAY","includeTailNum":true}""",
            summary,
            AppUtils.restoreResultItemsOrder(state.items, Nil),
            state.totalItems,
            state.sentItems,
            state.totalItems > state.sentItems
          )
        )
      case None => None
    }
  }

  /**
   * Recorre los vuelos e imprime coincidencias sobre `ARR_DELAY`.
   *
   * La reserva atomica de la version CUDA se sustituye por un acumulador
   * inmutable (`ItemState`) que se pasa de llamada en llamada.
   *
   * @param flights vuelos pendientes de revisar.
   * @param threshold umbral firmado usado para comparar retraso/adelanto.
   * @param label etiqueta textual que acompana al resultado.
   * @param itemLimit limite de items Cloud.
   * @param state acumulador de conteos e items.
   * @return estado final tras revisar todos los vuelos.
   */
  @tailrec
  private def printArrivalDelayMatches(
      flights: List[Flight],
      threshold: Int,
      label: String,
      itemLimit: Int,
      state: ItemState
  ): ItemState = {
    // La reserva atomica de CUDA se sustituye por el acumulador `matches`.
    flights match {
      case Nil => state
      case flight :: tail =>
        flight.arrivalDelay match {
          case Some(delay) if AppUtils.matchesSignedThreshold(delay, threshold) =>
            // La Fase 02 anade la matricula para identificar el avion de la coincidencia.
            val rawText = s"- Id dataset #${flight.id}  Matricula: ${flight.tailNum}  $label: $delay min"
            println(rawText)
            printArrivalDelayMatches(tail, threshold, label, itemLimit, storeDelayItem(state, itemLimit, flight, label, delay, rawText))
          case _ =>
            // Si `ARR_DELAY` es None o no supera el umbral, no se actualiza el estado.
            printArrivalDelayMatches(tail, threshold, label, itemLimit, state)
        }
    }
  }

  /**
   * Actualiza el estado de items para una coincidencia de llegada.
   *
   * Cuenta todas las coincidencias, pero solo guarda detalles mientras no se
   * supere el limite de items configurado.
   *
   * @param state acumulador previo.
   * @param itemLimit limite de items que se enviaran.
   * @param flight vuelo que cumple el filtro.
   * @param label descripcion del tipo de retraso/adelanto.
   * @param delay minutos encontrados en `ARR_DELAY`.
   * @param rawText linea impresa por consola.
   * @return acumulador actualizado.
   */
  private def storeDelayItem(
      state: ItemState,
      itemLimit: Int,
      flight: Flight,
      label: String,
      delay: Int,
      rawText: String
  ): ItemState = {
    val counted = state.copy(totalItems = state.totalItems + 1)
    if (counted.sentItems < itemLimit) {
      // Se conserva `tailNum` porque esta fase lo muestra y la API puede consultarlo.
      counted.copy(
        sentItems = counted.sentItems + 1,
        items = CloudResultItem(
          itemType = "delay_match",
          flightId = Some(flight.id),
          tailNum = Some(flight.tailNum),
          delayKind = Some(label),
          delayMinutes = Some(delay),
          rawText = Some(rawText)
        ) :: counted.items
      )
    } else {
      counted
    }
  }

  /**
   * Estado interno de acumulacion para Fase 02.
   *
   * @param totalItems coincidencias reales encontradas.
   * @param sentItems coincidencias guardadas dentro del limite Cloud.
   * @param items items acumulados por cabeza, pendientes de restaurar orden.
   */
  private final case class ItemState(totalItems: Int, sentItems: Int, items: List[CloudResultItem])
}
