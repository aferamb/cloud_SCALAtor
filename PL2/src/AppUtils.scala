import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.util.Try

object AppUtils {
  def trim(text: String): String = text.trim

  // Lee un umbral entero y permite cancelar con X sin lanzar excepciones.
  @tailrec
  def readSignedThreshold(prompt: String): Option[Int] = {
    print(prompt)
    val input = trim(readLine())
    input match {
      case "X" | "x" => None
      case _ =>
        Try(input.toInt).toOption match {
          case Some(value) => Some(value)
          case None =>
            println("Debe introducir un numero entero, o X.")
            readSignedThreshold(prompt)
        }
    }
  }

  def pauseForEnter(): Unit = {
    println()
    print("Pulse Intro para continuar...")
    readLine()
  }

  // Lee una opcion entera acotada sin usar bucles, permitiendo cancelar con X.
  // Se reutiliza en Fase 03 y Fase 04 para validar menus internos.
  @tailrec
  def readIntegerInRange(prompt: String, errorMessage: String, minValue: Int, maxValue: Int): Option[Int] = {
    print(prompt)
    val input = trim(readLine())
    input match {
      case "X" | "x" => None
      case _ =>
        Try(input.toInt).toOption match {
          case Some(value) if value >= minValue && value <= maxValue => Some(value)
          case _ =>
            println(errorMessage)
            readIntegerInRange(prompt, errorMessage, minValue, maxValue)
        }
    }
  }

  def matchesSignedThreshold(value: Int, threshold: Int): Boolean = {
    if (threshold >= 0) value >= threshold else value <= threshold
  }

  // Cuenta elementos sin usar length de la libreria.
  @tailrec
  def countFields(fields: List[String], acc: Int = 0): Int = {
    fields match {
      case Nil       => acc
      case _ :: tail => countFields(tail, acc + 1)
    }
  }

  // Obtiene una posicion de una lista sin indexacion directa.
  @tailrec
  def fieldAt(fields: List[String], index: Int): String = {
    fields match {
      case Nil => ""
      case head :: tail =>
        if (index == 0) head else fieldAt(tail, index - 1)
    }
  }

  @tailrec
  def restoreFlightsOrder(values: List[Flight], acc: List[Flight]): List[Flight] = {
    // Reordena una lista acumulada por cabeza sin usar reverse de la libreria.
    values match {
      case Nil          => acc
      case head :: tail => restoreFlightsOrder(tail, head :: acc)
    }
  }

  @tailrec
  def restoreResultItemsOrder(values: List[CloudResultItem], acc: List[CloudResultItem]): List[CloudResultItem] = {
    values match {
      case Nil          => acc
      case head :: tail => restoreResultItemsOrder(tail, head :: acc)
    }
  }

  @tailrec
  def readYesNo(prompt: String, defaultValue: Boolean): Boolean = {
    print(prompt)
    val input = trim(readLine())
    input match {
      case ""      => defaultValue
      case "S" | "s" | "SI" | "Si" | "si" => true
      case "N" | "n" | "NO" | "No" | "no" => false
      case _ =>
        println("Debe responder S o N.")
        readYesNo(prompt, defaultValue)
    }
  }

  // Inserta al final conservando el orden sin usar concatenacion de listas.
  def appendString(values: List[String], value: String): List[String] = {
    values match {
      case Nil          => value :: Nil
      case head :: tail => head :: appendString(tail, value)
    }
  }

  // Reconstruye texto desde caracteres sin depender de utilidades prohibidas.
  def charsToString(chars: List[Char]): String = {
    chars match {
      case Nil          => ""
      case head :: tail => head.toString + charsToString(tail)
    }
  }
}
