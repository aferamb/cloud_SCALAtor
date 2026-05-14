import scala.annotation.tailrec

object Phase01 {
  // Ejecuta la fase de retrasos de salida usando DEP_DELAY.
  def run(dataset: Dataset, itemLimit: Int): Option[PhaseResult] = {
    println()
    println("Fase 01 - DEP_DELAY")
    AppUtils.readSignedThreshold("Umbral (positivo=retraso, negativo=adelanto, X para volver): ") match {
      case Some(threshold) =>
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
            val rawText = s"- Id dataset #${flight.id}: $label de $delay minutos"
            println(rawText)
            printDepartureDelayMatches(tail, threshold, label, itemLimit, storeDelayItem(state, itemLimit, flight, label, delay, rawText))
          case _ =>
            printDepartureDelayMatches(tail, threshold, label, itemLimit, state)
        }
    }
  }

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

  private final case class ItemState(totalItems: Int, sentItems: Int, items: List[CloudResultItem])
}
