import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.util.Try

object AppUtils {
  def trim(text: String): String = text.trim

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

  def matchesSignedThreshold(value: Int, threshold: Int): Boolean = {
    if (threshold >= 0) value >= threshold else value <= threshold
  }

  @tailrec
  def countFields(fields: List[String], acc: Int = 0): Int = {
    fields match {
      case Nil       => acc
      case _ :: tail => countFields(tail, acc + 1)
    }
  }

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

  def appendString(values: List[String], value: String): List[String] = {
    values match {
      case Nil          => value :: Nil
      case head :: tail => head :: appendString(tail, value)
    }
  }

  def charsToString(chars: List[Char]): String = {
    chars match {
      case Nil          => ""
      case head :: tail => head.toString + charsToString(tail)
    }
  }
}
