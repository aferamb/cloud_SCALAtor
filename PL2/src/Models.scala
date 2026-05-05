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

// Resultado compacto de una fase, pensado tambien como base del envio Cloud.
final case class PhaseResult(
    phaseName: String,
    inputOptions: String,
    resultSummary: String
)
