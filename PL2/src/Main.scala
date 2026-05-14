import scala.annotation.tailrec
import scala.io.StdIn.readLine

/**
 * Punto de entrada y coordinador de la aplicacion de consola.
 *
 * Este archivo no implementa los calculos de las fases; se encarga de arrancar
 * la aplicacion, cargar configuracion y dataset, mostrar el menu, llamar a la
 * fase elegida y ofrecer la subida del resultado a Cloud.
 */
object Main {
  /**
   * Arranca la aplicacion.
   *
   * Muestra la cabecera, carga la configuracion Cloud, solicita el CSV inicial y
   * entra en el menu si el dataset se ha cargado correctamente.
   *
   * @param args argumentos de linea de comandos, no usados por esta practica.
   * @return no devuelve valor; controla la ejecucion interactiva completa.
   */
  def main(args: Array[String]): Unit = {
    println("========================================")
    println(" PL2 Scala - US Airline Dataset Toolkit")
    println("========================================")
    println("Version actual: carga CSV + menu + Fases 01 a 04 + envio Cloud")

    val cloudConfig = CloudConfigReader.loadAndConfirm()

    promptAndLoadDataset(None) match {
      case Some(dataset) => menuLoop(dataset, None, cloudConfig)
      case None          => println("Saliendo sin cargar dataset.")
    }
  }

  /**
   * Mantiene activo el menu principal mediante recursividad de cola.
   *
   * Cada opcion ejecuta una accion y vuelve a llamar a `menuLoop` con el estado
   * actualizado: mismo dataset, nuevo dataset o ultimo resultado de fase. La
   * opcion `X` es el unico caso que no hace llamada recursiva y por tanto sale.
   *
   * @param dataset dataset actualmente cargado.
   * @param lastResult ultimo resultado generado por una fase, si existe.
   * @param cloudConfig configuracion usada para ofrecer subidas Cloud.
   * @return no devuelve valor; imprime y lee desde consola.
   */
  @tailrec
  private def menuLoop(dataset: Dataset, lastResult: Option[PhaseResult], cloudConfig: CloudConfig): Unit = {
    printMenu(dataset)
    val option = AppUtils.trim(readLine("> "))

    option match {
      case "1" =>
        // Cada fase devuelve `None` si el usuario cancela desde su submenu.
        Phase01.run(dataset, cloudConfig.itemLimit) match {
          case Some(result) =>
            offerCloudUpload(dataset, result, cloudConfig)
            menuLoop(dataset, Some(result), cloudConfig)
          case None =>
            menuLoop(dataset, lastResult, cloudConfig)
        }
      case "2" =>
        Phase02.run(dataset, cloudConfig.itemLimit) match {
          case Some(result) =>
            offerCloudUpload(dataset, result, cloudConfig)
            menuLoop(dataset, Some(result), cloudConfig)
          case None =>
            menuLoop(dataset, lastResult, cloudConfig)
        }
      case "3" =>
        Phase03.run(dataset, cloudConfig.itemLimit) match {
          case Some(result) =>
            offerCloudUpload(dataset, result, cloudConfig)
            menuLoop(dataset, Some(result), cloudConfig)
          case None =>
            menuLoop(dataset, lastResult, cloudConfig)
        }
      case "4" =>
        Phase04.run(dataset, cloudConfig.itemLimit) match {
          case Some(result) =>
            offerCloudUpload(dataset, result, cloudConfig)
            menuLoop(dataset, Some(result), cloudConfig)
          case None =>
            menuLoop(dataset, lastResult, cloudConfig)
        }
      case "R" | "r" =>
        // Al recargar se conserva `lastResult`; solo cambia el dataset si la carga termina bien.
        promptAndLoadDataset(Some(dataset.path)) match {
          case Some(newDataset) => menuLoop(newDataset, lastResult, cloudConfig)
          case None             => menuLoop(dataset, lastResult, cloudConfig)
        }
      case "I" | "i" =>
        printApplicationState(dataset, cloudConfig, lastResult)
        AppUtils.pauseForEnter()
        menuLoop(dataset, lastResult, cloudConfig)
      case "X" | "x" =>
        println("Aplicacion finalizada.")
      case "" =>
        // Evita tratar como error un Intro sobrante despues de pausar una fase.
        menuLoop(dataset, lastResult, cloudConfig)
      case _ =>
        println("Opcion no valida.")
        menuLoop(dataset, lastResult, cloudConfig)
    }
  }

  /**
   * Imprime las opciones disponibles del menu principal.
   *
   * @param dataset dataset actual, usado para mostrar la ruta activa.
   * @return no devuelve valor; solo escribe por consola.
   */
  private def printMenu(dataset: Dataset): Unit = {
    println()
    println("Menu principal")
    println("1. Fase 01 - Retraso en salida")
    println("2. Fase 02 - Retraso en llegada")
    println("3. Fase 03 - Reduccion de retraso")
    println("4. Fase 04 - Histograma de aeropuertos")
    println("R. Recargar CSV")
    println("I. Ver estado de la aplicacion")
    println("X. Salir")
    println(s"CSV actual: ${dataset.path}")
  }

  /**
   * Pregunta si se debe subir a Cloud el resultado de una fase.
   *
   * Muestra cuantos items se enviaran, avisa si hubo truncado por limite y, si
   * el usuario acepta, delega el `POST` en `CloudApiClient`.
   *
   * @param dataset dataset usado al ejecutar la fase.
   * @param result resultado estructurado que se puede enviar.
   * @param cloudConfig configuracion de endpoint, usuario y limite.
   * @return no devuelve valor; puede producir un envio HTTP y siempre pausa al final.
   */
  private def offerCloudUpload(dataset: Dataset, result: PhaseResult, cloudConfig: CloudConfig): Unit = {
    println()
    println(s"Items para enviar: ${result.sentItemCount} de ${result.totalItemCount}")
    if (result.itemsTruncated) {
      println(s"Se enviaran solo los primeros ${result.sentItemCount} items por el limite configurado.")
    }

    if (AppUtils.readYesNo("Enviar este resultado a la API? [S/n]: ", true)) {
      // El cliente devuelve Either para imprimir exito o error sin lanzar excepciones.
      CloudApiClient.postResult(cloudConfig, dataset, result) match {
        case Right(body) =>
          println("Resultado enviado correctamente.")
          println(s"Respuesta API: $body")
        case Left(error) =>
          println(error)
      }
    }

    AppUtils.pauseForEnter()
  }

  /**
   * Solicita la ruta del CSV y reintenta hasta cargarlo o cancelar.
   *
   * Si existe una ruta por defecto se permite pulsar Intro para usarla. Si la
   * carga falla, se imprime el error y la funcion vuelve a preguntarse a si
   * misma por recursion de cola.
   *
   * @param defaultPath ruta sugerida, normalmente el dataset actual al recargar.
   * @return `Some(Dataset)` si se carga correctamente, o `None` si se cancela.
   */
  @tailrec
  private def promptAndLoadDataset(defaultPath: Option[String]): Option[Dataset] = {
    val detectedDefault = defaultPath match {
      case Some(path) => Some(path)
      case None =>
        // Cuando se arranca desde `PL2`, esta ruta permite cargar el CSV de ejemplo con Intro.
        val localPath = "data/Airline_dataset.csv"
        if (new java.io.File(localPath).exists()) Some(localPath) else None
    }

    detectedDefault match {
      case Some(path) => println(s"\nRuta del CSV [Intro = $path] [X = salir/volver]")
      case None       => println("\nRuta del CSV [X = salir/volver]")
    }

    val input = AppUtils.trim(readLine("> "))

    input match {
      case "X" | "x" => None
      case "" =>
        detectedDefault match {
          case Some(path) =>
            println("Cargando datos.........")
            // `CsvReader` encapsula los errores de fichero o parseo en un Either.
            CsvReader.loadDataset(path) match {
              case Right(dataset) => Some(dataset)
              case Left(error) =>
                println(error)
                promptAndLoadDataset(defaultPath)
            }
          case None =>
            println("Debe indicar una ruta valida.")
            promptAndLoadDataset(defaultPath)
        }
      case path =>
        CsvReader.loadDataset(path) match {
          case Right(dataset) => Some(dataset)
          case Left(error) =>
            println(error)
            promptAndLoadDataset(defaultPath)
        }
    }
  }

  /**
   * Muestra el estado actual de la aplicacion.
   *
   * Incluye estadisticas de carga del CSV, configuracion Cloud y resumen de la
   * ultima fase ejecutada para que el usuario sepa con que datos esta trabajando.
   *
   * @param dataset dataset actualmente activo.
   * @param cloudConfig configuracion Cloud activa.
   * @param lastResult ultimo resultado de fase, si ya se ejecuto alguna.
   * @return no devuelve valor; escribe el estado por consola.
   */
  private def printApplicationState(
      dataset: Dataset,
      cloudConfig: CloudConfig,
      lastResult: Option[PhaseResult]
  ): Unit = {
    val summary = dataset.summary
    println()
    println("Estado de la aplicacion")
    println(s"Ruta: ${dataset.path}")
    println(s"Filas leidas: ${summary.rowsRead}")
    println(s"Filas almacenadas: ${summary.storedRows}")
    println(s"Filas descartadas: ${summary.discardedRows}")
    println(s"DEP_DELAY faltantes: ${summary.missingDepartureDelay}")
    println(s"ARR_DELAY faltantes: ${summary.missingArrivalDelay}")
    println(s"WEATHER_DELAY faltantes: ${summary.missingWeatherDelay}")
    println(s"API Cloud: ${cloudConfig.apiUrl}")
    println(s"Usuario Cloud: ${cloudConfig.userName}")
    println(s"Limite de items Cloud: ${cloudConfig.itemLimit}")
    lastResult match {
      case Some(result) =>
        println(s"Ultima fase: ${result.phaseName}")
        println(s"Ultimo resumen: ${result.resultSummary}")
      case None =>
        println("Ultima fase: ninguna")
    }
  }
}
