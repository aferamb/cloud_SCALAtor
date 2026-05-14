import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.util.Try

/**
 * Funciones auxiliares compartidas por el menu, el lector CSV y las fases.
 *
 * Este archivo concentra operaciones pequenas que se reutilizan en varios
 * puntos del programa: lectura validada de consola, conteo/seleccion manual de
 * listas y reconstruccion de listas acumuladas por recursividad. Varias
 * funciones existen para respetar las restricciones funcionales de la practica,
 * evitando `length`, `reverse`, indexacion directa, bucles y colecciones
 * mutables.
 */
object AppUtils {
  /**
   * Elimina espacios al principio y al final de un texto.
   *
   * @param text texto de entrada.
   * @return el mismo texto sin espacios exteriores.
   */
  def trim(text: String): String = text.trim

  /**
   * Lee por consola un umbral entero que puede ser positivo o negativo.
   *
   * La funcion se llama a si misma cuando el usuario introduce un valor no
   * entero. Si se introduce `X` o `x`, se cancela la operacion sin lanzar
   * excepciones y se devuelve `None`.
   *
   * @param prompt texto que se muestra antes de leer la entrada.
   * @return `Some(valor)` si la entrada es un entero, o `None` si se cancela.
   */
  @tailrec
  def readSignedThreshold(prompt: String): Option[Int] = {
    print(prompt)
    val input = trim(readLine())
    input match {
      case "X" | "x" => None
      case _ =>
        // `Try` evita que una entrada no numerica interrumpa la aplicacion.
        Try(input.toInt).toOption match {
          case Some(value) => Some(value)
          case None =>
            println("Debe introducir un numero entero, o X.")
            // Recursion de cola: se repite la lectura hasta recibir un valor valido.
            readSignedThreshold(prompt)
        }
    }
  }

  /**
   * Pausa la ejecucion hasta que el usuario pulse Intro.
   *
   * @return no devuelve un valor util; solo produce el efecto de esperar entrada.
   */
  def pauseForEnter(): Unit = {
    println()
    print("Pulse Intro para continuar...")
    readLine()
  }

  /**
   * Lee por consola un entero dentro de un intervalo cerrado.
   *
   * Se reutiliza en Fase 03 y Fase 04 para validar submenus. Si el usuario
   * introduce `X` o `x`, se devuelve `None`; si introduce un valor fuera de
   * rango o no numerico, se muestra el error y se reintenta por recursion.
   *
   * @param prompt texto mostrado antes de leer.
   * @param errorMessage mensaje que se imprime cuando la entrada no es valida.
   * @param minValue valor minimo aceptado, incluido.
   * @param maxValue valor maximo aceptado, incluido.
   * @return `Some(valor)` si la opcion es valida, o `None` si se cancela.
   */
  @tailrec
  def readIntegerInRange(prompt: String, errorMessage: String, minValue: Int, maxValue: Int): Option[Int] = {
    print(prompt)
    val input = trim(readLine())
    input match {
      case "X" | "x" => None
      case _ =>
        // Se convierte de forma segura y se valida el rango en la misma rama.
        Try(input.toInt).toOption match {
          case Some(value) if value >= minValue && value <= maxValue => Some(value)
          case _ =>
            println(errorMessage)
            // No hay bucle `while`: el reintento es una llamada final optimizable.
            readIntegerInRange(prompt, errorMessage, minValue, maxValue)
        }
    }
  }

  /**
   * Comprueba si un retraso cumple el umbral introducido por el usuario.
   *
   * Con umbrales positivos se buscan retrasos mayores o iguales. Con umbrales
   * negativos se buscan adelantos menores o iguales.
   *
   * @param value retraso o adelanto de un vuelo.
   * @param threshold umbral elegido por el usuario.
   * @return `true` si el valor cumple la comparacion correspondiente.
   */
  def matchesSignedThreshold(value: Int, threshold: Int): Boolean = {
    if (threshold >= 0) value >= threshold else value <= threshold
  }

  /**
   * Cuenta los elementos de una lista sin usar `length`.
   *
   * @param fields lista que se quiere contar.
   * @param acc acumulador interno con el numero de elementos ya recorridos.
   * @return numero total de elementos de `fields`.
   */
  @tailrec
  def countFields(fields: List[String], acc: Int = 0): Int = {
    fields match {
      case Nil       => acc
      case _ :: tail =>
        // Se consume un nodo de la lista y se incrementa el acumulador.
        countFields(tail, acc + 1)
    }
  }

  /**
   * Obtiene el campo situado en una posicion sin usar indexacion directa.
   *
   * Si la posicion no existe, devuelve cadena vacia. Esto permite que el parseo
   * del CSV falle de forma controlada en vez de lanzar una excepcion.
   *
   * @param fields lista de campos de una fila CSV.
   * @param index posicion deseada, empezando en cero.
   * @return campo encontrado o `""` si la lista termina antes.
   */
  @tailrec
  def fieldAt(fields: List[String], index: Int): String = {
    fields match {
      case Nil => ""
      case head :: tail =>
        // Cada llamada descarta la cabeza y reduce el indice hasta llegar a cero.
        if (index == 0) head else fieldAt(tail, index - 1)
    }
  }

  /**
   * Restaura el orden original de una lista de vuelos acumulada al reves.
   *
   * Durante el parseo se inserta cada vuelo con `flight :: acc` por eficiencia,
   * lo que invierte el orden. Esta funcion reconstruye el orden correcto sin
   * usar `reverse`: mueve recursivamente cada cabeza de `values` al acumulador.
   *
   * @param values lista acumulada en orden inverso.
   * @param acc acumulador que va recibiendo los elementos en orden restaurado.
   * @return lista de vuelos en el orden original del CSV.
   */
  @tailrec
  def restoreFlightsOrder(values: List[Flight], acc: List[Flight]): List[Flight] = {
    values match {
      case Nil          => acc
      case head :: tail =>
        // Equivalente a invertir la lista, pero implementado con recursion propia.
        restoreFlightsOrder(tail, head :: acc)
    }
  }

  /**
   * Restaura el orden original de los items Cloud acumulados por cabeza.
   *
   * Las fases guardan cada nuevo item como primera posicion de la lista para no
   * recorrerla al insertar. Antes de construir el `PhaseResult`, esta funcion
   * devuelve los items en el orden en que se imprimieron por consola.
   *
   * @param values items acumulados en orden inverso.
   * @param acc acumulador de salida.
   * @return items en orden natural de ejecucion.
   */
  @tailrec
  def restoreResultItemsOrder(values: List[CloudResultItem], acc: List[CloudResultItem]): List[CloudResultItem] = {
    values match {
      case Nil          => acc
      case head :: tail =>
        restoreResultItemsOrder(tail, head :: acc)
    }
  }

  /**
   * Lee una respuesta si/no desde consola.
   *
   * Acepta variantes habituales en espanol (`S`, `SI`, `N`, `NO`) y aplica un
   * valor por defecto si el usuario pulsa Intro sin escribir nada.
   *
   * @param prompt texto que se muestra al usuario.
   * @param defaultValue valor devuelto cuando la entrada esta vacia.
   * @return `true` para si, `false` para no.
   */
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
        // Reintento recursivo hasta recibir una respuesta reconocible.
        readYesNo(prompt, defaultValue)
    }
  }

  /**
   * Inserta un texto al final de una lista conservando el orden.
   *
   * Se usa en el separador CSV para ir anadiendo tokens sin utilizar
   * concatenacion de listas (`++` o `:::`). La funcion reconstruye el prefijo
   * hasta llegar al final y coloca ahi el nuevo valor.
   *
   * @param values lista existente.
   * @param value nuevo elemento que debe quedar al final.
   * @return nueva lista con `value` como ultimo elemento.
   */
  def appendString(values: List[String], value: String): List[String] = {
    values match {
      case Nil          => value :: Nil
      case head :: tail =>
        // Se conserva la cabeza y se inserta al final del resto de la lista.
        head :: appendString(tail, value)
    }
  }

  /**
   * Convierte una lista de caracteres en `String` mediante recursion.
   *
   * @param chars caracteres que forman el texto.
   * @return cadena resultante en el mismo orden que la lista.
   */
  def charsToString(chars: List[Char]): String = {
    chars match {
      case Nil          => ""
      case head :: tail =>
        // Se concatena el caracter actual con el texto reconstruido del sufijo.
        head.toString + charsToString(tail)
    }
  }
}
