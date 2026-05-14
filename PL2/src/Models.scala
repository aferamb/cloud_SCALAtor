// Representa una fila util del dataset con solo las columnas necesarias.
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

// Resume la carga del CSV y los valores ausentes detectados.
final case class LoadSummary(
    rowsRead: Int,
    storedRows: Int,
    discardedRows: Int,
    missingDepartureDelay: Int,
    missingArrivalDelay: Int,
    missingWeatherDelay: Int
)

// Agrupa el dataset cargado junto con su ruta y estadisticas.
final case class Dataset(path: String, flights: List[Flight], summary: LoadSummary)

// Item normalizado para enviarlo a la API Cloud.
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

// Resultado de una fase listo para imprimirse y enviarse a la API.
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

// Configuracion local para el envio Cloud.
final case class CloudConfig(apiUrl: String, userName: String, itemLimit: Int)
