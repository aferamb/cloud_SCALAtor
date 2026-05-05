import scala.annotation.tailrec

object Phase01 {
  // Ejecuta la fase de retrasos de salida usando DEP_DELAY.
  def run(dataset: Dataset): Option[PhaseResult] = {
    println()
    println("Fase 01 - DEP_DELAY")
    AppUtils.readSignedThreshold("Umbral (positivo=retraso, negativo=adelanto, X para volver): ") match {
      case Some(threshold) =>
        val label = if (threshold >= 0) "Retraso" else "Adelanto"
        val matches = printDepartureDelayMatches(dataset.flights, threshold, label, 0)
        val summary = s"Coincidencias encontradas: $matches"
        println(summary)
        Some(PhaseResult("Fase 01 - Retraso en salida", s"umbral=$threshold", summary))
      case None => None
    }
  }

  @tailrec
  private def printDepartureDelayMatches(
      flights: List[Flight],
      threshold: Int,
      label: String,
      matches: Int
  ): Int = {
    // Sustituye el kernel CUDA de la Fase 01 por un recorrido recursivo de cola.
    flights match {
      case Nil => matches
      case flight :: tail =>
        flight.departureDelay match {
          case Some(delay) if AppUtils.matchesSignedThreshold(delay, threshold) =>
            println(s"- Id dataset #${flight.id}: $label de $delay minutos")
            printDepartureDelayMatches(tail, threshold, label, matches + 1)
          case _ =>
            printDepartureDelayMatches(tail, threshold, label, matches)
        }
    }
  }
}
