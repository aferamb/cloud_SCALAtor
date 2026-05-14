import scala.annotation.tailrec

/**
 * Fase 03: reduccion maximo/minimo sobre una columna de retraso.
 *
 * Implementa la variante simple de reduccion de la practica original. Elige una
 * columna (`DEP_DELAY`, `ARR_DELAY` o `WEATHER_DELAY`) y calcula el maximo o
 * minimo ignorando valores ausentes.
 */
object Phase03 {
  /**
   * Ejecuta la Fase 03 completa.
   *
   * Primero pide la columna de retraso y luego el tipo de reduccion. Si el
   * usuario cancela cualquiera de los dos pasos, no se genera resultado.
   *
   * @param dataset dataset actualmente cargado.
   * @param itemLimit maximo de items detallados que pueden enviarse a Cloud.
   * @return resultado de reduccion o `None` si se cancela/no hay datos validos.
   */
  def run(dataset: Dataset, itemLimit: Int): Option[PhaseResult] = {
    println()
    println("Fase 03 - Reduccion")
    println("1. DEP_DELAY  2. ARR_DELAY  3. WEATHER_DELAY")

    AppUtils.readIntegerInRange("Columna: ", "Debe introducir un numero entre 1 y 3, o X.", 1, 3) match {
      case Some(columnOption) =>
        println("1. Maximo  2. Minimo")
        AppUtils.readIntegerInRange("Reduccion: ", "Debe introducir un numero entre 1 y 2, o X.", 1, 2) match {
          case Some(reductionOption) =>
            executeReduction(dataset.flights, columnOption, reductionOption, itemLimit)
          case None => None
        }
      case None => None
    }
  }

  /**
   * Traduce opciones de menu y ejecuta la reduccion.
   *
   * Convierte numeros de menu a etiquetas, llama a `reduceDelayValues` y, si hay
   * resultado, construye un unico item Cloud con el valor reducido y la cantidad
   * de datos validos usados.
   *
   * @param flights vuelos sobre los que se calcula la reduccion.
   * @param columnOption opcion de columna elegida en el menu.
   * @param reductionOption opcion de reduccion: 1 maximo, 2 minimo.
   * @param itemLimit limite de items Cloud; afecta a si se incluye el item unico.
   * @return `Some(PhaseResult)` con la reduccion o `None` si no hay valores validos.
   */
  private def executeReduction(
      flights: List[Flight],
      columnOption: Int,
      reductionOption: Int,
      itemLimit: Int
  ): Option[PhaseResult] = {
    // Traduce las opciones numericas del menu a etiquetas y ejecuta solo la variante Simple.
    val columnLabel = columnName(columnOption)
    val isMax = reductionOption == 1
    val reductionLabel = if (isMax) "Maximo" else "Minimo"
    val reductionFunctionLabel = if (isMax) "Max" else "Min"
    // `None` como mejor valor inicial significa que aun no se ha encontrado ningun dato valido.
    val reduced = reduceDelayValues(flights, columnOption, isMax, None, 0)

    reduced match {
      case Some(ReductionState(result, validCount)) =>
        println(s"$columnLabel | $reductionLabel | validos $validCount")
        println()
        val rawText = s"[Simple] $reductionFunctionLabel() $columnLabel = $result minutos"
        println(rawText)
        val item = CloudResultItem(
          itemType = "reduction",
          reductionColumn = Some(columnLabel),
          reductionType = Some(reductionLabel),
          reductionValue = Some(result),
          validCount = Some(validCount),
          rawText = Some(s"$rawText; validos=$validCount")
        )
        // La Fase 03 produce un solo item; puede quedar truncado si el limite es 0.
        val items = if (itemLimit >= 1) item :: Nil else Nil
        val sentItems = if (itemLimit >= 1) 1 else 0
        Some(
          PhaseResult(
            "PHASE_03",
            "Fase 03 - Reduccion de retraso",
            s"""{"column":"$columnLabel","reduction":"$reductionLabel"}""",
            s"$rawText; validos=$validCount",
            items,
            1,
            sentItems,
            sentItems < 1
          )
        )
      case None =>
        println("No hay valores validos para la Fase 03.")
        None
    }
  }

  /**
   * Reduce recursivamente los retrasos seleccionados.
   *
   * Reemplaza la reduccion atomica de CUDA por un acumulador `bestValue`. Cada
   * vuelo aporta como mucho un valor; si la columna esta ausente, se ignora y no
   * aumenta `validCount`.
   *
   * @param flights vuelos pendientes.
   * @param columnOption columna que se debe leer de cada vuelo.
   * @param isMax `true` para maximo, `false` para minimo.
   * @param bestValue mejor valor acumulado hasta ahora, o `None` si no hay ninguno.
   * @param validCount numero de valores no vacios usados.
   * @return estado final con resultado y conteo, o `None` si no habia valores.
   */
  @tailrec
  private def reduceDelayValues(
      flights: List[Flight],
      columnOption: Int,
      isMax: Boolean,
      bestValue: Option[Int],
      validCount: Int
  ): Option[ReductionState] = {
    // Sustituye la reduccion atomica simple de CUDA por un acumulador recursivo de cola.
    // `bestValue` guarda el maximo/minimo actual y `validCount` ignora los valores ausentes para el conteo final.
    flights match {
      case Nil =>
        bestValue match {
          case Some(value) => Some(ReductionState(value, validCount))
          case None        => None
        }
      case flight :: tail =>
        selectedDelay(flight, columnOption) match {
          case Some(value) =>
            // Si ya existe mejor valor, se compara; si no, el primer valor valido inicia la reduccion.
            val nextBest = bestValue match {
              case Some(current) => Some(compareReduction(current, value, isMax))
              case None          => Some(value)
            }
            reduceDelayValues(tail, columnOption, isMax, nextBest, validCount + 1)
          case None =>
            // Los None no participan en max/min y tampoco cuentan como validos.
            reduceDelayValues(tail, columnOption, isMax, bestValue, validCount)
        }
    }
  }

  /**
   * Extrae de un vuelo la columna de retraso elegida.
   *
   * @param flight vuelo que se esta procesando.
   * @param columnOption opcion de columna del menu.
   * @return valor opcional de la columna seleccionada.
   */
  private def selectedDelay(flight: Flight, columnOption: Int): Option[Int] = {
    // Devuelve la columna elegida manteniendo los valores vacios como None.
    columnOption match {
      case 1 => flight.departureDelay
      case 2 => flight.arrivalDelay
      case 3 => flight.weatherDelay
      case _ => None
    }
  }

  /**
   * Convierte una opcion de columna en su nombre de dataset.
   *
   * @param columnOption numero elegido en el menu.
   * @return nombre de columna usado en consola y JSON.
   */
  private def columnName(columnOption: Int): String = {
    columnOption match {
      case 1 => "DEP_DELAY"
      case 2 => "ARR_DELAY"
      case 3 => "WEATHER_DELAY"
      case _ => "DESCONOCIDA"
    }
  }

  /**
   * Compara dos valores segun el tipo de reduccion.
   *
   * @param left mejor valor acumulado.
   * @param right nuevo valor encontrado.
   * @param isMax `true` para quedarse con el mayor, `false` para el menor.
   * @return valor que debe continuar como mejor acumulado.
   */
  private def compareReduction(left: Int, right: Int, isMax: Boolean): Int = {
    // Equivalente funcional de atomicMax/atomicMin para dos valores.
    if (isMax) {
      if (left > right) left else right
    } else {
      if (left < right) left else right
    }
  }

  /**
   * Resultado interno de la reduccion.
   *
   * @param value maximo o minimo calculado.
   * @param validCount cantidad de datos reales usados para obtenerlo.
   */
  private final case class ReductionState(value: Int, validCount: Int)
}
