-- Esquema principal de cloud_SCALAtor para Azure SQL.
--
-- Modelo relacional:
--   1. dbo.phase_runs guarda una ejecucion completa de una fase de Scala.
--   2. dbo.phase_output_items guarda los items detallados de esa ejecucion.
--   3. dbo.api_audit_events guarda eventos tecnicos de la API.
--
-- ADVERTENCIA:
-- Este script es destructivo. Borra las tablas si existen para poder recrear el
-- esquema desde cero durante pruebas o preparacion inicial. No ejecutarlo sobre
-- una base con datos que se quieran conservar.

-- Primero se borran las tablas hijas, porque dependen de dbo.phase_runs por
-- claves foraneas. Si se intentara borrar phase_runs antes, SQL Server lo
-- impediria por las referencias existentes.
IF OBJECT_ID('dbo.phase_output_items', 'U') IS NOT NULL
  DROP TABLE dbo.phase_output_items;
GO

IF OBJECT_ID('dbo.api_audit_events', 'U') IS NOT NULL
  DROP TABLE dbo.api_audit_events;
GO

IF OBJECT_ID('dbo.phase_runs', 'U') IS NOT NULL
  DROP TABLE dbo.phase_runs;
GO

-- Tabla principal: una fila por ejecucion de una fase.
--
-- Esta tabla contiene los metadatos comunes del run: usuario, fase, momento de
-- ejecucion, informacion del dataset, resumen final y datos tecnicos de la
-- peticion HTTP.
CREATE TABLE dbo.phase_runs (
  -- Identificador interno autoincremental usado por la API y las FK.
  id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_phase_runs PRIMARY KEY,

  -- Identificador publico alternativo. Es util si en el futuro se quiere
  -- exponer un ID no secuencial fuera de la base de datos.
  run_uuid UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_phase_runs_uuid DEFAULT NEWID(),

  -- Usuario y fase enviados por Scala en el JSON de POST /api/results.
  user_name NVARCHAR(120) NOT NULL,
  phase_code NVARCHAR(20) NOT NULL,
  phase_name NVARCHAR(160) NOT NULL,
  executed_at DATETIMEOFFSET NOT NULL,

  -- Resumen de carga del CSV usado por Scala. Son NULL para permitir payloads
  -- incompletos o pruebas manuales, pero Scala normalmente los envia.
  dataset_path NVARCHAR(500) NULL,
  rows_read INT NULL,
  stored_rows INT NULL,
  discarded_rows INT NULL,
  missing_departure_delay INT NULL,
  missing_arrival_delay INT NULL,
  missing_weather_delay INT NULL,

  -- Opciones elegidas en la fase y contadores de truncado. Se guarda como JSON
  -- para no tener que cambiar el esquema cada vez que una fase tenga opciones
  -- distintas.
  input_options_json NVARCHAR(MAX) NOT NULL,

  -- Resumen humano de la ejecucion y salida completa opcional.
  result_summary NVARCHAR(1000) NOT NULL,
  console_output NVARCHAR(MAX) NULL,

  -- Numero de items realmente recibidos en esta peticion. Puede ser menor que
  -- el total real de resultados si Scala aplico `items.limit`.
  item_count INT NOT NULL CONSTRAINT DF_phase_runs_item_count DEFAULT 0,

  -- Metadatos de insercion y origen HTTP.
  inserted_at DATETIMEOFFSET NOT NULL CONSTRAINT DF_phase_runs_inserted_at DEFAULT SYSDATETIMEOFFSET(),
  client_ip NVARCHAR(80) NULL,
  user_agent NVARCHAR(500) NULL,
  source_app NVARCHAR(120) NULL,

  -- Reglas de integridad: UUID unico, JSON valido y codigos de fase cerrados a
  -- las cuatro fases implementadas.
  CONSTRAINT UQ_phase_runs_uuid UNIQUE (run_uuid),
  CONSTRAINT CK_phase_runs_input_options_json CHECK (ISJSON(input_options_json) = 1),
  CONSTRAINT CK_phase_runs_phase_code CHECK (phase_code IN ('PHASE_01', 'PHASE_02', 'PHASE_03', 'PHASE_04'))
);
GO

-- Indice principal del visor: lista ejecuciones recientes en orden descendente.
CREATE INDEX IX_phase_runs_executed_at ON dbo.phase_runs (executed_at DESC, id DESC);
GO

-- Indice para el filtro por usuario del visor web.
CREATE INDEX IX_phase_runs_user_name ON dbo.phase_runs (user_name);
GO

-- Indice para el filtro por fase del visor web.
CREATE INDEX IX_phase_runs_phase_code ON dbo.phase_runs (phase_code);
GO

-- Tabla de items detallados.
--
-- Es una tabla polimorfica: todos los tipos de salida comparten la misma tabla,
-- y cada fase rellena solo las columnas que le corresponden. El JSON completo
-- del item se conserva en item_json para no perder informacion futura.
CREATE TABLE dbo.phase_output_items (
  id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_phase_output_items PRIMARY KEY,

  -- FK al run propietario y posicion original del item dentro del array enviado.
  run_id INT NOT NULL,
  item_index INT NOT NULL,

  -- Tipo logico del item: delay_match, reduction, airport_histogram, etc.
  item_type NVARCHAR(60) NULL,

  -- Identificacion de vuelo usada por Fase 01 y Fase 02.
  flight_id INT NULL,
  tail_num NVARCHAR(40) NULL,

  -- Campos usados por Fase 04.
  airport_code NVARCHAR(12) NULL,
  airport_seq_id INT NULL,

  -- Clasificacion y minutos de retraso/adelanto usados por Fase 01 y Fase 02.
  delay_kind NVARCHAR(40) NULL,
  delay_minutes INT NULL,

  -- Campos usados por Fase 03.
  reduction_column NVARCHAR(40) NULL,
  reduction_type NVARCHAR(40) NULL,
  reduction_value INT NULL,
  valid_count INT NULL,

  -- Campos especificos del histograma de Fase 04.
  airport_kind NVARCHAR(40) NULL,
  airport_count INT NULL,
  bar_text NVARCHAR(100) NULL,

  -- Texto tal como se imprimio o se podria imprimir en consola.
  raw_text NVARCHAR(1000) NULL,

  -- Copia completa del item original. Debe ser JSON valido.
  item_json NVARCHAR(MAX) NOT NULL,
  inserted_at DATETIMEOFFSET NOT NULL CONSTRAINT DF_phase_output_items_inserted_at DEFAULT SYSDATETIMEOFFSET(),

  -- Si se borra un run, sus items dejan de tener sentido y se eliminan tambien.
  CONSTRAINT FK_phase_output_items_phase_runs
    FOREIGN KEY (run_id) REFERENCES dbo.phase_runs(id) ON DELETE CASCADE,
  CONSTRAINT CK_phase_output_items_item_json CHECK (ISJSON(item_json) = 1)
);
GO

-- Indice para cargar rapido los items de un run en el orden original.
CREATE INDEX IX_phase_output_items_run_id ON dbo.phase_output_items (run_id, item_index);
GO

-- Tabla de auditoria tecnica.
--
-- Guarda eventos internos de la API: validaciones fallidas, errores de insercion
-- y resultados insertados correctamente. No sustituye a los logs de Azure, pero
-- facilita depurar desde SQL.
CREATE TABLE dbo.api_audit_events (
  id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_api_audit_events PRIMARY KEY,
  event_type NVARCHAR(80) NOT NULL,

  -- Puede ser NULL porque algunos errores ocurren antes de crear phase_runs.
  run_id INT NULL,

  -- Datos HTTP basicos de la peticion que genero el evento.
  method NVARCHAR(12) NULL,
  path NVARCHAR(300) NULL,
  status_code INT NULL,
  client_ip NVARCHAR(80) NULL,
  user_agent NVARCHAR(500) NULL,

  -- Detalles estructurados del evento, por ejemplo errores de validacion.
  details_json NVARCHAR(MAX) NULL,
  created_at DATETIMEOFFSET NOT NULL CONSTRAINT DF_api_audit_events_created_at DEFAULT SYSDATETIMEOFFSET(),

  -- Si se borra un run, se conserva la auditoria pero se elimina la referencia.
  CONSTRAINT FK_api_audit_events_phase_runs
    FOREIGN KEY (run_id) REFERENCES dbo.phase_runs(id) ON DELETE SET NULL,
  CONSTRAINT CK_api_audit_events_details_json CHECK (details_json IS NULL OR ISJSON(details_json) = 1)
);
GO

-- Indice para consultar primero los eventos mas recientes.
CREATE INDEX IX_api_audit_events_created_at ON dbo.api_audit_events (created_at DESC, id DESC);
GO
