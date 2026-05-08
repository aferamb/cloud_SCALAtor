# cloud_SCALAtor

`cloud_SCALAtor` es una aplicacion de consola escrita en Scala para la PL2 de
Paradigmas Avanzados de Programacion. Analiza el dataset "US Airline Dataset"
mediante una adaptacion funcional de las fases obligatorias de la PL1 CUDA.

La parte Scala local esta implementada. La integracion Cloud indicada en el
enunciado de PL2 queda pendiente por ahora: no hay todavia API, envio HTTP,
base de datos ni visor web desplegado.

## Estado

Funcionalidad disponible:

- carga interactiva de un CSV local;
- deteccion de ruta por defecto en `data/Airline_dataset.csv`;
- menu principal con ejecucion de fases, recarga de CSV, consulta de estado y
  salida;
- Fase 01: retrasos o adelantos en salida usando `DEP_DELAY`;
- Fase 02: retrasos o adelantos en llegada usando `ARR_DELAY` y `TAIL_NUM`;
- Fase 03: reduccion simple de maximo o minimo sobre `DEP_DELAY`, `ARR_DELAY`
  o `WEATHER_DELAY`;
- Fase 04: histograma textual de aeropuertos de origen o destino;
- resumen compacto de cada fase mediante `PhaseResult`, preparado como base
  para la futura conexion Cloud.

Restricciones funcionales revisadas en `PL2/src`:

- se usan `val`, modelos inmutables y `Option` para valores ausentes;
- los recorridos principales se hacen con recursividad de cola;
- no hay usos evidentes de `var`, bucles `for`/`while`, colecciones mutables,
  `filter`, `flatMap`, `flatten`, `reverse`, `length`, `last`, `isEmpty`,
  `++` ni `:::`;
- las operaciones CUDA de hilos, atomicas y memoria compartida se sustituyen por
  recorridos recursivos y acumuladores, como pide la adaptacion Scala.

## Estructura

```text
.
├── README.md
├── Enunciado_PL2_v1.md
├── PAP_Enunciado_PL1_v1.md
├── NOTES_IMPORTANT.md
└── PL2
    ├── TODO.txt
    ├── cloud_SCALAtor.iml
    ├── data
    │   └── Airline_dataset.csv
    └── src
        ├── AppUtils.scala
        ├── CsvReader.scala
        ├── Main.scala
        ├── Models.scala
        ├── Phase01.scala
        ├── Phase02.scala
        ├── Phase03.scala
        └── Phase04.scala
```

Responsabilidades principales:

- `Main.scala`: punto de entrada, carga inicial del CSV, menu principal,
  recarga y estado de la aplicacion.
- `Models.scala`: modelos inmutables `Flight`, `LoadSummary`, `Dataset` y
  `PhaseResult`.
- `CsvReader.scala`: apertura del fichero, lectura de cabecera, parseo CSV,
  conversion de campos y resumen de carga.
- `AppUtils.scala`: utilidades comunes de consola, validacion y recorridos
  propios sobre listas.
- `Phase01.scala` a `Phase04.scala`: implementacion de las cuatro fases
  obligatorias.

## Dataset esperado

El lector CSV usa posiciones fijas de columnas y salta la cabecera:

| Indice | Campo usado |
| --- | --- |
| 0 | Identificador de fila usado como `id` |
| 3 | `TAIL_NUM` |
| 5 | `ORIGIN_SEQ_ID` |
| 6 | `ORIGIN_AIRPORT` |
| 7 | `DEST_SEQ_ID` |
| 8 | `DEST_AIRPORT` |
| 10 | `DEP_DELAY` |
| 12 | `ARR_DELAY` |
| 13 | `WEATHER_DELAY` |

Cada fila necesita al menos 14 columnas. Las filas incompletas se descartan. Los
campos numericos vacios o invalidos se guardan como `None`, por lo que no
participan en filtros ni reducciones.

## Menu

Al arrancar, la aplicacion solicita la ruta del CSV. Si encuentra una ruta por
defecto, se puede pulsar Intro para usarla. Si la ruta no existe o no se puede
leer, se muestra un error y se vuelve a pedir.

```text
Menu principal
1. Fase 01 - Retraso en salida
2. Fase 02 - Retraso en llegada
3. Fase 03 - Reduccion de retraso
4. Fase 04 - Histograma de aeropuertos
R. Recargar CSV
I. Ver estado de la aplicacion
X. Salir
CSV actual: <ruta>
```

Las opciones de fase vuelven al menu al terminar. En los menus internos se puede
usar `X` para cancelar y volver.

## Fases

### Fase 01 - Retraso en salida

Entrada: umbral entero, positivo, cero o negativo.

Reglas:

- si el umbral es positivo o cero, muestra vuelos con `DEP_DELAY >= umbral`;
- si el umbral es negativo, muestra vuelos con `DEP_DELAY <= umbral`;
- los valores ausentes se ignoran.

Salida: identificador del dataset, etiqueta de retraso/adelanto, minutos y total
de coincidencias.

### Fase 02 - Retraso en llegada

Entrada: umbral entero, positivo, cero o negativo.

Reglas:

- si el umbral es positivo o cero, muestra vuelos con `ARR_DELAY >= umbral`;
- si el umbral es negativo, muestra vuelos con `ARR_DELAY <= umbral`;
- los valores ausentes se ignoran.

Salida: identificador del dataset, matricula `TAIL_NUM`, etiqueta de
retraso/adelanto, minutos y total de aviones encontrados.

### Fase 03 - Reduccion de retraso

Entrada:

- columna: `DEP_DELAY`, `ARR_DELAY` o `WEATHER_DELAY`;
- reduccion: maximo o minimo.

Solo implementa la variante `[3.1 Simple]` exigida por PL2. Los valores
decimales del CSV se truncan a entero durante el parseo, siguiendo la indicacion
de PL1.

### Fase 04 - Histograma de aeropuertos

Entrada:

- tipo de aeropuerto: origen o destino;
- umbral minimo de visualizacion, mayor o igual que cero.

Agrupa por identificador secuencial de aeropuerto, conserva el codigo para
imprimirlo y escala barras de texto con `#` hasta 40 caracteres.

## Ejecucion

La forma principal de uso es abrir `PL2` como proyecto de IntelliJ IDEA con un
SDK de Scala configurado. El modulo `PL2/cloud_SCALAtor.iml` marca `PL2/src`
como carpeta de fuentes.

Pasos recomendados:

1. Abrir la carpeta `PL2` en IntelliJ IDEA.
2. Configurar JDK y Scala SDK si IntelliJ lo solicita.
3. Ejecutar el objeto `Main`.
4. Pulsar Intro para usar una ruta detectada o introducir la ruta del CSV.

Si se dispone de `scalac` y `scala` instalados localmente, tambien se puede
ejecutar desde la raiz:

```bash
mkdir -p PL2/out
scalac -d PL2/out PL2/src/*.scala
scala -cp PL2/out Main
```

O desde `PL2`:

```bash
mkdir -p out
scalac -d out src/*.scala
scala -cp out Main
```

## Pendiente Cloud

Segun `Enunciado_PL2_v1.md` y `NOTES_IMPORTANT.md`, la entrega completa debera
anadir una aplicacion web en Azure App Service con API y un visor HTML. El Scala
local debera enviar resultados a esa API, incluyendo fase ejecutada, opciones de
entrada, resultado, usuario y fecha/hora.

Ese bloque no esta implementado todavia.
