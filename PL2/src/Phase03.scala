import scala.annotation.tailrec

object Phase03 {
  // Punto de entrada de la fase: recoge las opciones de columna y reduccion desde consola.
  def run(dataset: Dataset): Option[PhaseResult] = {
    println()
    println("Fase 03 - Reduccion")
    println("1. DEP_DELAY  2. ARR_DELAY  3. WEATHER_DELAY")

    AppUtils.readIntegerInRange("Columna: ", "Debe introducir un numero entre 1 y 3, o X.", 1, 3) match {
      case Some(columnOption) =>
        println("1. Maximo  2. Minimo")
        AppUtils.readIntegerInRange("Reduccion: ", "Debe introducir un numero entre 1 y 2, o X.", 1, 2) match {
          case Some(reductionOption) =>
            executeReduction(dataset.flights, columnOption, reductionOption)
          case None => None
        }
      case None => None
    }
  }

  private def executeReduction(
      flights: List[Flight],
      columnOption: Int,
      reductionOption: Int
  ): Option[PhaseResult] = {
    // Traduce las opciones numericas del menu a etiquetas y ejecuta solo la variante Simple.
    val columnLabel = columnName(columnOption)
    val isMax = reductionOption == 1
    val reductionLabel = if (isMax) "Maximo" else "Minimo"
    val reductionFunctionLabel = if (isMax) "Max" else "Min"
    val reduced = reduceDelayValues(flights, columnOption, isMax, None, 0)

    reduced match {
      case Some(ReductionState(result, validCount)) =>
        println(s"$columnLabel | $reductionLabel | validos $validCount")
        println()
        println(s"[Simple] $reductionFunctionLabel() $columnLabel = $result minutos")
        Some(
          PhaseResult(
            "Fase 03 - Reduccion de retraso",
            s"columna=$columnLabel; reduccion=$reductionLabel",
            s"[Simple] $reductionFunctionLabel() $columnLabel = $result minutos; validos=$validCount"
          )
        )
      case None =>
        println("No hay valores validos para la Fase 03.")
        None
    }
  }

  @tailrec
  private def reduceDelayValues(
      flights: List[Flight],
      columnOption: Int,
      isMax: Boolean,
      bestValue: Option[Int],
      validCount: Int
  ): Option[ReductionState] = {
    // Sustituye la reduccion atomica simple de CUDA por un acumulador recursivo de cola.
    // `bestValue` guarda el maximo/minimo actual y `validCount` ignora los valores ausentes.
    flights match {
      case Nil =>
        bestValue match {
          case Some(value) => Some(ReductionState(value, validCount))
          case None        => None
        }
      case flight :: tail =>
        selectedDelay(flight, columnOption) match {
          case Some(value) =>
            val nextBest = bestValue match {
              case Some(current) => Some(compareReduction(current, value, isMax))
              case None          => Some(value)
            }
            reduceDelayValues(tail, columnOption, isMax, nextBest, validCount + 1)
          case None =>
            reduceDelayValues(tail, columnOption, isMax, bestValue, validCount)
        }
    }
  }

  private def selectedDelay(flight: Flight, columnOption: Int): Option[Int] = {
    // Devuelve la columna elegida manteniendo los valores vacios como None.
    columnOption match {
      case 1 => flight.departureDelay
      case 2 => flight.arrivalDelay
      case 3 => flight.weatherDelay
      case _ => None
    }
  }

  private def columnName(columnOption: Int): String = {
    columnOption match {
      case 1 => "DEP_DELAY"
      case 2 => "ARR_DELAY"
      case 3 => "WEATHER_DELAY"
      case _ => "DESCONOCIDA"
    }
  }

  private def compareReduction(left: Int, right: Int, isMax: Boolean): Int = {
    // Equivalente funcional de atomicMax/atomicMin para dos valores.
    if (isMax) {
      if (left > right) left else right
    } else {
      if (left < right) left else right
    }
  }

  private final case class ReductionState(value: Int, validCount: Int)
}
