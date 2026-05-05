import scala.annotation.tailrec
import scala.io.StdIn.readLine

object Main {
  def main(args: Array[String]): Unit = {
    println("========================================")
    println(" PL2 Scala - US Airline Dataset Toolkit")
    println("========================================")
    println("Version actual: carga CSV + menu + Fases 01 y 02")

    promptAndLoadDataset(None) match {
      case Some(dataset) => menuLoop(dataset, None)
      case None          => println("Saliendo sin cargar dataset.")
    }
  }

  // Mantiene el menu activo mediante recursividad de cola.
  @tailrec
  private def menuLoop(dataset: Dataset, lastResult: Option[PhaseResult]): Unit = {
    printMenu(dataset)
    val option = AppUtils.trim(readLine("> "))

    option match {
      case "1" =>
        Phase01.run(dataset) match {
          case Some(result) =>
            AppUtils.pauseForEnter()
            menuLoop(dataset, Some(result))
          case None =>
            menuLoop(dataset, lastResult)
        }
      case "2" =>
        Phase02.run(dataset) match {
          case Some(result) =>
            AppUtils.pauseForEnter()
            menuLoop(dataset, Some(result))
          case None =>
            menuLoop(dataset, lastResult)
        }
      case "3" =>
        println("Fase 03 pendiente de implementar.")
        AppUtils.pauseForEnter()
        menuLoop(dataset, lastResult)
      case "4" =>
        println("Fase 04 pendiente de implementar.")
        AppUtils.pauseForEnter()
        menuLoop(dataset, lastResult)
      case "R" | "r" =>
        promptAndLoadDataset(Some(dataset.path)) match {
          case Some(newDataset) => menuLoop(newDataset, lastResult)
          case None             => menuLoop(dataset, lastResult)
        }
      case "I" | "i" =>
        printLoadSummary(dataset)
        AppUtils.pauseForEnter()
        menuLoop(dataset, lastResult)
      case "X" | "x" =>
        println("Aplicacion finalizada.")
      case _ =>
        println("Opcion no valida.")
        menuLoop(dataset, lastResult)
    }
  }

  private def printMenu(dataset: Dataset): Unit = {
    println()
    println("Menu principal")
    println("🕐. Fase 01 - Retraso en salida")
    println("🕑. Fase 02 - Retraso en llegada")
    println("🕒. Fase 03 - Reduccion de retraso")
    println("🕓. Fase 04 - Histograma de aeropuertos")
    println("R. Recargar CSV")
    println("I. Ver estado de la aplicacion")
    println("X. Salir")
    println(s"CSV actual: ${dataset.path}")
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
  private def printLoadSummary(dataset: Dataset): Unit = {
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
  }
}
