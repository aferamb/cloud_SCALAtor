import scala.annotation.tailrec

object Phase02 {
  // Ejecuta la fase de retrasos de llegada usando ARR_DELAY y TAIL_NUM.
  def run(dataset: Dataset, itemLimit: Int): Option[PhaseResult] = {
    println()
    println("Fase 02 - ARR_DELAY + TAIL_NUM")
    AppUtils.readSignedThreshold("Umbral (positivo=retraso, negativo=adelanto, X para volver): ") match {
      case Some(threshold) =>
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
            val rawText = s"- Id dataset #${flight.id}  Matricula: ${flight.tailNum}  $label: $delay min"
            println(rawText)
            printArrivalDelayMatches(tail, threshold, label, itemLimit, storeDelayItem(state, itemLimit, flight, label, delay, rawText))
          case _ =>
            printArrivalDelayMatches(tail, threshold, label, itemLimit, state)
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

  private final case class ItemState(totalItems: Int, sentItems: Int, items: List[CloudResultItem])
}
