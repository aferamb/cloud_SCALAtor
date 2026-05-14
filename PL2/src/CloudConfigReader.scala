import scala.annotation.tailrec
import scala.io.Source
import scala.io.StdIn.readLine
import scala.util.Try

/**
 * Lector y confirmador interactivo de la configuracion Cloud.
 *
 * Este archivo se encarga de localizar `cloud-api.properties`, leer sus claves
 * conocidas y pedir confirmacion al usuario antes de que `Main` empiece a
 * ejecutar fases. La configuracion resultante se guarda en un `CloudConfig`
 * inmutable que se pasa al cliente HTTP.
 */
object CloudConfigReader {
  private val ConfigFileName = "cloud-api.properties"
  private val MaxItemLimit = 1000000
  private val DefaultConfig = CloudConfig(
    apiUrl = "http://localhost:3000/api/results",
    userName = "alumno.demo",
    itemLimit = 5000
  )

  /**
   * Carga la configuracion Cloud y permite modificarla desde consola.
   *
   * Primero resuelve que fichero de propiedades debe usar, luego intenta leerlo
   * y finalmente pregunta al usuario si quiere mantener o cambiar URL, usuario
   * y limite de items.
   *
   * @return configuracion definitiva que se usara para los envios Cloud.
   */
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

  /**
   * Decide la ruta del fichero `cloud-api.properties`.
   *
   * Si el programa se ejecuta desde dentro de `PL2`, el fichero esta en el
   * directorio actual. Si se ejecuta desde la raiz del repositorio, se busca en
   * `PL2/cloud-api.properties`.
   *
   * @return objeto `File` con la ruta que debe intentarse leer.
   */
  private def resolveConfigFile(): java.io.File = {
    val localFile = new java.io.File(ConfigFileName)
    val localSrc = new java.io.File("src")
    if (localFile.exists() || localSrc.exists()) {
      localFile
    } else {
      new java.io.File("PL2/" + ConfigFileName)
    }
  }

  /**
   * Lee el fichero de propiedades o devuelve valores por defecto.
   *
   * Los errores de lectura se capturan para que el programa pueda continuar con
   * `DefaultConfig` en vez de finalizar. La funcion cierra siempre el `Source`
   * en el bloque `finally`.
   *
   * @param file fichero de propiedades que se quiere leer.
   * @return configuracion completa, ya sea leida o por defecto.
   */
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

  /**
   * Recorre recursivamente las lineas del fichero de configuracion.
   *
   * Cada linea actualiza una `PartialConfig`. Al usar recursion de cola no se
   * necesita un bucle `while` sobre el iterador.
   *
   * @param lines iterador de lineas del fichero.
   * @param config configuracion parcial acumulada hasta el momento.
   * @return configuracion parcial despues de procesar todas las lineas.
   */
  @tailrec
  private def parseLines(lines: Iterator[String], config: PartialConfig): PartialConfig = {
    if (lines.hasNext) {
      // Se procesa una linea y la configuracion resultante pasa a la siguiente llamada.
      parseLines(lines, parseLine(lines.next(), config))
    } else {
      config
    }
  }

  /**
   * Interpreta una linea individual de `cloud-api.properties`.
   *
   * Acepta solo claves conocidas (`api.url`, `user.name`, `items.limit`) e
   * ignora lineas vacias, comentarios, claves desconocidas o valores invalidos.
   *
   * @param line linea de texto original.
   * @param config configuracion parcial previa.
   * @return nueva configuracion parcial con la clave aplicada si era valida.
   */
  private def parseLine(line: String, config: PartialConfig): PartialConfig = {
    val trimmed = AppUtils.trim(line)
    if (trimmed == "" || trimmed.startsWith("#")) {
      config
    } else {
      // Se busca el primer `=` para separar clave y valor como en un properties simple.
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

  /**
   * Completa una configuracion parcial usando valores por defecto.
   *
   * @param config valores opcionales leidos desde fichero.
   * @return `CloudConfig` sin campos opcionales.
   */
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

  /**
   * Convierte y valida el limite de items.
   *
   * Solo acepta enteros entre `1` y `MaxItemLimit`; cualquier otro valor se
   * considera ausente para que el llamador pueda ignorarlo o reintentar.
   *
   * @param value texto leido del fichero o consola.
   * @return `Some(limite)` si es valido, o `None` si no lo es.
   */
  private def parseLimit(value: String): Option[Int] = {
    Try(value.toInt).toOption match {
      case Some(limit) if limit >= 1 && limit <= MaxItemLimit => Some(limit)
      case _                                                  => None
    }
  }

  /**
   * Pregunta por un campo textual manteniendo un valor por defecto.
   *
   * @param label nombre del campo que se muestra al usuario.
   * @param current valor actual que se conserva si se pulsa Intro.
   * @return nuevo texto introducido o `current` si la entrada queda vacia.
   */
  private def promptText(label: String, current: String): String = {
    println(s"$label actual: $current")
    print("Nuevo valor [Intro = mantener]: ")
    val input = AppUtils.trim(readLine())
    input match {
      case ""    => current
      case value => value
    }
  }

  /**
   * Pregunta por el limite de items hasta obtener un entero valido.
   *
   * @param current limite actual, usado como valor por defecto con Intro.
   * @return limite validado que se guardara en `CloudConfig`.
   */
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
            // Recursion de cola para repetir la pregunta sin mutar variables.
            promptItemLimit(current)
        }
    }
  }

  /**
   * Configuracion intermedia mientras se parsea el fichero.
   *
   * @param apiUrl URL opcional encontrada en `api.url`.
   * @param userName usuario opcional encontrado en `user.name`.
   * @param itemLimit limite opcional encontrado en `items.limit`.
   */
  private final case class PartialConfig(
      apiUrl: Option[String],
      userName: Option[String],
      itemLimit: Option[Int]
  )
}
