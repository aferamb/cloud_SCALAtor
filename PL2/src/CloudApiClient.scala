import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.{Duration, OffsetDateTime}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/**
 * Cliente HTTP encargado de enviar resultados Scala a la API Cloud.
 *
 * Este archivo contiene dos responsabilidades relacionadas: `CloudApiClient`
 * prepara y ejecuta el `POST`, mientras que `CloudJson` serializa manualmente
 * el resultado a JSON sin depender de librerias externas.
 */
object CloudApiClient {
  private val RequestTimeout = Duration.ofSeconds(45)
  private val Client = HttpClient
    .newBuilder()
    .connectTimeout(Duration.ofSeconds(15))
    .build()

  /**
   * Envia a la API el resultado de una fase.
   *
   * Normaliza la URL configurada, construye el JSON completo con fecha actual y
   * ejecuta un `POST` con cabeceras JSON. Los fallos de red o URL se devuelven
   * como `Left` para que el menu pueda mostrarlos sin detener el programa.
   *
   * @param config configuracion Cloud confirmada al arrancar la aplicacion.
   * @param dataset dataset usado en la fase, incluido en el resumen enviado.
   * @param result resultado estructurado de la fase ejecutada.
   * @return `Right(body)` si la API responde 2xx, o `Left(error)` si falla.
   */
  def postResult(config: CloudConfig, dataset: Dataset, result: PhaseResult): Either[String, String] = {
    val endpoint = normalizeEndpoint(config.apiUrl)
    val payload = CloudJson.resultPayload(config.userName, OffsetDateTime.now().toString, dataset, result)

    Try {
      // Se construye una peticion inmutable con timeout y cuerpo UTF-8.
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
        // La respuesta HTTP se interpreta aparte para distinguir codigos 2xx de errores de API.
        handleResponse(response)
      case Failure(error) =>
        Left(s"No se pudo conectar con la API: ${error.getMessage}")
    }
  }

  /**
   * Convierte una URL base en el endpoint real de resultados.
   *
   * Permite que el usuario configure tanto la URL completa `/api/results` como
   * solo la URL base del servicio. Si ya termina en `/api/results`, no se toca.
   *
   * @param rawUrl URL escrita en la configuracion o consola.
   * @return URL final a la que se hara el `POST`.
   */
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

  /**
   * Traduce la respuesta HTTP a un `Either`.
   *
   * @param response respuesta devuelta por `HttpClient`.
   * @return cuerpo como `Right` si el estado es 2xx, o error legible si no.
   */
  private def handleResponse(response: HttpResponse[String]): Either[String, String] = {
    val body = response.body()
    if (response.statusCode() >= 200 && response.statusCode() <= 299) {
      Right(body)
    } else {
      Left(s"La API respondio ${response.statusCode()}: $body")
    }
  }
}

/**
 * Serializador JSON manual para los resultados Cloud.
 *
 * Todas las funciones devuelven fragmentos de texto JSON. Se usan helpers
 * pequenos para mantener escapado correcto de cadenas, campos opcionales y
 * arrays sin depender de una libreria de JSON externa.
 */
object CloudJson {
  /**
   * Construye el payload JSON completo que espera la API.
   *
   * @param userName usuario que identifica la ejecucion.
   * @param executedAt fecha/hora ISO generada al enviar.
   * @param dataset dataset usado, del que se envia solo resumen y ruta.
   * @param result resultado de fase con opciones, resumen e items.
   * @return documento JSON como texto.
   */
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

  /**
   * Serializa el bloque identificador de fase.
   *
   * @param result resultado de fase que contiene codigo y nombre.
   * @return objeto JSON con `code` y `name`.
   */
  private def phaseJson(result: PhaseResult): String = {
    "{" +
      stringField("code", result.phaseCode) + "," +
      stringField("name", result.phaseName) +
      "}"
  }

  /**
   * Serializa las opciones de entrada y contadores de items.
   *
   * `phaseOptions` ya llega como JSON para conservar exactamente las opciones
   * especificas de cada fase.
   *
   * @param result resultado de fase.
   * @return objeto JSON con opciones y metadatos de truncado.
   */
  private def inputOptionsJson(result: PhaseResult): String = {
    "{" +
      "\"phaseOptions\":" + result.inputOptionsJson + "," +
      numberField("totalItemCount", result.totalItemCount) + "," +
      numberField("sentItemCount", result.sentItemCount) + "," +
      booleanField("itemsTruncated", result.itemsTruncated) +
      "}"
  }

  /**
   * Serializa la informacion de origen del dataset.
   *
   * @param dataset dataset cargado en memoria.
   * @return objeto JSON con ruta y resumen de carga.
   */
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

  /**
   * Serializa la lista de items como array JSON.
   *
   * @param items items generados por la fase, ya limitados por `itemLimit`.
   * @return array JSON textual.
   */
  private def itemsJson(items: List[CloudResultItem]): String = {
    "[" + itemsJsonLoop(items, "") + "]"
  }

  /**
   * Construye recursivamente el contenido del array de items.
   *
   * @param items items pendientes de serializar.
   * @param acc texto acumulado sin los corchetes externos.
   * @return fragmento JSON con los items separados por comas.
   */
  @tailrec
  private def itemsJsonLoop(items: List[CloudResultItem], acc: String): String = {
    items match {
      case Nil => acc
      case head :: tail =>
        // Si ya hay contenido, se antepone una coma antes del siguiente objeto.
        val prefix = if (acc == "") "" else acc + ","
        itemsJsonLoop(tail, prefix + itemJson(head))
    }
  }

  /**
   * Serializa un item de salida de cualquier fase.
   *
   * Los campos que no correspondan a la fase concreta se escriben como `null`
   * para mantener una estructura estable en la API y en la base de datos.
   *
   * @param item item normalizado.
   * @return objeto JSON textual.
   */
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

  /**
   * Construye un campo JSON de texto obligatorio.
   *
   * @param name nombre del campo.
   * @param value valor textual.
   * @return par `"name":"value"` con ambos lados escapados.
   */
  private def stringField(name: String, value: String): String = {
    quote(name) + ":" + quote(value)
  }

  /**
   * Construye un campo JSON numerico obligatorio.
   *
   * @param name nombre del campo.
   * @param value valor entero.
   * @return par `"name":value`.
   */
  private def numberField(name: String, value: Int): String = {
    quote(name) + ":" + value.toString
  }

  /**
   * Construye un campo JSON booleano obligatorio.
   *
   * @param name nombre del campo.
   * @param value valor booleano.
   * @return par `"name":true` o `"name":false`.
   */
  private def booleanField(name: String, value: Boolean): String = {
    quote(name) + ":" + booleanJson(value)
  }

  /**
   * Construye un campo JSON de texto opcional.
   *
   * @param name nombre del campo.
   * @param value valor opcional.
   * @return par con texto escapado o `null`.
   */
  private def optionStringField(name: String, value: Option[String]): String = {
    quote(name) + ":" + optionString(value)
  }

  /**
   * Construye un campo JSON numerico opcional.
   *
   * @param name nombre del campo.
   * @param value entero opcional.
   * @return par con numero o `null`.
   */
  private def optionNumberField(name: String, value: Option[Int]): String = {
    quote(name) + ":" + optionNumber(value)
  }

  /**
   * Serializa un `Option[String]` como JSON.
   *
   * @param value texto opcional.
   * @return cadena JSON entrecomillada o `null`.
   */
  private def optionString(value: Option[String]): String = {
    value match {
      case Some(text) => quote(text)
      case None       => "null"
    }
  }

  /**
   * Serializa un `Option[Int]` como JSON.
   *
   * @param value entero opcional.
   * @return numero como texto o `null`.
   */
  private def optionNumber(value: Option[Int]): String = {
    value match {
      case Some(number) => number.toString
      case None         => "null"
    }
  }

  /**
   * Serializa un booleano al literal JSON correspondiente.
   *
   * @param value valor booleano Scala.
   * @return `true` o `false`.
   */
  private def booleanJson(value: Boolean): String = {
    if (value) "true" else "false"
  }

  /**
   * Escapa y envuelve un texto entre comillas JSON.
   *
   * @param value texto original.
   * @return texto listo para insertarse como string JSON.
   */
  private def quote(value: String): String = {
    "\"" + escapeChars(value.toList) + "\""
  }

  /**
   * Escapa recursivamente caracteres especiales de JSON.
   *
   * @param chars caracteres pendientes de procesar.
   * @return texto escapado sin comillas externas.
   */
  private def escapeChars(chars: List[Char]): String = {
    chars match {
      case Nil => ""
      // Cada rama sustituye un caracter especial por su secuencia JSON valida.
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
