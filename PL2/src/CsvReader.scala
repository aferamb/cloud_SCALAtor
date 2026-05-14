import scala.annotation.tailrec
import scala.io.Source
import scala.util.Try

/**
 * Lector y parser del CSV de vuelos.
 *
 * Este archivo transforma el fichero original del dataset en un `Dataset`
 * inmutable. Solo extrae las columnas que usa la practica y mantiene
 * estadisticas de carga para informar al usuario de filas descartadas y valores
 * ausentes. El parseo evita bucles de colecciones y usa recursion propia donde
 * hace falta cumplir las restricciones funcionales.
 */
object CsvReader {
  private val CsvMinColumnCount = 14
  private val IdColumn = 0
  private val TailNumColumn = 3
  private val OriginSeqIdColumn = 5
  private val OriginAirportColumn = 6
  private val DestinationSeqIdColumn = 7
  private val DestinationAirportColumn = 8
  private val DepartureDelayColumn = 10
  private val ArrivalDelayColumn = 12
  private val WeatherDelayColumn = 13

  /**
   * Carga un CSV desde disco y lo convierte en `Dataset`.
   *
   * Comprueba que el fichero exista, abre un `Source`, delega el parseo en el
   * iterador de lineas y cierra siempre el recurso. Cualquier error se devuelve
   * como texto dentro de `Left` para que `Main` pueda reintentar.
   *
   * @param path ruta del fichero CSV.
   * @return `Right(Dataset)` si la carga termina bien, o `Left(mensaje)` si falla.
   */
  def loadDataset(path: String): Either[String, Dataset] = {
    val file = new java.io.File(path)
    if (!file.exists()) {
      Left("No se pudo abrir el archivo CSV indicado.")
    } else {
      val source = Source.fromFile(file)
      try {
        val lines = source.getLines()
        parseDatasetIterator(path, lines) match {
          case Some(dataset) => Right(dataset)
          case None          => Left("El CSV esta vacio o no se pudo leer la cabecera.")
        }
      } catch {
        case ex: Exception => Left(s"No se pudo cargar el CSV: ${ex.getMessage}")
      } finally {
        // El fichero se cierra incluso si el parseo lanza una excepcion.
        source.close()
      }
    }
  }

  /**
   * Salta la cabecera del CSV y procesa las filas restantes.
   *
   * Si no existe cabecera, no se puede saber que el fichero sea un CSV valido y
   * se devuelve `None`. Si hay cabecera, se inicializa el resumen y se procesa el
   * resto de lineas de forma recursiva.
   *
   * @param path ruta original del CSV, guardada en el `Dataset`.
   * @param lines iterador de lineas abierto desde el fichero.
   * @return dataset construido, o `None` si no habia ninguna linea.
   */
  private def parseDatasetIterator(path: String, lines: Iterator[String]): Option[Dataset] = {
    if (lines.hasNext) {
      // La primera linea es la cabecera: se consume y no se convierte a `Flight`.
      lines.next()
      val initialSummary = LoadSummary(0, 0, 0, 0, 0, 0)
      val parsed = parseFlightRows(lines, initialSummary, Nil)
      // Las filas se acumulan por cabeza; por eso se restaura el orden antes de crear el dataset.
      Some(Dataset(path, AppUtils.restoreFlightsOrder(parsed._2, Nil), parsed._1))
    } else {
      None
    }
  }

  /**
   * Procesa todas las filas de datos del CSV.
   *
   * Por cada linea incrementa `rowsRead`, separa los campos, intenta crear un
   * `Flight` y actualiza los contadores de filas almacenadas, descartadas y
   * valores ausentes. Los vuelos validos se agregan por cabeza en `acc`.
   *
   * @param lines iterador con las lineas pendientes.
   * @param summary resumen acumulado hasta la fila anterior.
   * @param acc vuelos validos acumulados en orden inverso.
   * @return tupla con resumen final y lista acumulada de vuelos.
   */
  @tailrec
  private def parseFlightRows(
      lines: Iterator[String],
      summary: LoadSummary,
      acc: List[Flight]
  ): (LoadSummary, List[Flight]) = {
    if (lines.hasNext) {
      val line = lines.next()
      val fields = splitCsvLine(line)
      val readSummary = summary.copy(rowsRead = summary.rowsRead + 1)
      parseFlight(fields) match {
        case Some(flight) =>
          // Si la fila es valida, se contabiliza y se revisan los retrasos ausentes.
          val nextSummary = readSummary.copy(
            storedRows = readSummary.storedRows + 1,
            missingDepartureDelay = addIfMissing(readSummary.missingDepartureDelay, flight.departureDelay),
            missingArrivalDelay = addIfMissing(readSummary.missingArrivalDelay, flight.arrivalDelay),
            missingWeatherDelay = addIfMissing(readSummary.missingWeatherDelay, flight.weatherDelay)
          )
          parseFlightRows(lines, nextSummary, flight :: acc)
        case None =>
          // Las filas con menos columnas de las necesarias no entran en el dataset.
          parseFlightRows(lines, readSummary.copy(discardedRows = readSummary.discardedRows + 1), acc)
      }
    } else {
      (summary, acc)
    }
  }

  /**
   * Convierte los campos de una fila CSV en un `Flight`.
   *
   * La funcion exige al menos 14 columnas porque las posiciones usadas llegan
   * hasta `WEATHER_DELAY`. Los campos numericos invalidos se transforman en
   * `None` en las columnas opcionales, o en `-1` en identificadores necesarios.
   *
   * @param fields campos ya separados de una linea CSV.
   * @return `Some(Flight)` si hay suficientes columnas, o `None` si la fila es incompleta.
   */
  private def parseFlight(fields: List[String]): Option[Flight] = {
    val fieldCount = AppUtils.countFields(fields)
    if (fieldCount < CsvMinColumnCount) {
      None
    } else {
      Some(
        // `fieldAt` evita indexacion directa y `cleanToken` normaliza comillas/espacios.
        Flight(
          id = parseIntField(AppUtils.fieldAt(fields, IdColumn)).getOrElse(-1),
          tailNum = cleanToken(AppUtils.fieldAt(fields, TailNumColumn)),
          originSeqId = parseIntField(AppUtils.fieldAt(fields, OriginSeqIdColumn)).getOrElse(-1),
          originAirport = cleanToken(AppUtils.fieldAt(fields, OriginAirportColumn)),
          destinationSeqId = parseIntField(AppUtils.fieldAt(fields, DestinationSeqIdColumn)).getOrElse(-1),
          destinationAirport = cleanToken(AppUtils.fieldAt(fields, DestinationAirportColumn)),
          departureDelay = parseIntField(AppUtils.fieldAt(fields, DepartureDelayColumn)),
          arrivalDelay = parseIntField(AppUtils.fieldAt(fields, ArrivalDelayColumn)),
          weatherDelay = parseIntField(AppUtils.fieldAt(fields, WeatherDelayColumn))
        )
      )
    }
  }

  /**
   * Incrementa un contador cuando un campo opcional esta ausente.
   *
   * @param counter valor acumulado del contador.
   * @param value campo opcional que se acaba de parsear.
   * @return contador igual si hay valor, o contador + 1 si es `None`.
   */
  private def addIfMissing(counter: Int, value: Option[Int]): Int = {
    value match {
      case Some(_) => counter
      case None    => counter + 1
    }
  }

  /**
   * Divide una linea CSV en campos respetando comillas.
   *
   * @param line linea completa del CSV.
   * @return lista de tokens en el orden en que aparecen en la linea.
   */
  private def splitCsvLine(line: String): List[String] = {
    splitCsvChars(line.toList, "", false, Nil)
  }

  /**
   * Recorre los caracteres de una linea CSV y construye sus campos.
   *
   * Mantiene tres estados: el token actual, si se esta dentro de comillas y la
   * lista de tokens ya cerrados. Una coma solo separa campos cuando no estamos
   * dentro de comillas.
   *
   * @param chars caracteres pendientes de procesar.
   * @param currentToken texto acumulado del campo actual.
   * @param insideQuotes indica si el cursor esta dentro de un campo entre comillas.
   * @param tokens campos ya terminados.
   * @return lista completa de campos de la linea.
   */
  @tailrec
  private def splitCsvChars(
      chars: List[Char],
      currentToken: String,
      insideQuotes: Boolean,
      tokens: List[String]
  ): List[String] = {
    chars match {
      case Nil =>
        // Al terminar la linea se anade el ultimo token, que no va seguido de coma.
        AppUtils.appendString(tokens, currentToken)
      case '"' :: '"' :: tail if insideQuotes =>
        // Dos comillas seguidas dentro de comillas representan una comilla literal.
        splitCsvChars(tail, currentToken + "\"", insideQuotes, tokens)
      case '"' :: tail =>
        // Una comilla simple abre o cierra el modo quoted.
        splitCsvChars(tail, currentToken, !insideQuotes, tokens)
      case ',' :: tail if !insideQuotes =>
        // La coma exterior cierra el token actual y empieza uno nuevo.
        splitCsvChars(tail, "", insideQuotes, AppUtils.appendString(tokens, currentToken))
      case '\r' :: tail =>
        // Se ignora el retorno de carro de ficheros CRLF.
        splitCsvChars(tail, currentToken, insideQuotes, tokens)
      case char :: tail =>
        splitCsvChars(tail, currentToken + char.toString, insideQuotes, tokens)
    }
  }

  /**
   * Limpia un campo CSV individual.
   *
   * @param token token original, posiblemente con espacios o comillas exteriores.
   * @return token normalizado sin espacios exteriores ni comillas envolventes.
   */
  private def cleanToken(token: String): String = {
    val trimmed = AppUtils.trim(token)
    stripOuterQuotes(trimmed)
  }

  /**
   * Quita la comilla inicial de un campo si existe.
   *
   * Solo elimina comillas exteriores: si no hay comilla inicial, devuelve el
   * token sin cambios. Si la hay, delega la eliminacion de la comilla final.
   *
   * @param token token ya recortado de espacios.
   * @return texto sin comillas exteriores si estaban presentes.
   */
  private def stripOuterQuotes(token: String): String = {
    val chars = token.toList
    chars match {
      case '"' :: tail => stripClosingQuote(AppUtils.charsToString(tail))
      case _           => token
    }
  }

  /**
   * Quita la comilla final de un campo previamente abierto con comilla inicial.
   *
   * @param token texto sin la primera comilla.
   * @return texto sin la ultima comilla si existia, y sin espacios exteriores.
   */
  private def stripClosingQuote(token: String): String = {
    removeLastQuote(token.toList) match {
      case Some(value) => AppUtils.trim(value)
      case None        => AppUtils.trim(token)
    }
  }

  /**
   * Elimina una comilla final sin usar `last` ni `reverse`.
   *
   * Recorre la lista hasta encontrar que el ultimo caracter es `"`. Al volver de
   * la recursion reconstruye el texto anterior caracter a caracter.
   *
   * @param chars caracteres del token sin la comilla inicial.
   * @return `Some(texto)` si la ultima posicion era comilla, o `None` si no.
   */
  private def removeLastQuote(chars: List[Char]): Option[String] = {
    chars match {
      case Nil => None
      case '"' :: Nil => Some("")
      case head :: tail =>
        // Si el sufijo confirma que habia comilla final, se recompone el prefijo.
        removeLastQuote(tail) match {
          case Some(rest) => Some(head.toString + rest)
          case None       => None
        }
    }
  }

  /**
   * Convierte un campo numerico del CSV a entero opcional.
   *
   * Los campos vacios o invalidos devuelven `None`. Se pasa por `Double` antes
   * de `Int` para aceptar valores numericos que vengan con formato decimal.
   *
   * @param raw campo original del CSV.
   * @return entero parseado o `None` si no hay numero valido.
   */
  private def parseIntField(raw: String): Option[Int] = {
    val cleaned = cleanToken(raw)
    cleaned match {
      case "" => None
      case _  => Try(cleaned.toDouble.toInt).toOption
    }
  }
}
