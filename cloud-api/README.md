# cloud_SCALAtor API

API y visor web para almacenar en Azure SQL las salidas de la PL2 ejecutadas desde Scala.

## Estructura

- `src/server.js`: API Express y endpoints del visor.
- `src/db.js`: conexion a Azure SQL con `mssql`.
- `src/validation.js`: validacion y normalizacion del JSON recibido.
- `db/schema.sql`: tablas para resultados, items y auditoria.
- `public/index.html`: visor HTML servido por `GET /`.

## Preparacion local

```bash
cd cloud-api
npm install
cp .env.example .env
npm start
```

Antes de insertar resultados hay que crear las tablas de `db/schema.sql` en Azure SQL y completar `.env` con la cadena de conexion.

## Endpoints

- `GET /`: visor HTML.
- `GET /api/health`: estado basico de la API.
- `POST /api/results`: guarda una ejecucion de fase.
- `GET /api/results`: lista ejecuciones ordenadas por fecha descendente.
- `GET /api/results/:id`: detalle con todos los items.
- `GET /api/audit`: eventos de auditoria.

## Ejemplo de POST

```bash
curl -X POST http://localhost:3000/api/results \
  -H "Content-Type: application/json" \
  -d '{
    "userName": "alumno1",
    "executedAt": "2026-05-08T12:00:00+02:00",
    "phase": { "code": "PHASE_01", "name": "Fase 01 - Retraso en salida" },
    "inputOptions": { "threshold": 1440 },
    "summary": "Coincidencias encontradas: 2",
    "dataset": {
      "path": "PL2/data/Airline_dataset.csv",
      "rowsRead": 1204825,
      "storedRows": 1204825,
      "discardedRows": 0,
      "missingDepartureDelay": 0,
      "missingArrivalDelay": 0,
      "missingWeatherDelay": 0
    },
    "items": [
      {
        "itemType": "delay_match",
        "flightId": 103309,
        "delayKind": "Retraso",
        "delayMinutes": 1855,
        "rawText": "- Id dataset #103309: Retraso de 1855 minutos"
      }
    ]
  }'
```

## Formato de items por fase

- Fase 01: `itemType=delay_match`, `flightId`, `delayKind`, `delayMinutes`, `rawText`.
- Fase 02: igual que Fase 01 y ademas `tailNum`.
- Fase 03: `itemType=reduction`, `reductionColumn`, `reductionType`, `reductionValue`, `validCount`.
- Fase 04: `itemType=airport_histogram`, `airportKind`, `airportCode`, `airportSeqId`, `airportCount`, `barText`.
