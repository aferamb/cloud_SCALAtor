/**
 * Servidor HTTP principal de cloud_SCALAtor.
 *
 * Este archivo define la API Express que recibe resultados desde Scala, los
 * valida, los guarda en Azure SQL y sirve el visor web estatico. La escritura de
 * un resultado completo se hace dentro de una transaccion: primero se crea el
 * run en `phase_runs` y despues se insertan sus items en `phase_output_items`.
 */
require('dotenv').config();

const path = require('path');
const express = require('express');
const { getPool, isDbConfigured, sql } = require('./db');
const { validateResultPayload } = require('./validation');

const app = express();
const port = Number(process.env.PORT || 3000);

// Permite pruebas con payloads muy grandes. El limite logico de filas se aplica
// en `validation.js`; este limite controla el tamano bruto del cuerpo JSON.
app.use(express.json({ limit: '1gb' }));

/**
 * Obtiene la IP real del cliente cuando la app esta detras de Azure.
 *
 * App Service puede enviar la IP original en `x-forwarded-for`; si no existe,
 * se usa la IP del socket. Solo se toma la primera IP porque la cabecera puede
 * contener una cadena de proxies separados por comas.
 */
function clientIp(req) {
  return String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || '')
    .split(',')[0]
    .trim();
}

/**
 * Registra un evento tecnico en `api_audit_events`.
 *
 * La auditoria no debe impedir que la API responda: si el insert de auditoria
 * falla, se escribe en consola y se continua. Las consultas usan parametros SQL
 * para evitar interpolar valores recibidos por HTTP dentro de la query.
 */
async function audit(pool, req, eventType, statusCode, details, runId = null) {
  try {
    await pool
      .request()
      .input('event_type', sql.NVarChar(80), eventType)
      .input('run_id', sql.Int, runId)
      .input('method', sql.NVarChar(12), req.method)
      .input('path', sql.NVarChar(300), req.originalUrl)
      .input('status_code', sql.Int, statusCode)
      .input('client_ip', sql.NVarChar(80), clientIp(req))
      .input('user_agent', sql.NVarChar(500), req.get('user-agent') || '')
      .input('details', sql.NVarChar(sql.MAX), details ? JSON.stringify(details) : null)
      .query(`
        INSERT INTO api_audit_events
          (event_type, run_id, method, path, status_code, client_ip, user_agent, details_json)
        VALUES
          (@event_type, @run_id, @method, @path, @status_code, @client_ip, @user_agent, @details)
      `);
  } catch (error) {
    console.error('Could not write audit event:', error.message);
  }
}

/**
 * Middleware que bloquea rutas dependientes de SQL si faltan variables `DB_*`.
 *
 * No comprueba credenciales ni existencia de tablas. Solo evita intentar abrir
 * conexion cuando la App Service todavia no tiene configuracion minima.
 */
function requireDatabase(req, res, next) {
  if (!isDbConfigured()) {
    res.status(503).json({
      error: 'Database is not configured',
      details: 'Copy .env.example to .env and set the Azure SQL variables.'
    });
    return;
  }
  next();
}

/**
 * Convierte un parametro de query a entero acotado.
 *
 * Se usa en limites de lectura para proteger la API y el navegador de consultas
 * demasiado grandes. Si el valor no es entero, se usa el valor por defecto.
 */
function boundedInteger(value, defaultValue, min, max) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    return defaultValue;
  }
  return Math.min(Math.max(parsed, min), max);
}

/**
 * Inserta los items detallados de una ejecucion dentro de la transaccion activa.
 *
 * Cada item ya viene normalizado por `validation.js`, por eso aqui solo se mapea
 * cada propiedad a su columna SQL. El `runId` enlaza todos los items con la fila
 * principal de `phase_runs`.
 */
async function insertOutputItems(transaction, runId, items) {
  for (const item of items) {
    // Se crea una request nueva por item porque el driver `mssql` asocia los
    // parametros a cada request. Todas comparten la misma transaccion.
    await new sql.Request(transaction)
      .input('run_id', sql.Int, runId)
      .input('item_index', sql.Int, item.itemIndex)
      .input('item_type', sql.NVarChar(60), item.itemType)
      .input('flight_id', sql.Int, item.flightId)
      .input('tail_num', sql.NVarChar(40), item.tailNum)
      .input('airport_code', sql.NVarChar(12), item.airportCode)
      .input('airport_seq_id', sql.Int, item.airportSeqId)
      .input('delay_kind', sql.NVarChar(40), item.delayKind)
      .input('delay_minutes', sql.Int, item.delayMinutes)
      .input('reduction_column', sql.NVarChar(40), item.reductionColumn)
      .input('reduction_type', sql.NVarChar(40), item.reductionType)
      .input('reduction_value', sql.Int, item.reductionValue)
      .input('valid_count', sql.Int, item.validCount)
      .input('airport_kind', sql.NVarChar(40), item.airportKind)
      .input('airport_count', sql.Int, item.airportCount)
      .input('bar_text', sql.NVarChar(100), item.barText)
      .input('raw_text', sql.NVarChar(1000), item.rawText)
      .input('item_json', sql.NVarChar(sql.MAX), item.itemJson)
      .query(`
        INSERT INTO phase_output_items
          (
            run_id, item_index, item_type, flight_id, tail_num, airport_code, airport_seq_id,
            delay_kind, delay_minutes, reduction_column, reduction_type, reduction_value,
            valid_count, airport_kind, airport_count, bar_text, raw_text, item_json
          )
        VALUES
          (
            @run_id, @item_index, @item_type, @flight_id, @tail_num, @airport_code, @airport_seq_id,
            @delay_kind, @delay_minutes, @reduction_column, @reduction_type, @reduction_value,
            @valid_count, @airport_kind, @airport_count, @bar_text, @raw_text, @item_json
          )
      `);
  }
}

// Visor web. El HTML usa la propia API para consultar runs e items.
app.get('/', (_req, res) => {
  res.sendFile(path.join(__dirname, '..', 'public', 'index.html'));
});

// Health check ligero para comprobar si el proceso esta vivo y si ve variables
// de base de datos. No abre conexion ni valida que existan las tablas.
app.get('/api/health', async (_req, res) => {
  res.json({
    ok: true,
    databaseConfigured: isDbConfigured()
  });
});

/**
 * Guarda una ejecucion completa enviada por Scala.
 *
 * El cuerpo esperado es un JSON con usuario, fase, resumen, dataset e `items`.
 * Si la validacion pasa, se guarda todo en una transaccion para que no queden
 * runs sin items o items sin run en caso de error intermedio.
 */
app.post('/api/results', requireDatabase, async (req, res) => {
  const pool = await getPool();
  const validation = validateResultPayload(req.body);

  if (!validation.ok) {
    // Los errores de contrato se auditan para poder depurar payloads invalidos
    // sin tener que mirar solo los logs del proceso.
    await audit(pool, req, 'validation_error', 400, { errors: validation.errors });
    res.status(400).json({ error: 'Invalid result payload', details: validation.errors });
    return;
  }

  const data = validation.value;
  const transaction = new sql.Transaction(pool);

  try {
    await transaction.begin();

    // Inserta la cabecera del run. `OUTPUT inserted.id` recupera el ID identity
    // que se necesita como clave foranea para los items.
    const result = await new sql.Request(transaction)
      .input('user_name', sql.NVarChar(120), data.userName)
      .input('phase_code', sql.NVarChar(20), data.phaseCode)
      .input('phase_name', sql.NVarChar(160), data.phaseName)
      .input('executed_at', sql.DateTimeOffset, data.executedAt)
      .input('dataset_path', sql.NVarChar(500), data.datasetPath)
      .input('rows_read', sql.Int, data.rowsRead)
      .input('stored_rows', sql.Int, data.storedRows)
      .input('discarded_rows', sql.Int, data.discardedRows)
      .input('missing_departure_delay', sql.Int, data.missingDepartureDelay)
      .input('missing_arrival_delay', sql.Int, data.missingArrivalDelay)
      .input('missing_weather_delay', sql.Int, data.missingWeatherDelay)
      .input('input_options_json', sql.NVarChar(sql.MAX), data.inputOptionsJson)
      .input('result_summary', sql.NVarChar(1000), data.resultSummary)
      .input('console_output', sql.NVarChar(sql.MAX), data.consoleOutput)
      .input('item_count', sql.Int, data.items.length)
      .input('client_ip', sql.NVarChar(80), clientIp(req))
      .input('user_agent', sql.NVarChar(500), req.get('user-agent') || '')
      .input('source_app', sql.NVarChar(120), data.sourceApp)
      .query(`
        INSERT INTO phase_runs
          (
            user_name, phase_code, phase_name, executed_at, dataset_path,
            rows_read, stored_rows, discarded_rows, missing_departure_delay,
            missing_arrival_delay, missing_weather_delay, input_options_json,
            result_summary, console_output, item_count, client_ip, user_agent, source_app
          )
        OUTPUT inserted.id, CONVERT(varchar(36), inserted.run_uuid) AS run_uuid
        VALUES
          (
            @user_name, @phase_code, @phase_name, @executed_at, @dataset_path,
            @rows_read, @stored_rows, @discarded_rows, @missing_departure_delay,
            @missing_arrival_delay, @missing_weather_delay, @input_options_json,
            @result_summary, @console_output, @item_count, @client_ip, @user_agent, @source_app
          )
      `);

    const inserted = result.recordset[0];

    // Los items se insertan despues del run para poder guardar el `run_id`.
    // Siguen dentro de la misma transaccion.
    await insertOutputItems(transaction, inserted.id, data.items);
    await transaction.commit();

    // La auditoria se escribe tras confirmar la transaccion principal: el run ya
    // existe y puede referenciarse con seguridad.
    await audit(pool, req, 'result_inserted', 201, { itemCount: data.items.length }, inserted.id);
    res.status(201).json({
      id: inserted.id,
      runUuid: inserted.run_uuid,
      itemCount: data.items.length
    });
  } catch (error) {
    // Ante cualquier fallo de SQL se revierte la transaccion. El `.catch` evita
    // que un rollback fallido o ya cerrado oculte el error original.
    await transaction.rollback().catch(() => undefined);
    await audit(pool, req, 'insert_error', 500, { message: error.message });
    console.error(error);
    res.status(500).json({ error: 'Could not store result' });
  }
});

/**
 * Lista ejecuciones de fase para la tabla principal del visor.
 *
 * Soporta filtros simples por usuario y fase. El limite queda acotado a 500 para
 * no cargar demasiadas filas en una sola respuesta.
 */
app.get('/api/results', requireDatabase, async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit || 100), 1), 500);
  const filters = [];
  const request = (await getPool()).request().input('limit', sql.Int, limit);

  if (req.query.user) {
    // Filtro parcial para facilitar busqueda desde el input del visor web.
    filters.push('user_name LIKE @user_name');
    request.input('user_name', sql.NVarChar(130), `%${req.query.user}%`);
  }
  if (req.query.phase) {
    // Filtro exacto porque `phase_code` es un conjunto cerrado de cuatro fases.
    filters.push('phase_code = @phase_code');
    request.input('phase_code', sql.NVarChar(20), req.query.phase);
  }

  // La clausula WHERE se compone solo con fragmentos controlados por el codigo;
  // los valores de usuario se pasan siempre como parametros.
  const whereClause = filters.length > 0 ? `WHERE ${filters.join(' AND ')}` : '';
  const result = await request.query(`
    SELECT TOP (@limit)
      id,
      CONVERT(varchar(36), run_uuid) AS runUuid,
      user_name AS userName,
      phase_code AS phaseCode,
      phase_name AS phaseName,
      executed_at AS executedAt,
      result_summary AS resultSummary,
      item_count AS itemCount,
      dataset_path AS datasetPath,
      inserted_at AS insertedAt,
      source_app AS sourceApp
    FROM phase_runs
    ${whereClause}
    ORDER BY executed_at DESC, inserted_at DESC, id DESC
  `);

  res.json({ results: result.recordset });
});

/**
 * Devuelve el detalle de una ejecucion concreta.
 *
 * La respuesta incluye la cabecera del run y los primeros `itemLimit` items. El
 * total real se devuelve aparte para que el visor pueda mostrar "N de total" sin
 * cargar todos los detalles.
 */
app.get('/api/results/:id', requireDatabase, async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    res.status(400).json({ error: 'Invalid id' });
    return;
  }

  // Por defecto el visor carga 25 items. El maximo 500 protege al navegador y a
  // SQL en ejecuciones con muchisimos resultados.
  const itemLimit = boundedInteger(req.query.itemLimit, 25, 1, 500);
  const pool = await getPool();
  const run = await pool.request().input('id', sql.Int, id).query(`
    SELECT
      id,
      CONVERT(varchar(36), run_uuid) AS runUuid,
      user_name AS userName,
      phase_code AS phaseCode,
      phase_name AS phaseName,
      executed_at AS executedAt,
      dataset_path AS datasetPath,
      rows_read AS rowsRead,
      stored_rows AS storedRows,
      discarded_rows AS discardedRows,
      missing_departure_delay AS missingDepartureDelay,
      missing_arrival_delay AS missingArrivalDelay,
      missing_weather_delay AS missingWeatherDelay,
      input_options_json AS inputOptionsJson,
      result_summary AS resultSummary,
      console_output AS consoleOutput,
      item_count AS itemCount,
      inserted_at AS insertedAt,
      client_ip AS clientIp,
      user_agent AS userAgent,
      source_app AS sourceApp
    FROM phase_runs
    WHERE id = @id
  `);

  if (run.recordset.length === 0) {
    res.status(404).json({ error: 'Result not found' });
    return;
  }

  // Los items se ordenan por `item_index`, que conserva el orden que mando
  // Scala, y por `id` como desempate estable.
  const items = await pool
    .request()
    .input('id', sql.Int, id)
    .input('item_limit', sql.Int, itemLimit)
    .query(`
    SELECT TOP (@item_limit)
      item_index AS itemIndex,
      item_type AS itemType,
      flight_id AS flightId,
      tail_num AS tailNum,
      airport_code AS airportCode,
      airport_seq_id AS airportSeqId,
      delay_kind AS delayKind,
      delay_minutes AS delayMinutes,
      reduction_column AS reductionColumn,
      reduction_type AS reductionType,
      reduction_value AS reductionValue,
      valid_count AS validCount,
      airport_kind AS airportKind,
      airport_count AS airportCount,
      bar_text AS barText,
      raw_text AS rawText,
      item_json AS itemJson
    FROM phase_output_items
    WHERE run_id = @id
    ORDER BY item_index ASC, id ASC
  `);

  res.json({
    result: run.recordset[0],
    items: items.recordset,
    itemsShown: items.recordset.length,
    itemsTotal: run.recordset[0].itemCount
  });
});

/**
 * Lista eventos tecnicos de auditoria.
 *
 * Es util para diagnosticar errores de validacion, insercion o pruebas manuales
 * sin acceder directamente a los logs de Azure.
 */
app.get('/api/audit', requireDatabase, async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit || 100), 1), 500);
  const result = await (await getPool()).request().input('limit', sql.Int, limit).query(`
    SELECT TOP (@limit)
      id,
      event_type AS eventType,
      run_id AS runId,
      method,
      path,
      status_code AS statusCode,
      client_ip AS clientIp,
      user_agent AS userAgent,
      details_json AS detailsJson,
      created_at AS createdAt
    FROM api_audit_events
    ORDER BY created_at DESC, id DESC
  `);
  res.json({ events: result.recordset });
});

// Manejador final de errores no capturados por una ruta concreta.
app.use((err, req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'Unexpected server error' });
});

// En Azure App Service `PORT` lo define la plataforma. En local se usa 3000.
app.listen(port, () => {
  console.log(`cloud_SCALAtor API listening on http://localhost:${port}`);
});
