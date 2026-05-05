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

final case class LoadSummary(
    rowsRead: Int,
    storedRows: Int,
    discardedRows: Int,
    missingDepartureDelay: Int,
    missingArrivalDelay: Int,
    missingWeatherDelay: Int
)

final case class Dataset(path: String, flights: List[Flight], summary: LoadSummary)

final case class PhaseResult(
    phaseName: String,
    inputOptions: String,
    resultSummary: String
)
