# cloud_SCALAtor

`cloud_SCALAtor` es una aplicacion de consola escrita en Scala para la PL2 de
Paradigmas Avanzados de Programacion. Analiza el dataset "US Airline Dataset"
mediante una adaptacion funcional de las fases obligatorias de la PL1 CUDA.

La parte Scala local esta implementada y puede enviar resultados a la API Cloud
incluida en `cloud-api`. La API se despliega como App Service, guarda en Azure
SQL y sirve un visor HTML con los resultados recibidos.

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
- resultado estructurado de cada fase mediante `PhaseResult` e items
  normalizados para enviar al Cloud;
- configuracion local de URL, usuario y limite de items;
- envio HTTP opcional tras cada fase a `POST /api/results`.

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
        ├── CloudApiClient.scala
        ├── CloudConfigReader.scala
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
  configuracion Cloud, recarga, estado y subida opcional de resultados.
- `Models.scala`: modelos inmutables `Flight`, `LoadSummary`, `Dataset` y
  `PhaseResult`, ademas de los modelos Cloud.
- `CloudConfigReader.scala`: lee `cloud-api.properties` y permite cambiar los valores solo para la ejecucion actual.
- `CloudApiClient.scala`: serializa JSON y envia resultados con HTTP.
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

Al arrancar, la aplicacion carga `cloud-api.properties` si existe y pregunta si
se mantiene o cambia la URL de la API, el usuario y el limite de items. Despues
solicita la ruta del CSV. Si encuentra una ruta por defecto, se puede pulsar
Intro para usarla. Si la ruta no existe o no se puede leer, se muestra un error
y se vuelve a pedir.

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
usar `X` para cancelar y volver. Tras una fase completada, la aplicacion
pregunta si se quiere enviar ese resultado al Cloud.

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
4. Confirmar o cambiar la configuracion Cloud.
5. Pulsar Intro para usar una ruta detectada o introducir la ruta del CSV.

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

## Integracion Cloud

El fichero local real se llama `PL2/cloud-api.properties` y queda ignorado por
git. Hay un ejemplo versionado en `PL2/cloud-api.example.properties`:

```properties
api.url=http://localhost:3000/api/results
user.name=alumno.demo
items.limit=5000
```

`api.url` puede ser la URL completa de `POST /api/results` o la URL base del
App Service. Si se indica la base, Scala completa `/api/results`.

Al enviar, Scala construye un JSON con `userName`, `executedAt`, `phase`,
`inputOptions`, `summary`, `dataset`, `sourceApp` e `items`. Si una fase produce
mas detalles que `items.limit`, se envia solo ese maximo y `inputOptions`
incluye cuantos items habia realmente.
