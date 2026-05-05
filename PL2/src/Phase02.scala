import scala.annotation.tailrec

object Phase02 {
  // Ejecuta la fase de retrasos de llegada usando ARR_DELAY y TAIL_NUM.
  def run(dataset: Dataset): Option[PhaseResult] = {
    println()
    println("Fase 02 - ARR_DELAY + TAIL_NUM")
    AppUtils.readSignedThreshold("Umbral (positivo=retraso, negativo=adelanto, X para volver): ") match {
      case Some(threshold) =>
        val label = if (threshold >= 0) "Retraso (llegada)" else "Adelanto (llegada)"
        val matches = printArrivalDelayMatches(dataset.flights, threshold, label, 0)
        val summary = s"Coincidencias encontradas: $matches"
        println()
        println("Resultados CPU:")
        println(s"Se han encontrado $matches aviones")
        println(summary)
        Some(PhaseResult("Fase 02 - Retraso en llegada", s"umbral=$threshold", summary))
      case None => None
    }
  }

  @tailrec
  private def printArrivalDelayMatches(
      flights: List[Flight],
      threshold: Int,
      label: String,
      matches: Int
  ): Int = {
    // La reserva atomica de CUDA se sustituye por el acumulador `matches`.
    flights match {
      case Nil => matches
      case flight :: tail =>
        flight.arrivalDelay match {
          case Some(delay) if AppUtils.matchesSignedThreshold(delay, threshold) =>
            println(s"- Id dataset #${flight.id}  Matricula: ${flight.tailNum}  $label: $delay min")
            printArrivalDelayMatches(tail, threshold, label, matches + 1)
          case _ =>
            printArrivalDelayMatches(tail, threshold, label, matches)
        }
    }
  }
}
