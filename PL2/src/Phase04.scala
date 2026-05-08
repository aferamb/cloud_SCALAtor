import scala.annotation.tailrec

object Phase04 {
  private val MaxBarWidth = 40

  // Punto de entrada de la fase: elige origen/destino y umbral minimo de visualizacion.
  def run(dataset: Dataset): Option[PhaseResult] = {
    println()
    println("Fase 04 - Histograma de aeropuertos")
    println("1. Origen  2. Destino")

    AppUtils.readIntegerInRange("Tipo de aeropuerto: ", "Debe introducir un numero entre 1 y 2, o X.", 1, 2) match {
      case Some(airportOption) =>
        AppUtils.readIntegerInRange(
          "Umbral minimo (>= 0, X para volver): ",
          "Debe introducir un numero mayor o igual que 0, o X.",
          0,
          Int.MaxValue
        ) match {
          case Some(threshold) =>
            executeHistogram(dataset.flights, airportOption, threshold)
          case None => None
        }
      case None => None
    }
  }

  private def executeHistogram(
      flights: List[Flight],
      airportOption: Int,
      threshold: Int
  ): Option[PhaseResult] = {
    // Construye el histograma completo y despues aplica el umbral solo al imprimir.
    val airportLabel = if (airportOption == 1) "origen" else "destino"
    val histogram = buildHistogram(flights, airportOption, Nil, 0)

    histogram match {
      case HistogramBuildResult(Nil, _) =>
        println("No hay datos validos para la Fase 04.")
        None
      case HistogramBuildResult(entries, totalElements) =>
        val stats = computeShownStats(entries, threshold, HistogramStats(0, 0))
        println(s"$airportLabel | filas validas $totalElements | bins ${countEntries(entries, 0)}")
        val totalAirports = countEntries(entries, 0)
        printHistogram(airportLabel, threshold, entries, stats.maximumShownCount, totalAirports)
        Some(
          PhaseResult(
            "Fase 04 - Histograma de aeropuertos",
            s"tipo=$airportLabel; umbral=$threshold",
            s"Aeropuertos mostrados=${stats.shownAirports}; total=$totalAirports; filas validas=$totalElements"
          )
        )
    }
  }

  @tailrec
  private def buildHistogram(
      flights: List[Flight],
      airportOption: Int,
      histogram: List[AirportCount],
      totalElements: Int
  ): HistogramBuildResult = {
    // Recorre el dataset y actualiza una lista de pares con datos no modificables, evitando mapas mutables.
    flights match {
      case Nil => HistogramBuildResult(histogram, totalElements)
      case flight :: tail =>
        selectedAirport(flight, airportOption) match {
          case Some(airport) =>
            buildHistogram(tail, airportOption, incrementAirport(histogram, airport), totalElements + 1)
          case None =>
            buildHistogram(tail, airportOption, histogram, totalElements)
        }
    }
  }

  private def selectedAirport(flight: Flight, airportOption: Int): Option[AirportKey] = {
    // Selecciona las columnas ORIGIN_* o DEST_* segun la opcion del usuario.
    if (airportOption == 1) {
      validAirport(flight.originSeqId, flight.originAirport)
    } else {
      validAirport(flight.destinationSeqId, flight.destinationAirport)
    }
  }

  private def validAirport(seqId: Int, code: String): Option[AirportKey] = {
    if (seqId >= 0) Some(AirportKey(seqId, code)) else None
  }

  private def incrementAirport(entries: List[AirportCount], airport: AirportKey): List[AirportCount] = {
    // Actualiza el contador de un aeropuerto mediante una lista de pares, sin usar Map ni colecciones mutables.
    incrementAirportLoop(entries, airport, Nil)
  }

  @tailrec
  private def incrementAirportLoop(
      entries: List[AirportCount],
      airport: AirportKey,
      processed: List[AirportCount]
  ): List[AirportCount] = {
    // Busca el aeropuerto con recursividad de cola; `processed` guarda el prefijo ya revisado.
    entries match {
      case Nil =>
        prependProcessed(processed, AirportCount(airport, 1) :: Nil)
      case head :: tail =>
        if (head.airport.seqId == airport.seqId) {
          prependProcessed(processed, AirportCount(head.airport, head.count + 1) :: tail)
        } else {
          incrementAirportLoop(tail, airport, head :: processed)
        }
    }
  }

  @tailrec
  private def prependProcessed(processed: List[AirportCount], suffix: List[AirportCount]): List[AirportCount] = {
    // Reconstruye la lista final a partir del prefijo procesado, sin `++` ni `reverse`.
    processed match {
      case Nil          => suffix
      case head :: tail => prependProcessed(tail, head :: suffix)
    }
  }

  @tailrec
  private def computeShownStats(
      entries: List[AirportCount],
      threshold: Int,
      stats: HistogramStats
  ): HistogramStats = {
    // Calcula cuantos aeropuertos se mostraran y el maximo para escalar las barras.
    entries match {
      case Nil => stats
      case head :: tail =>
        if (head.count >= threshold) {
          val nextMaximum =
            if (head.count > stats.maximumShownCount) head.count else stats.maximumShownCount
          computeShownStats(tail, threshold, HistogramStats(stats.shownAirports + 1, nextMaximum))
        } else {
          computeShownStats(tail, threshold, stats)
        }
    }
  }

  private def printHistogram(
      airportLabel: String,
      threshold: Int,
      entries: List[AirportCount],
      maximumShownCount: Int,
      totalAirports: Int
  ): Unit = {
    // Mantiene un formato similar al de la PL1 CUDA para facilitar la comparacion.
    println()
    println(s"(4) Histograma de aeropuertos de $airportLabel")
    println(s"Num de aeropuertos encontrados: $totalAirports")
    println()
    printHistogramEntries(entries, threshold, maximumShownCount, totalAirports, 0)
  }

  @tailrec
  private def printHistogramEntries(
      entries: List[AirportCount],
      threshold: Int,
      maximumShownCount: Int,
      totalAirports: Int,
      shownAirports: Int
  ): Unit = {
    entries match {
      case Nil =>
        println()
        println(
          s"Aeropuertos mostrados (con al menos $threshold vuelos): $shownAirports " +
            s"(del total $totalAirports)"
        )
      case head :: tail =>
        if (head.count >= threshold) {
          println(
            s"${head.airport.code} (${head.airport.seqId}) | ${head.count} " +
              barForCount(head.count, maximumShownCount)
          )
          printHistogramEntries(tail, threshold, maximumShownCount, totalAirports, shownAirports + 1)
        } else {
          printHistogramEntries(tail, threshold, maximumShownCount, totalAirports, shownAirports)
        }
    }
  }

  @tailrec
  private def countEntries(entries: List[AirportCount], acc: Int): Int = {
    entries match {
      case Nil       => acc
      case _ :: tail => countEntries(tail, acc + 1)
    }
  }

  private def barForCount(count: Int, maximumShownCount: Int): String = {
    // Escala la barra al aeropuerto con mas vuelos entre los que superan el umbral.
    val rawLength =
      if (maximumShownCount > 0) (count.toLong * MaxBarWidth.toLong / maximumShownCount.toLong).toInt else 0
    val barLength = if (rawLength <= 0 && count > 0) 1 else rawLength
    repeatChar('#', barLength)
  }

  private def repeatChar(char: Char, times: Int): String = {
    if (times <= 0) "" else char.toString + repeatChar(char, times - 1)
  }

  private final case class AirportKey(seqId: Int, code: String)
  private final case class AirportCount(airport: AirportKey, count: Int)
  private final case class HistogramBuildResult(entries: List[AirportCount], totalElements: Int)
  private final case class HistogramStats(shownAirports: Int, maximumShownCount: Int)
}
