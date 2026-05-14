import scala.annotation.tailrec

/**
 * Fase 01: busqueda de vuelos por retraso o adelanto en salida.
 *
 * Esta fase recorre la columna `DEP_DELAY` del dataset y muestra los vuelos que
 * cumplen el umbral introducido. Sustituye el recorrido paralelo de CUDA por
 * una recursion de cola sobre la lista de vuelos.
 */
object Phase01 {
  /**
   * Ejecuta la Fase 01 completa.
   *
   * Pide al usuario un umbral, decide si se interpretara como retraso o adelanto
   * y devuelve un `PhaseResult` con los items que se pueden enviar a Cloud.
   *
   * @param dataset dataset actualmente cargado.
   * @param itemLimit maximo de items detallados que se guardan para Cloud.
   * @return `Some(PhaseResult)` si se ejecuta, o `None` si el usuario cancela.
   */
  def run(dataset: Dataset, itemLimit: Int): Option[PhaseResult] = {
    println()
    println("Fase 01 - DEP_DELAY")
    AppUtils.readSignedThreshold("Umbral (positivo=retraso, negativo=adelanto, X para volver): ") match {
      case Some(threshold) =>
        // El signo del umbral determina la comparacion y la etiqueta mostrada.
        val label = if (threshold >= 0) "Retraso" else "Adelanto"
        val state = printDepartureDelayMatches(dataset.flights, threshold, label, itemLimit, ItemState(0, 0, Nil))
        val summary = s"Coincidencias encontradas: ${state.totalItems}"
        println(summary)
        Some(
          PhaseResult(
            "PHASE_01",
            "Fase 01 - Retraso en salida",
            s"""{"threshold":$threshold,"delayColumn":"DEP_DELAY"}""",
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
   * Recorre los vuelos e imprime los que cumplen el filtro de `DEP_DELAY`.
   *
   * La funcion recibe un `ItemState` acumulado para contar coincidencias totales
   * y guardar solo los primeros `itemLimit` items para Cloud.
   *
   * @param flights vuelos pendientes de revisar.
   * @param threshold umbral firmado introducido por el usuario.
   * @param label texto `Retraso` o `Adelanto` que se imprime y se guarda.
   * @param itemLimit limite de items detallados para Cloud.
   * @param state acumulador de conteo e items.
   * @return estado final con total de coincidencias e items guardados.
   */
  @tailrec
  private def printDepartureDelayMatches(
      flights: List[Flight],
      threshold: Int,
      label: String,
      itemLimit: Int,
      state: ItemState
  ): ItemState = {
    // Sustituye el kernel CUDA de la Fase 01 por un recorrido recursivo de cola.
    flights match {
      case Nil => state
      case flight :: tail =>
        flight.departureDelay match {
          case Some(delay) if AppUtils.matchesSignedThreshold(delay, threshold) =>
            // Si `DEP_DELAY` existe y cumple el umbral, se imprime y se registra.
            val rawText = s"- Id dataset #${flight.id}: $label de $delay minutos"
            println(rawText)
            printDepartureDelayMatches(tail, threshold, label, itemLimit, storeDelayItem(state, itemLimit, flight, label, delay, rawText))
          case _ =>
            // Los vuelos sin `DEP_DELAY` o que no cumplen el umbral se omiten.
            printDepartureDelayMatches(tail, threshold, label, itemLimit, state)
        }
    }
  }

  /**
   * Actualiza el acumulador de items de Fase 01.
   *
   * Siempre incrementa el contador total de coincidencias, pero solo inserta un
   * `CloudResultItem` si todavia no se ha alcanzado `itemLimit`.
   *
   * @param state acumulador previo.
   * @param itemLimit limite configurado para items enviados.
   * @param flight vuelo que ha cumplido el filtro.
   * @param label tipo de coincidencia (`Retraso` o `Adelanto`).
   * @param delay minutos encontrados en `DEP_DELAY`.
   * @param rawText linea exacta impresa por consola.
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
      // Se inserta por cabeza por eficiencia; el orden se restaurara al crear el PhaseResult.
      counted.copy(
        sentItems = counted.sentItems + 1,
        items = CloudResultItem(
          itemType = "delay_match",
          flightId = Some(flight.id),
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
   * Estado interno de acumulacion para Fase 01.
   *
   * @param totalItems coincidencias reales encontradas.
   * @param sentItems coincidencias guardadas para Cloud tras aplicar limite.
   * @param items lista de items acumulados en orden inverso.
   */
  private final case class ItemState(totalItems: Int, sentItems: Int, items: List[CloudResultItem])
}
