import scala.annotation.tailrec

/**
 * Fase 04: histograma textual de aeropuertos de origen o destino.
 *
 * Esta fase cuenta cuantas veces aparece cada aeropuerto, filtra los resultados
 * por un umbral minimo y dibuja una barra proporcional al aeropuerto con mas
 * vuelos mostrado. El histograma se implementa con listas inmutables de pares,
 * no con `Map` mutable.
 */
object Phase04 {
  private val MaxBarWidth = 40

  /**
   * Ejecuta la Fase 04 completa.
   *
   * Pide si el histograma debe usar aeropuertos de origen o destino y despues
   * pide el umbral minimo de vuelos para mostrar una barra.
   *
   * @param dataset dataset actualmente cargado.
   * @param itemLimit maximo de items detallados que se guardan para Cloud.
   * @return resultado de histograma o `None` si se cancela/no hay datos validos.
   */
  def run(dataset: Dataset, itemLimit: Int): Option[PhaseResult] = {
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
            executeHistogram(dataset.flights, airportOption, threshold, itemLimit)
          case None => None
        }
      case None => None
    }
  }

  /**
   * Construye, imprime y empaqueta el histograma.
   *
   * Primero cuenta todos los aeropuertos validos, despues calcula estadisticas
   * de los que superan el umbral y finalmente genera items Cloud con las mismas
   * barras que se imprimen por consola.
   *
   * @param flights vuelos sobre los que se construye el histograma.
   * @param airportOption 1 para origen, 2 para destino.
   * @param threshold minimo de vuelos para mostrar un aeropuerto.
   * @param itemLimit limite de items detallados para Cloud.
   * @return `PhaseResult` con barras de histograma, o `None` si no hay datos.
   */
  private def executeHistogram(
      flights: List[Flight],
      airportOption: Int,
      threshold: Int,
      itemLimit: Int
  ): Option[PhaseResult] = {
    val airportLabel = if (airportOption == 1) "origen" else "destino"
    val histogram = buildHistogram(flights, airportOption, Nil, 0)

    histogram match {
      case HistogramBuildResult(Nil, _) =>
        println("No hay datos validos para la Fase 04.")
        None
      case HistogramBuildResult(entries, totalElements) =>
        // El histograma completo conserva todos los aeropuertos; el umbral solo decide cuales se muestran.
        val stats = computeShownStats(entries, threshold, HistogramStats(0, 0))
        println(s"$airportLabel | filas validas $totalElements | bins ${countEntries(entries, 0)}")
        val totalAirports = countEntries(entries, 0)
        printHistogram(airportLabel, threshold, entries, stats.maximumShownCount, totalAirports)
        val itemState = buildHistogramItems(
          entries,
          threshold,
          stats.maximumShownCount,
          airportLabel,
          itemLimit,
          HistogramItemState(0, 0, Nil)
        )
        Some(
          PhaseResult(
            "PHASE_04",
            "Fase 04 - Histograma de aeropuertos",
            s"""{"airportType":"$airportLabel","threshold":$threshold}""",
            s"Aeropuertos mostrados=${stats.shownAirports}; total=$totalAirports; filas validas=$totalElements",
            AppUtils.restoreResultItemsOrder(itemState.items, Nil),
            itemState.totalItems,
            itemState.sentItems,
            itemState.totalItems > itemState.sentItems
          )
        )
    }
  }

  /**
   * Recorre los vuelos y acumula los conteos por aeropuerto.
   *
   * Para cada vuelo selecciona origen o destino, valida que tenga identificador
   * numerico y actualiza una lista inmutable de `AirportCount`.
   *
   * @param flights vuelos pendientes de procesar.
   * @param airportOption 1 para origen, 2 para destino.
   * @param histogram lista de conteos acumulados.
   * @param totalElements numero de vuelos con aeropuerto valido.
   * @return histograma final junto con el total de elementos validos.
   */
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
            // Si el aeropuerto es valido, se incrementa su contador o se crea un nuevo bin.
            buildHistogram(tail, airportOption, incrementAirport(histogram, airport), totalElements + 1)
          case None =>
            // Los aeropuertos sin identificador valido no participan en el histograma.
            buildHistogram(tail, airportOption, histogram, totalElements)
        }
    }
  }

  /**
   * Selecciona el aeropuerto de origen o destino de un vuelo.
   *
   * @param flight vuelo que se esta leyendo.
   * @param airportOption 1 para origen, cualquier otro valor esperado aqui es destino.
   * @return clave de aeropuerto valida o `None` si el identificador no es valido.
   */
  private def selectedAirport(flight: Flight, airportOption: Int): Option[AirportKey] = {
    // Selecciona las columnas ORIGIN_* o DEST_* segun la opcion del usuario.
    if (airportOption == 1) {
      validAirport(flight.originSeqId, flight.originAirport)
    } else {
      validAirport(flight.destinationSeqId, flight.destinationAirport)
    }
  }

  /**
   * Valida y empaqueta una clave de aeropuerto.
   *
   * @param seqId identificador numerico del aeropuerto.
   * @param code codigo textual del aeropuerto.
   * @return `Some(AirportKey)` si `seqId` es no negativo, o `None` si no.
   */
  private def validAirport(seqId: Int, code: String): Option[AirportKey] = {
    if (seqId >= 0) Some(AirportKey(seqId, code)) else None
  }

  /**
   * Incrementa el contador de un aeropuerto dentro del histograma.
   *
   * @param entries histograma actual como lista de pares aeropuerto/contador.
   * @param airport aeropuerto que acaba de aparecer en el dataset.
   * @return nuevo histograma con el contador actualizado.
   */
  private def incrementAirport(entries: List[AirportCount], airport: AirportKey): List[AirportCount] = {
    // Actualiza el contador de un aeropuerto mediante una lista de pares, sin usar Map ni colecciones mutables.
    incrementAirportLoop(entries, airport, Nil)
  }

  /**
   * Busca un aeropuerto en la lista e incrementa su contador.
   *
   * `processed` guarda el prefijo ya revisado en orden inverso. Cuando se
   * encuentra el aeropuerto, se reconstruye el prefijo delante del sufijo
   * actualizado. Si no se encuentra, se crea una entrada nueva con contador 1.
   *
   * @param entries parte del histograma que queda por revisar.
   * @param airport aeropuerto que se quiere incrementar.
   * @param processed entradas ya visitadas, acumuladas por cabeza.
   * @return histograma actualizado.
   */
  @tailrec
  private def incrementAirportLoop(
      entries: List[AirportCount],
      airport: AirportKey,
      processed: List[AirportCount]
  ): List[AirportCount] = {
    // Busca el aeropuerto con recursividad de cola; `processed` guarda el prefijo ya revisado.
    entries match {
      case Nil =>
        // No existia el aeropuerto: se anade al final logico reconstruyendo el prefijo.
        prependProcessed(processed, AirportCount(airport, 1) :: Nil)
      case head :: tail =>
        if (head.airport.seqId == airport.seqId) {
          // Encontrado: se incrementa el contador y se conserva el resto del histograma.
          prependProcessed(processed, AirportCount(head.airport, head.count + 1) :: tail)
        } else {
          incrementAirportLoop(tail, airport, head :: processed)
        }
    }
  }

  /**
   * Reconstruye el prefijo procesado delante de un sufijo.
   *
   * Esta funcion reemplaza el uso de `processed.reverse ++ suffix`. Como
   * `processed` esta acumulado al reves, mover cada cabeza a `suffix` restaura
   * el orden original del prefijo.
   *
   * @param processed prefijo acumulado en orden inverso.
   * @param suffix parte final ya actualizada.
   * @return lista completa con el prefijo restaurado.
   */
  @tailrec
  private def prependProcessed(processed: List[AirportCount], suffix: List[AirportCount]): List[AirportCount] = {
    // Reconstruye la lista final a partir del prefijo procesado con recursion propia (sin `++` ni `reverse`).
    processed match {
      case Nil          => suffix
      case head :: tail =>
        prependProcessed(tail, head :: suffix)
    }
  }

  /**
   * Calcula estadisticas de los aeropuertos que superan el umbral.
   *
   * Se necesita saber cuantos aeropuertos se mostraran y cual es el mayor
   * contador mostrado para poder escalar las barras del histograma.
   *
   * @param entries histograma completo.
   * @param threshold minimo de vuelos para mostrar un aeropuerto.
   * @param stats acumulador de aeropuertos visibles y maximo visible.
   * @return estadisticas finales para impresion y generacion de items.
   */
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
          // Solo los aeropuertos visibles actualizan el maximo usado para escalar barras.
          val nextMaximum =
            if (head.count > stats.maximumShownCount) head.count else stats.maximumShownCount
          computeShownStats(tail, threshold, HistogramStats(stats.shownAirports + 1, nextMaximum))
        } else {
          computeShownStats(tail, threshold, stats)
        }
    }
  }

  /**
   * Imprime la cabecera del histograma y delega la impresion de cada entrada.
   *
   * @param airportLabel texto `origen` o `destino`.
   * @param threshold minimo de vuelos aplicado.
   * @param entries histograma completo.
   * @param maximumShownCount mayor contador entre aeropuertos visibles.
   * @param totalAirports numero total de aeropuertos distintos encontrados.
   * @return no devuelve valor; escribe por consola.
   */
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

  /**
   * Imprime recursivamente las entradas visibles del histograma.
   *
   * Cada aeropuerto bajo el umbral se salta. Los visibles se imprimen con codigo,
   * identificador, conteo y barra escalada.
   *
   * @param entries entradas pendientes.
   * @param threshold minimo de vuelos para imprimir.
   * @param maximumShownCount valor maximo usado para escalar barras.
   * @param totalAirports numero total de aeropuertos distintos.
   * @param shownAirports contador de aeropuertos ya impresos.
   * @return no devuelve valor; imprime lineas de histograma.
   */
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
          // Al imprimir una entrada visible se incrementa el contador mostrado.
          printHistogramEntries(tail, threshold, maximumShownCount, totalAirports, shownAirports + 1)
        } else {
          printHistogramEntries(tail, threshold, maximumShownCount, totalAirports, shownAirports)
        }
    }
  }

  /**
   * Cuenta las entradas del histograma sin usar `length`.
   *
   * @param entries entradas pendientes de contar.
   * @param acc contador acumulado.
   * @return numero total de entradas.
   */
  @tailrec
  private def countEntries(entries: List[AirportCount], acc: Int): Int = {
    entries match {
      case Nil       => acc
      case _ :: tail => countEntries(tail, acc + 1)
    }
  }

  /**
   * Construye la barra textual proporcional para un contador.
   *
   * La barra maxima mide `MaxBarWidth`. El resto se escala con division entera:
   * `count * MaxBarWidth / maximumShownCount`. Si el resultado seria cero pero
   * el contador es positivo, se fuerza longitud 1 para que el aeropuerto visible
   * tenga alguna marca.
   * Escala la barra al aeropuerto con mas vuelos entre los que superan el umbral.
   *
   * @param count vuelos del aeropuerto.
   * @param maximumShownCount mayor contador visible del histograma.
   * @return texto formado por `#` con longitud escalada.
   */
  private def barForCount(count: Int, maximumShownCount: Int): String = {
    val rawLength =
      if (maximumShownCount > 0) (count.toLong * MaxBarWidth.toLong / maximumShownCount.toLong).toInt else 0
    val barLength = if (rawLength <= 0 && count > 0) 1 else rawLength
    repeatChar('#', barLength)
  }

  /**
   * Repite un caracter un numero de veces.
   *
   * @param char caracter que se quiere repetir.
   * @param times numero de repeticiones.
   * @return cadena con `char` repetido `times` veces, o vacia si `times <= 0`.
   */
  private def repeatChar(char: Char, times: Int): String = {
    if (times <= 0) "" else char.toString + repeatChar(char, times - 1)
  }

  /**
   * Genera items Cloud para los aeropuertos visibles del histograma.
   *
   * Recorre las mismas entradas que se imprimen por consola y crea un item por
   * aeropuerto que cumple el umbral, manteniendo el mismo texto de barra.
   *
   * @param entries histograma completo.
   * @param threshold minimo de vuelos para incluir item.
   * @param maximumShownCount maximo usado para escalar barras.
   * @param airportLabel texto `origen` o `destino`.
   * @param itemLimit limite de items detallados para Cloud.
   * @param state acumulador de conteos e items.
   * @return estado final con total visible y items guardados.
   */
  @tailrec
  private def buildHistogramItems(
      entries: List[AirportCount],
      threshold: Int,
      maximumShownCount: Int,
      airportLabel: String,
      itemLimit: Int,
      state: HistogramItemState
  ): HistogramItemState = {
    entries match {
      case Nil => state
      case head :: tail =>
        if (head.count >= threshold) {
          // Se recalcula la barra con la misma formula usada al imprimir.
          val barText = barForCount(head.count, maximumShownCount)
          val rawText = s"${head.airport.code} (${head.airport.seqId}) | ${head.count} $barText"
          val nextState = storeHistogramItem(state, itemLimit, head, airportLabel, barText, rawText)
          buildHistogramItems(tail, threshold, maximumShownCount, airportLabel, itemLimit, nextState)
        } else {
          buildHistogramItems(tail, threshold, maximumShownCount, airportLabel, itemLimit, state)
        }
    }
  }

  /**
   * Actualiza el acumulador de items de histograma.
   *
   * Cuenta todos los aeropuertos que superan el umbral, pero solo guarda el item
   * detallado si todavia hay espacio dentro de `itemLimit`.
   *
   * @param state estado previo.
   * @param itemLimit maximo de items que se enviaran.
   * @param airportCount entrada de histograma visible.
   * @param airportLabel tipo de aeropuerto (`origen` o `destino`).
   * @param barText barra textual calculada.
   * @param rawText linea equivalente a la salida de consola.
   * @return estado actualizado.
   */
  private def storeHistogramItem(
      state: HistogramItemState,
      itemLimit: Int,
      airportCount: AirportCount,
      airportLabel: String,
      barText: String,
      rawText: String
  ): HistogramItemState = {
    val counted = state.copy(totalItems = state.totalItems + 1)
    if (counted.sentItems < itemLimit) {
      // Los items se acumulan por cabeza; al final se restaura el orden en `executeHistogram`.
      counted.copy(
        sentItems = counted.sentItems + 1,
        items = CloudResultItem(
          itemType = "airport_histogram",
          airportCode = Some(airportCount.airport.code),
          airportSeqId = Some(airportCount.airport.seqId),
          airportKind = Some(airportLabel),
          airportCount = Some(airportCount.count),
          barText = Some(barText),
          rawText = Some(rawText)
        ) :: counted.items
      )
    } else {
      counted
    }
  }

  /**
   * Clave interna que identifica un aeropuerto.
   *
   * @param seqId identificador numerico usado para comparar aeropuertos.
   * @param code codigo textual mostrado por consola.
   */
  private final case class AirportKey(seqId: Int, code: String)

  /**
   * Entrada del histograma: aeropuerto y contador acumulado.
   *
   * @param airport aeropuerto contado.
   * @param count numero de vuelos asociados.
   */
  private final case class AirportCount(airport: AirportKey, count: Int)

  /**
   * Resultado de construir el histograma completo.
   *
   * @param entries lista de aeropuertos con sus conteos.
   * @param totalElements numero de vuelos con aeropuerto valido.
   */
  private final case class HistogramBuildResult(entries: List[AirportCount], totalElements: Int)

  /**
   * Estadisticas de las entradas que superan el umbral.
   *
   * @param shownAirports aeropuertos que se mostraran.
   * @param maximumShownCount mayor contador entre los mostrados.
   */
  private final case class HistogramStats(shownAirports: Int, maximumShownCount: Int)

  /**
   * Estado de acumulacion de items Cloud para el histograma.
   *
   * @param totalItems aeropuertos visibles antes de aplicar limite.
   * @param sentItems items guardados despues del limite.
   * @param items items acumulados por cabeza.
   */
  private final case class HistogramItemState(totalItems: Int, sentItems: Int, items: List[CloudResultItem])
}
