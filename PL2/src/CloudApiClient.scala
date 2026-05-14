import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.{Duration, OffsetDateTime}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object CloudApiClient {
  private val RequestTimeout = Duration.ofSeconds(45)
  private val Client = HttpClient
    .newBuilder()
    .connectTimeout(Duration.ofSeconds(15))
    .build()

  def postResult(config: CloudConfig, dataset: Dataset, result: PhaseResult): Either[String, String] = {
    val endpoint = normalizeEndpoint(config.apiUrl)
    val payload = CloudJson.resultPayload(config.userName, OffsetDateTime.now().toString, dataset, result)

    Try {
      val request = HttpRequest
        .newBuilder()
        .uri(URI.create(endpoint))
        .timeout(RequestTimeout)
        .header("Content-Type", "application/json; charset=utf-8")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
        .build()

      Client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    } match {
      case Success(response) =>
        handleResponse(response)
      case Failure(error) =>
        Left(s"No se pudo conectar con la API: ${error.getMessage}")
    }
  }

  private def normalizeEndpoint(rawUrl: String): String = {
    val trimmed = AppUtils.trim(rawUrl)
    if (trimmed.endsWith("/api/results") || trimmed.endsWith("/api/results/")) {
      trimmed
    } else if (trimmed.endsWith("/")) {
      trimmed + "api/results"
    } else {
      trimmed + "/api/results"
    }
  }

  private def handleResponse(response: HttpResponse[String]): Either[String, String] = {
    val body = response.body()
    if (response.statusCode() >= 200 && response.statusCode() <= 299) {
      Right(body)
    } else {
      Left(s"La API respondio ${response.statusCode()}: $body")
    }
  }
}

object CloudJson {
  def resultPayload(userName: String, executedAt: String, dataset: Dataset, result: PhaseResult): String = {
    "{" +
      stringField("userName", userName) + "," +
      stringField("executedAt", executedAt) + "," +
      stringField("sourceApp", "cloud_SCALAtor Scala") + "," +
      "\"phase\":" + phaseJson(result) + "," +
      "\"inputOptions\":" + inputOptionsJson(result) + "," +
      stringField("summary", result.resultSummary) + "," +
      "\"dataset\":" + datasetJson(dataset) + "," +
      "\"items\":" + itemsJson(result.items) +
      "}"
  }

  private def phaseJson(result: PhaseResult): String = {
    "{" +
      stringField("code", result.phaseCode) + "," +
      stringField("name", result.phaseName) +
      "}"
  }

  private def inputOptionsJson(result: PhaseResult): String = {
    "{" +
      "\"phaseOptions\":" + result.inputOptionsJson + "," +
      numberField("totalItemCount", result.totalItemCount) + "," +
      numberField("sentItemCount", result.sentItemCount) + "," +
      booleanField("itemsTruncated", result.itemsTruncated) +
      "}"
  }

  private def datasetJson(dataset: Dataset): String = {
    val summary = dataset.summary
    "{" +
      stringField("path", dataset.path) + "," +
      numberField("rowsRead", summary.rowsRead) + "," +
      numberField("storedRows", summary.storedRows) + "," +
      numberField("discardedRows", summary.discardedRows) + "," +
      numberField("missingDepartureDelay", summary.missingDepartureDelay) + "," +
      numberField("missingArrivalDelay", summary.missingArrivalDelay) + "," +
      numberField("missingWeatherDelay", summary.missingWeatherDelay) +
      "}"
  }

  private def itemsJson(items: List[CloudResultItem]): String = {
    "[" + itemsJsonLoop(items, "") + "]"
  }

  @tailrec
  private def itemsJsonLoop(items: List[CloudResultItem], acc: String): String = {
    items match {
      case Nil => acc
      case head :: tail =>
        val prefix = if (acc == "") "" else acc + ","
        itemsJsonLoop(tail, prefix + itemJson(head))
    }
  }

  private def itemJson(item: CloudResultItem): String = {
    "{" +
      stringField("itemType", item.itemType) + "," +
      optionNumberField("flightId", item.flightId) + "," +
      optionStringField("tailNum", item.tailNum) + "," +
      optionStringField("airportCode", item.airportCode) + "," +
      optionNumberField("airportSeqId", item.airportSeqId) + "," +
      optionStringField("delayKind", item.delayKind) + "," +
      optionNumberField("delayMinutes", item.delayMinutes) + "," +
      optionStringField("reductionColumn", item.reductionColumn) + "," +
      optionStringField("reductionType", item.reductionType) + "," +
      optionNumberField("reductionValue", item.reductionValue) + "," +
      optionNumberField("validCount", item.validCount) + "," +
      optionStringField("airportKind", item.airportKind) + "," +
      optionNumberField("airportCount", item.airportCount) + "," +
      optionStringField("barText", item.barText) + "," +
      optionStringField("rawText", item.rawText) +
      "}"
  }

  private def stringField(name: String, value: String): String = {
    quote(name) + ":" + quote(value)
  }

  private def numberField(name: String, value: Int): String = {
    quote(name) + ":" + value.toString
  }

  private def booleanField(name: String, value: Boolean): String = {
    quote(name) + ":" + booleanJson(value)
  }

  private def optionStringField(name: String, value: Option[String]): String = {
    quote(name) + ":" + optionString(value)
  }

  private def optionNumberField(name: String, value: Option[Int]): String = {
    quote(name) + ":" + optionNumber(value)
  }

  private def optionString(value: Option[String]): String = {
    value match {
      case Some(text) => quote(text)
      case None       => "null"
    }
  }

  private def optionNumber(value: Option[Int]): String = {
    value match {
      case Some(number) => number.toString
      case None         => "null"
    }
  }

  private def booleanJson(value: Boolean): String = {
    if (value) "true" else "false"
  }

  private def quote(value: String): String = {
    "\"" + escapeChars(value.toList) + "\""
  }

  private def escapeChars(chars: List[Char]): String = {
    chars match {
      case Nil => ""
      case '"' :: tail => "\\\"" + escapeChars(tail)
      case '\\' :: tail => "\\\\" + escapeChars(tail)
      case '\b' :: tail => "\\b" + escapeChars(tail)
      case '\f' :: tail => "\\f" + escapeChars(tail)
      case '\n' :: tail => "\\n" + escapeChars(tail)
      case '\r' :: tail => "\\r" + escapeChars(tail)
      case '\t' :: tail => "\\t" + escapeChars(tail)
      case head :: tail if head < ' ' => "\\u%04x".format(head.toInt) + escapeChars(tail)
      case head :: tail => head.toString + escapeChars(tail)
    }
  }
}
