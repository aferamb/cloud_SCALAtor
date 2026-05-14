/**
 * Archivo de modelos inmutables usados por toda la aplicacion Scala.
 *
 * Cada `case class` representa una pieza de informacion que viaja entre la
 * lectura del CSV, las fases de calculo, el menu principal y el envio Cloud.
 * No contienen logica: son estructuras de datos puras para que el resto del
 * codigo pueda trabajar sin colecciones mutables ni variables globales.
 */

/**
 * Representa una fila util del dataset de vuelos.
 *
 * El CSV original contiene muchas columnas, pero la practica solo necesita las
 * columnas de identificacion, aeropuerto y retrasos. Los retrasos se guardan en
 * `Option[Int]` porque pueden venir vacios o con valores no convertibles.
 *
 * @param id identificador de la fila en el dataset, tomado de la primera columna.
 * @param tailNum matricula del avion (`TAIL_NUM`).
 * @param originSeqId identificador numerico del aeropuerto de origen.
 * @param originAirport codigo textual del aeropuerto de origen.
 * @param destinationSeqId identificador numerico del aeropuerto de destino.
 * @param destinationAirport codigo textual del aeropuerto de destino.
 * @param departureDelay retraso o adelanto en salida (`DEP_DELAY`), si existe.
 * @param arrivalDelay retraso o adelanto en llegada (`ARR_DELAY`), si existe.
 * @param weatherDelay retraso meteorologico (`WEATHER_DELAY`), si existe.
 */
final case class Flight(
    id: Int,
    tailNum: String,
    originSeqId: Int,
    originAirport: String,
    destinationSeqId: Int,
    destinationAirport: String,
    departureDelay: Option[Int],
    arrivalDelay: Option[Int],
    weatherDelay: Option[Int]
)

/**
 * Resume el proceso de carga del CSV.
 *
 * Se usa para mostrar al usuario cuantas filas se han leido, cuantas se han
 * almacenado como `Flight` y cuantos datos faltantes se han detectado en las
 * columnas de retraso.
 *
 * @param rowsRead numero de filas de datos recorridas, sin contar la cabecera.
 * @param storedRows filas convertidas correctamente a `Flight`.
 * @param discardedRows filas descartadas por no tener el minimo de columnas.
 * @param missingDepartureDelay filas almacenadas sin `DEP_DELAY` valido.
 * @param missingArrivalDelay filas almacenadas sin `ARR_DELAY` valido.
 * @param missingWeatherDelay filas almacenadas sin `WEATHER_DELAY` valido.
 */
final case class LoadSummary(
    rowsRead: Int,
    storedRows: Int,
    discardedRows: Int,
    missingDepartureDelay: Int,
    missingArrivalDelay: Int,
    missingWeatherDelay: Int
)

/**
 * Agrupa todos los vuelos cargados junto con su ruta y resumen de carga.
 *
 * @param path ruta del CSV usado para construir el dataset.
 * @param flights lista inmutable de vuelos validos, en el orden original del CSV.
 * @param summary estadisticas de lectura y datos ausentes.
 */
final case class Dataset(path: String, flights: List[Flight], summary: LoadSummary)

/**
 * Item normalizado de salida que se envia a la API Cloud.
 *
 * Cada fase produce datos distintos. Por eso casi todos los campos son
 * opcionales: Fase 01 y 02 rellenan datos de retraso, Fase 03 rellena campos de
 * reduccion y Fase 04 rellena campos de histograma.
 *
 * @param itemType tipo logico del item (`delay_match`, `reduction`, `airport_histogram`).
 * @param flightId identificador de vuelo cuando el item procede de una fila concreta.
 * @param tailNum matricula del avion cuando la fase la muestra.
 * @param airportCode codigo textual del aeropuerto para items de histograma.
 * @param airportSeqId identificador numerico del aeropuerto para histograma.
 * @param delayKind etiqueta de retraso o adelanto usada en fases 01 y 02.
 * @param delayMinutes minutos de retraso/adelanto detectados.
 * @param reductionColumn columna usada en la reduccion de Fase 03.
 * @param reductionType tipo de reduccion (`Maximo` o `Minimo`).
 * @param reductionValue resultado numerico de la reduccion.
 * @param validCount cantidad de valores validos usados por la reduccion.
 * @param airportKind indica si el histograma es de origen o destino.
 * @param airportCount numero de vuelos asociados al aeropuerto.
 * @param barText barra textual escalada del histograma.
 * @param rawText linea de texto original impresa por consola para trazabilidad.
 */
final case class CloudResultItem(
    itemType: String,
    flightId: Option[Int] = None,
    tailNum: Option[String] = None,
    airportCode: Option[String] = None,
    airportSeqId: Option[Int] = None,
    delayKind: Option[String] = None,
    delayMinutes: Option[Int] = None,
    reductionColumn: Option[String] = None,
    reductionType: Option[String] = None,
    reductionValue: Option[Int] = None,
    validCount: Option[Int] = None,
    airportKind: Option[String] = None,
    airportCount: Option[Int] = None,
    barText: Option[String] = None,
    rawText: Option[String] = None
)

/**
 * Resultado completo de una fase ejecutada.
 *
 * `Main` recibe este modelo despues de ejecutar una fase, lo muestra al usuario
 * como candidato a subida y lo pasa al serializador JSON si se confirma el
 * envio Cloud.
 *
 * @param phaseCode identificador estable de la fase.
 * @param phaseName nombre legible de la fase.
 * @param inputOptionsJson JSON ya construido con las opciones introducidas.
 * @param resultSummary resumen corto de lo calculado.
 * @param items items detallados que se enviaran a la API, ya limitados.
 * @param totalItemCount numero total de items generados antes de aplicar limite.
 * @param sentItemCount numero de items incluidos finalmente en `items`.
 * @param itemsTruncated indica si se recorto la lista por `CloudConfig.itemLimit`.
 */
final case class PhaseResult(
    phaseCode: String,
    phaseName: String,
    inputOptionsJson: String,
    resultSummary: String,
    items: List[CloudResultItem],
    totalItemCount: Int,
    sentItemCount: Int,
    itemsTruncated: Boolean
)

/**
 * Configuracion local usada por el cliente Cloud.
 *
 * @param apiUrl URL base o endpoint completo de la API.
 * @param userName nombre de usuario que identifica la ejecucion enviada.
 * @param itemLimit maximo de items detallados que se incluyen en cada envio.
 */
final case class CloudConfig(apiUrl: String, userName: String, itemLimit: Int)
