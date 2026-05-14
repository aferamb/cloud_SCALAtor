import scala.annotation.tailrec
import scala.io.Source
import scala.io.StdIn.readLine
import scala.util.Try

object CloudConfigReader {
  private val ConfigFileName = "cloud-api.properties"
  private val MaxItemLimit = 1000000
  private val DefaultConfig = CloudConfig(
    apiUrl = "http://localhost:3000/api/results",
    userName = "alumno.demo",
    itemLimit = 5000
  )

  def loadAndConfirm(): CloudConfig = {
    val file = resolveConfigFile()
    val current = readConfig(file)

    println()
    println("Configuracion Cloud")
    println(s"Archivo: ${file.getPath}")
    CloudConfig(
      apiUrl = promptText("URL de la API", current.apiUrl),
      userName = promptText("Usuario", current.userName),
      itemLimit = promptItemLimit(current.itemLimit)
    )
  }

  private def resolveConfigFile(): java.io.File = {
    val localFile = new java.io.File(ConfigFileName)
    val localSrc = new java.io.File("src")
    if (localFile.exists() || localSrc.exists()) {
      localFile
    } else {
      new java.io.File("PL2/" + ConfigFileName)
    }
  }

  private def readConfig(file: java.io.File): CloudConfig = {
    if (!file.exists()) {
      println(s"No se encontro ${file.getPath}. Se usaran valores por defecto.")
      DefaultConfig
    } else {
      try {
        val source = Source.fromFile(file)
        try {
          completeConfig(parseLines(source.getLines(), PartialConfig(None, None, None)))
        } finally {
          source.close()
        }
      } catch {
        case error: Exception =>
          println(s"No se pudo leer ${file.getPath}: ${error.getMessage}")
          DefaultConfig
      }
    }
  }

  @tailrec
  private def parseLines(lines: Iterator[String], config: PartialConfig): PartialConfig = {
    if (lines.hasNext) {
      parseLines(lines, parseLine(lines.next(), config))
    } else {
      config
    }
  }

  private def parseLine(line: String, config: PartialConfig): PartialConfig = {
    val trimmed = AppUtils.trim(line)
    if (trimmed == "" || trimmed.startsWith("#")) {
      config
    } else {
      val separator = trimmed.indexOf("=")
      if (separator <= 0) {
        config
      } else {
        val key = AppUtils.trim(trimmed.substring(0, separator))
        val value = AppUtils.trim(trimmed.substring(separator + 1))
        key match {
          case "api.url" =>
            if (value == "") config else config.copy(apiUrl = Some(value))
          case "user.name" =>
            if (value == "") config else config.copy(userName = Some(value))
          case "items.limit" =>
            parseLimit(value) match {
              case Some(limit) => config.copy(itemLimit = Some(limit))
              case None        => config
            }
          case _ =>
            config
        }
      }
    }
  }

  private def completeConfig(config: PartialConfig): CloudConfig = {
    CloudConfig(
      apiUrl = config.apiUrl match {
        case Some(value) => value
        case None        => DefaultConfig.apiUrl
      },
      userName = config.userName match {
        case Some(value) => value
        case None        => DefaultConfig.userName
      },
      itemLimit = config.itemLimit match {
        case Some(value) => value
        case None        => DefaultConfig.itemLimit
      }
    )
  }

  private def parseLimit(value: String): Option[Int] = {
    Try(value.toInt).toOption match {
      case Some(limit) if limit >= 1 && limit <= MaxItemLimit => Some(limit)
      case _                                                  => None
    }
  }

  private def promptText(label: String, current: String): String = {
    println(s"$label actual: $current")
    print("Nuevo valor [Intro = mantener]: ")
    val input = AppUtils.trim(readLine())
    input match {
      case ""    => current
      case value => value
    }
  }

  @tailrec
  private def promptItemLimit(current: Int): Int = {
    println(s"Limite de items actual: $current")
    print(s"Nuevo limite 1-$MaxItemLimit [Intro = mantener]: ")
    val input = AppUtils.trim(readLine())
    input match {
      case "" => current
      case _ =>
        parseLimit(input) match {
          case Some(limit) => limit
          case None =>
            println(s"Debe introducir un entero entre 1 y $MaxItemLimit.")
            promptItemLimit(current)
        }
    }
  }

  private final case class PartialConfig(
      apiUrl: Option[String],
      userName: Option[String],
      itemLimit: Option[Int]
  )
}
