-- Sample data for Azure SQL.
-- Run this after cloud-api/db/schema.sql.
-- It only removes previous rows inserted by this same sample script.

SET XACT_ABORT ON;

BEGIN TRY
  BEGIN TRANSACTION;

  DECLARE @SampleTag NVARCHAR(120) = 'cloud_SCALAtor sample-data v1';
  DECLARE @Now DATETIMEOFFSET = SYSDATETIMEOFFSET();

  DELETE FROM dbo.api_audit_events
  WHERE JSON_VALUE(details_json, '$.sampleSet') = @SampleTag;

  DELETE FROM dbo.phase_runs
  WHERE source_app = @SampleTag;

  DECLARE @Run01 INT;
  DECLARE @Run02 INT;
  DECLARE @Run03 INT;
  DECLARE @Run04 INT;

  INSERT INTO dbo.phase_runs (
    user_name,
    phase_code,
    phase_name,
    executed_at,
    dataset_path,
    rows_read,
    stored_rows,
    discarded_rows,
    missing_departure_delay,
    missing_arrival_delay,
    missing_weather_delay,
    input_options_json,
    result_summary,
    console_output,
    item_count,
    client_ip,
    user_agent,
    source_app
  )
  VALUES (
    N'alumno.demo',
    N'PHASE_01',
    N'Fase 01 - Retraso en salida',
    DATEADD(HOUR, -8, @Now),
    N'PL2/data/Airline_dataset.csv',
    1204825,
    1204118,
    707,
    84,
    91,
    1198760,
    N'{"threshold":1440,"delayColumn":"DEP_DELAY"}',
    N'Coincidencias encontradas: 3',
    CONCAT(N'Fase 01 - DEP_DELAY', CHAR(13), CHAR(10), N'Coincidencias encontradas: 3'),
    3,
    N'127.0.0.1',
    N'sample-data.sql',
    @SampleTag
  );

  SET @Run01 = CONVERT(INT, SCOPE_IDENTITY());

  INSERT INTO dbo.phase_output_items (
    run_id,
    item_index,
    item_type,
    flight_id,
    tail_num,
    airport_code,
    airport_seq_id,
    delay_kind,
    delay_minutes,
    reduction_column,
    reduction_type,
    reduction_value,
    valid_count,
    airport_kind,
    airport_count,
    bar_text,
    raw_text,
    item_json
  )
  VALUES
    (
      @Run01,
      0,
      N'delay_match',
      103309,
      NULL,
      NULL,
      NULL,
      N'Retraso',
      1855,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #103309: Retraso de 1855 minutos',
      N'{"itemType":"delay_match","flightId":103309,"delayKind":"Retraso","delayMinutes":1855,"rawText":"- Id dataset #103309: Retraso de 1855 minutos"}'
    ),
    (
      @Run01,
      1,
      N'delay_match',
      225917,
      NULL,
      NULL,
      NULL,
      N'Retraso',
      1502,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #225917: Retraso de 1502 minutos',
      N'{"itemType":"delay_match","flightId":225917,"delayKind":"Retraso","delayMinutes":1502,"rawText":"- Id dataset #225917: Retraso de 1502 minutos"}'
    ),
    (
      @Run01,
      2,
      N'delay_match',
      709482,
      NULL,
      NULL,
      NULL,
      N'Retraso',
      1448,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #709482: Retraso de 1448 minutos',
      N'{"itemType":"delay_match","flightId":709482,"delayKind":"Retraso","delayMinutes":1448,"rawText":"- Id dataset #709482: Retraso de 1448 minutos"}'
    );

  INSERT INTO dbo.phase_runs (
    user_name,
    phase_code,
    phase_name,
    executed_at,
    dataset_path,
    rows_read,
    stored_rows,
    discarded_rows,
    missing_departure_delay,
    missing_arrival_delay,
    missing_weather_delay,
    input_options_json,
    result_summary,
    console_output,
    item_count,
    client_ip,
    user_agent,
    source_app
  )
  VALUES (
    N'alumno.demo',
    N'PHASE_02',
    N'Fase 02 - Retraso en llegada',
    DATEADD(HOUR, -6, @Now),
    N'PL2/data/Airline_dataset.csv',
    1204825,
    1204118,
    707,
    84,
    91,
    1198760,
    N'{"threshold":1200,"delayColumn":"ARR_DELAY","includeTailNum":true}',
    N'Coincidencias encontradas: 4',
    CONCAT(
      N'Fase 02 - ARR_DELAY + TAIL_NUM',
      CHAR(13),
      CHAR(10),
      N'Resultados CPU:',
      CHAR(13),
      CHAR(10),
      N'Se han encontrado 4 aviones'
    ),
    4,
    N'127.0.0.1',
    N'sample-data.sql',
    @SampleTag
  );

  SET @Run02 = CONVERT(INT, SCOPE_IDENTITY());

  INSERT INTO dbo.phase_output_items (
    run_id,
    item_index,
    item_type,
    flight_id,
    tail_num,
    airport_code,
    airport_seq_id,
    delay_kind,
    delay_minutes,
    reduction_column,
    reduction_type,
    reduction_value,
    valid_count,
    airport_kind,
    airport_count,
    bar_text,
    raw_text,
    item_json
  )
  VALUES
    (
      @Run02,
      0,
      N'delay_match',
      301204,
      N'N928AT',
      NULL,
      NULL,
      N'Retraso (llegada)',
      1642,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #301204  Matricula: N928AT  Retraso (llegada): 1642 min',
      N'{"itemType":"delay_match","flightId":301204,"tailNum":"N928AT","delayKind":"Retraso (llegada)","delayMinutes":1642,"rawText":"- Id dataset #301204  Matricula: N928AT  Retraso (llegada): 1642 min"}'
    ),
    (
      @Run02,
      1,
      N'delay_match',
      518903,
      N'N671DL',
      NULL,
      NULL,
      N'Retraso (llegada)',
      1397,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #518903  Matricula: N671DL  Retraso (llegada): 1397 min',
      N'{"itemType":"delay_match","flightId":518903,"tailNum":"N671DL","delayKind":"Retraso (llegada)","delayMinutes":1397,"rawText":"- Id dataset #518903  Matricula: N671DL  Retraso (llegada): 1397 min"}'
    ),
    (
      @Run02,
      2,
      N'delay_match',
      621440,
      N'N812UA',
      NULL,
      NULL,
      N'Retraso (llegada)',
      1288,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #621440  Matricula: N812UA  Retraso (llegada): 1288 min',
      N'{"itemType":"delay_match","flightId":621440,"tailNum":"N812UA","delayKind":"Retraso (llegada)","delayMinutes":1288,"rawText":"- Id dataset #621440  Matricula: N812UA  Retraso (llegada): 1288 min"}'
    ),
    (
      @Run02,
      3,
      N'delay_match',
      902771,
      N'N433AA',
      NULL,
      NULL,
      N'Retraso (llegada)',
      1215,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'- Id dataset #902771  Matricula: N433AA  Retraso (llegada): 1215 min',
      N'{"itemType":"delay_match","flightId":902771,"tailNum":"N433AA","delayKind":"Retraso (llegada)","delayMinutes":1215,"rawText":"- Id dataset #902771  Matricula: N433AA  Retraso (llegada): 1215 min"}'
    );

  INSERT INTO dbo.phase_runs (
    user_name,
    phase_code,
    phase_name,
    executed_at,
    dataset_path,
    rows_read,
    stored_rows,
    discarded_rows,
    missing_departure_delay,
    missing_arrival_delay,
    missing_weather_delay,
    input_options_json,
    result_summary,
    console_output,
    item_count,
    client_ip,
    user_agent,
    source_app
  )
  VALUES (
    N'alumno.demo',
    N'PHASE_03',
    N'Fase 03 - Reduccion de retraso',
    DATEADD(HOUR, -4, @Now),
    N'PL2/data/Airline_dataset.csv',
    1204825,
    1204118,
    707,
    84,
    91,
    1198760,
    N'{"reductions":[{"column":"DEP_DELAY","reduction":"Maximo"},{"column":"ARR_DELAY","reduction":"Minimo"}]}',
    N'Reducciones de ejemplo: DEP_DELAY Maximo=1855; ARR_DELAY Minimo=-82',
    CONCAT(
      N'Fase 03 - Reduccion',
      CHAR(13),
      CHAR(10),
      N'DEP_DELAY | Maximo | validos 1204034',
      CHAR(13),
      CHAR(10),
      N'[Simple] Max() DEP_DELAY = 1855 minutos',
      CHAR(13),
      CHAR(10),
      N'[Simple] Min() ARR_DELAY = -82 minutos'
    ),
    2,
    N'127.0.0.1',
    N'sample-data.sql',
    @SampleTag
  );

  SET @Run03 = CONVERT(INT, SCOPE_IDENTITY());

  INSERT INTO dbo.phase_output_items (
    run_id,
    item_index,
    item_type,
    flight_id,
    tail_num,
    airport_code,
    airport_seq_id,
    delay_kind,
    delay_minutes,
    reduction_column,
    reduction_type,
    reduction_value,
    valid_count,
    airport_kind,
    airport_count,
    bar_text,
    raw_text,
    item_json
  )
  VALUES
    (
      @Run03,
      0,
      N'reduction',
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'DEP_DELAY',
      N'Maximo',
      1855,
      1204034,
      NULL,
      NULL,
      NULL,
      N'[Simple] Max() DEP_DELAY = 1855 minutos; validos=1204034',
      N'{"itemType":"reduction","reductionColumn":"DEP_DELAY","reductionType":"Maximo","reductionValue":1855,"validCount":1204034,"rawText":"[Simple] Max() DEP_DELAY = 1855 minutos; validos=1204034"}'
    ),
    (
      @Run03,
      1,
      N'reduction',
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'ARR_DELAY',
      N'Minimo',
      -82,
      1204027,
      NULL,
      NULL,
      NULL,
      N'[Simple] Min() ARR_DELAY = -82 minutos; validos=1204027',
      N'{"itemType":"reduction","reductionColumn":"ARR_DELAY","reductionType":"Minimo","reductionValue":-82,"validCount":1204027,"rawText":"[Simple] Min() ARR_DELAY = -82 minutos; validos=1204027"}'
    );

  INSERT INTO dbo.phase_runs (
    user_name,
    phase_code,
    phase_name,
    executed_at,
    dataset_path,
    rows_read,
    stored_rows,
    discarded_rows,
    missing_departure_delay,
    missing_arrival_delay,
    missing_weather_delay,
    input_options_json,
    result_summary,
    console_output,
    item_count,
    client_ip,
    user_agent,
    source_app
  )
  VALUES (
    N'alumno.demo',
    N'PHASE_04',
    N'Fase 04 - Histograma de aeropuertos',
    DATEADD(HOUR, -2, @Now),
    N'PL2/data/Airline_dataset.csv',
    1204825,
    1204118,
    707,
    84,
    91,
    1198760,
    N'{"airportType":"origen","threshold":5000}',
    N'Aeropuertos mostrados=5; total=318; filas validas=1204118',
    CONCAT(
      N'Fase 04 - Histograma de aeropuertos',
      CHAR(13),
      CHAR(10),
      N'origen | filas validas 1204118 | bins 318',
      CHAR(13),
      CHAR(10),
      N'Aeropuertos mostrados=5; total=318; filas validas=1204118'
    ),
    5,
    N'127.0.0.1',
    N'sample-data.sql',
    @SampleTag
  );

  SET @Run04 = CONVERT(INT, SCOPE_IDENTITY());

  INSERT INTO dbo.phase_output_items (
    run_id,
    item_index,
    item_type,
    flight_id,
    tail_num,
    airport_code,
    airport_seq_id,
    delay_kind,
    delay_minutes,
    reduction_column,
    reduction_type,
    reduction_value,
    valid_count,
    airport_kind,
    airport_count,
    bar_text,
    raw_text,
    item_json
  )
  VALUES
    (
      @Run04,
      0,
      N'airport_histogram',
      NULL,
      NULL,
      N'ATL',
      1039707,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'origen',
      74231,
      N'########################################',
      N'ATL (1039707) | 74231 ########################################',
      N'{"itemType":"airport_histogram","airportKind":"origen","airportCode":"ATL","airportSeqId":1039707,"airportCount":74231,"barText":"########################################","rawText":"ATL (1039707) | 74231 ########################################"}'
    ),
    (
      @Run04,
      1,
      N'airport_histogram',
      NULL,
      NULL,
      N'ORD',
      1393004,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'origen',
      51809,
      N'###########################',
      N'ORD (1393004) | 51809 ###########################',
      N'{"itemType":"airport_histogram","airportKind":"origen","airportCode":"ORD","airportSeqId":1393004,"airportCount":51809,"barText":"###########################","rawText":"ORD (1393004) | 51809 ###########################"}'
    ),
    (
      @Run04,
      2,
      N'airport_histogram',
      NULL,
      NULL,
      N'DFW',
      1129806,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'origen',
      48712,
      N'##########################',
      N'DFW (1129806) | 48712 ##########################',
      N'{"itemType":"airport_histogram","airportKind":"origen","airportCode":"DFW","airportSeqId":1129806,"airportCount":48712,"barText":"##########################","rawText":"DFW (1129806) | 48712 ##########################"}'
    ),
    (
      @Run04,
      3,
      N'airport_histogram',
      NULL,
      NULL,
      N'DEN',
      1129202,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'origen',
      36344,
      N'###################',
      N'DEN (1129202) | 36344 ###################',
      N'{"itemType":"airport_histogram","airportKind":"origen","airportCode":"DEN","airportSeqId":1129202,"airportCount":36344,"barText":"###################","rawText":"DEN (1129202) | 36344 ###################"}'
    ),
    (
      @Run04,
      4,
      N'airport_histogram',
      NULL,
      NULL,
      N'LAX',
      1289208,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      NULL,
      N'origen',
      29165,
      N'################',
      N'LAX (1289208) | 29165 ################',
      N'{"itemType":"airport_histogram","airportKind":"origen","airportCode":"LAX","airportSeqId":1289208,"airportCount":29165,"barText":"################","rawText":"LAX (1289208) | 29165 ################"}'
    );

  INSERT INTO dbo.api_audit_events (
    event_type,
    run_id,
    method,
    path,
    status_code,
    client_ip,
    user_agent,
    details_json
  )
  VALUES
    (
      N'result_inserted',
      @Run01,
      N'POST',
      N'/api/results',
      201,
      N'127.0.0.1',
      N'sample-data.sql',
      N'{"sampleSet":"cloud_SCALAtor sample-data v1","itemCount":3}'
    ),
    (
      N'result_inserted',
      @Run02,
      N'POST',
      N'/api/results',
      201,
      N'127.0.0.1',
      N'sample-data.sql',
      N'{"sampleSet":"cloud_SCALAtor sample-data v1","itemCount":4}'
    ),
    (
      N'result_inserted',
      @Run03,
      N'POST',
      N'/api/results',
      201,
      N'127.0.0.1',
      N'sample-data.sql',
      N'{"sampleSet":"cloud_SCALAtor sample-data v1","itemCount":2}'
    ),
    (
      N'result_inserted',
      @Run04,
      N'POST',
      N'/api/results',
      201,
      N'127.0.0.1',
      N'sample-data.sql',
      N'{"sampleSet":"cloud_SCALAtor sample-data v1","itemCount":5}'
    ),
    (
      N'validation_error',
      NULL,
      N'POST',
      N'/api/results',
      400,
      N'127.0.0.1',
      N'sample-data.sql',
      N'{"sampleSet":"cloud_SCALAtor sample-data v1","errors":["items must be an array"]}'
    );

  COMMIT TRANSACTION;

  SELECT
    id,
    CONVERT(VARCHAR(36), run_uuid) AS run_uuid,
    user_name,
    phase_code,
    phase_name,
    executed_at,
    item_count,
    source_app
  FROM dbo.phase_runs
  WHERE source_app = @SampleTag
  ORDER BY executed_at DESC, id DESC;
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0
    ROLLBACK TRANSACTION;

  THROW;
END CATCH;
