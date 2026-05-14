import scala.annotation.tailrec
import scala.io.StdIn.readLine

object Main {
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

  // Mantiene el menu activo mediante recursividad de cola.
  @tailrec
  private def menuLoop(dataset: Dataset, lastResult: Option[PhaseResult], cloudConfig: CloudConfig): Unit = {
    printMenu(dataset)
    val option = AppUtils.trim(readLine("> "))

    option match {
      case "1" =>
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

  private def offerCloudUpload(dataset: Dataset, result: PhaseResult, cloudConfig: CloudConfig): Unit = {
    println()
    println(s"Items para enviar: ${result.sentItemCount} de ${result.totalItemCount}")
    if (result.itemsTruncated) {
      println(s"Se enviaran solo los primeros ${result.sentItemCount} items por el limite configurado.")
    }

    if (AppUtils.readYesNo("Enviar este resultado a la API? [S/n]: ", true)) {
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

  // Solicita la ruta del CSV, ofrece una ruta local por defecto y reintenta si falla.
  @tailrec
  private def promptAndLoadDataset(defaultPath: Option[String]): Option[Dataset] = {
    val detectedDefault = defaultPath match {
      case Some(path) => Some(path)
      case None =>
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

  // Muestra las estadisticas basicas de la carga actual.
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
