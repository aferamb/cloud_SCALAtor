import scala.annotation.tailrec
import scala.io.Source
import scala.util.Try

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
        source.close()
      }
    }
  }

  private def parseDatasetIterator(path: String, lines: Iterator[String]): Option[Dataset] = {
    if (lines.hasNext) {
      lines.next()
      val initialSummary = LoadSummary(0, 0, 0, 0, 0, 0)
      val parsed = parseFlightRows(lines, initialSummary, Nil)
      Some(Dataset(path, AppUtils.restoreFlightsOrder(parsed._2, Nil), parsed._1))
    } else {
      None
    }
  }

  @tailrec
  private def parseFlightRows(
      lines: Iterator[String],
      summary: LoadSummary,
      acc: List[Flight]
  ): (LoadSummary, List[Flight]) = {
    // Recorrido del CSV con acumulador para evitar bucles y mantener la carga controlada.
    if (lines.hasNext) {
      val line = lines.next()
      val fields = splitCsvLine(line)
      val readSummary = summary.copy(rowsRead = summary.rowsRead + 1)
      parseFlight(fields) match {
        case Some(flight) =>
          val nextSummary = readSummary.copy(
            storedRows = readSummary.storedRows + 1,
            missingDepartureDelay = addIfMissing(readSummary.missingDepartureDelay, flight.departureDelay),
            missingArrivalDelay = addIfMissing(readSummary.missingArrivalDelay, flight.arrivalDelay),
            missingWeatherDelay = addIfMissing(readSummary.missingWeatherDelay, flight.weatherDelay)
          )
          parseFlightRows(lines, nextSummary, flight :: acc)
        case None =>
          parseFlightRows(lines, readSummary.copy(discardedRows = readSummary.discardedRows + 1), acc)
      }
    } else {
      (summary, acc)
    }
  }

  private def parseFlight(fields: List[String]): Option[Flight] = {
    val fieldCount = AppUtils.countFields(fields)
    if (fieldCount < CsvMinColumnCount) {
      None
    } else {
      Some(
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

  private def addIfMissing(counter: Int, value: Option[Int]): Int = {
    value match {
      case Some(_) => counter
      case None    => counter + 1
    }
  }

  private def splitCsvLine(line: String): List[String] = {
    splitCsvChars(line.toList, "", false, Nil)
  }

  @tailrec
  private def splitCsvChars(
      chars: List[Char],
      currentToken: String,
      insideQuotes: Boolean,
      tokens: List[String]
  ): List[String] = {
    chars match {
      case Nil =>
        AppUtils.appendString(tokens, currentToken)
      case '"' :: '"' :: tail if insideQuotes =>
        splitCsvChars(tail, currentToken + "\"", insideQuotes, tokens)
      case '"' :: tail =>
        splitCsvChars(tail, currentToken, !insideQuotes, tokens)
      case ',' :: tail if !insideQuotes =>
        splitCsvChars(tail, "", insideQuotes, AppUtils.appendString(tokens, currentToken))
      case '\r' :: tail =>
        splitCsvChars(tail, currentToken, insideQuotes, tokens)
      case char :: tail =>
        splitCsvChars(tail, currentToken + char.toString, insideQuotes, tokens)
    }
  }

  private def cleanToken(token: String): String = {
    val trimmed = AppUtils.trim(token)
    stripOuterQuotes(trimmed)
  }

  private def stripOuterQuotes(token: String): String = {
    val chars = token.toList
    chars match {
      case '"' :: tail => stripClosingQuote(AppUtils.charsToString(tail))
      case _           => token
    }
  }

  private def stripClosingQuote(token: String): String = {
    removeLastQuote(token.toList) match {
      case Some(value) => AppUtils.trim(value)
      case None        => AppUtils.trim(token)
    }
  }

  private def removeLastQuote(chars: List[Char]): Option[String] = {
    chars match {
      case Nil => None
      case '"' :: Nil => Some("")
      case head :: tail =>
        removeLastQuote(tail) match {
          case Some(rest) => Some(head.toString + rest)
          case None       => None
        }
    }
  }

  private def parseIntField(raw: String): Option[Int] = {
    val cleaned = cleanToken(raw)
    cleaned match {
      case "" => None
      case _  => Try(cleaned.toDouble.toInt).toOption
    }
  }
}
