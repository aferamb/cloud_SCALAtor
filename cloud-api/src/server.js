require('dotenv').config();

const path = require('path');
const express = require('express');
const { getPool, isDbConfigured, sql } = require('./db');
const { validateResultPayload } = require('./validation');

const app = express();
const port = Number(process.env.PORT || 3000);

app.use(express.json({ limit: '5mb' }));

function clientIp(req) {
  return String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || '')
    .split(',')[0]
    .trim();
}

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

async function insertOutputItems(transaction, runId, items) {
  for (const item of items) {
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

app.get('/', (_req, res) => {
  res.sendFile(path.join(__dirname, '..', 'public', 'index.html'));
});

app.get('/api/health', async (_req, res) => {
  res.json({
    ok: true,
    databaseConfigured: isDbConfigured()
  });
});

app.post('/api/results', requireDatabase, async (req, res) => {
  const pool = await getPool();
  const validation = validateResultPayload(req.body);

  if (!validation.ok) {
    await audit(pool, req, 'validation_error', 400, { errors: validation.errors });
    res.status(400).json({ error: 'Invalid result payload', details: validation.errors });
    return;
  }

  const data = validation.value;
  const transaction = new sql.Transaction(pool);

  try {
    await transaction.begin();
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
    await insertOutputItems(transaction, inserted.id, data.items);
    await transaction.commit();

    await audit(pool, req, 'result_inserted', 201, { itemCount: data.items.length }, inserted.id);
    res.status(201).json({
      id: inserted.id,
      runUuid: inserted.run_uuid,
      itemCount: data.items.length
    });
  } catch (error) {
    await transaction.rollback().catch(() => undefined);
    await audit(pool, req, 'insert_error', 500, { message: error.message });
    console.error(error);
    res.status(500).json({ error: 'Could not store result' });
  }
});

app.get('/api/results', requireDatabase, async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit || 100), 1), 500);
  const filters = [];
  const request = (await getPool()).request().input('limit', sql.Int, limit);

  if (req.query.user) {
    filters.push('user_name LIKE @user_name');
    request.input('user_name', sql.NVarChar(130), `%${req.query.user}%`);
  }
  if (req.query.phase) {
    filters.push('phase_code = @phase_code');
    request.input('phase_code', sql.NVarChar(20), req.query.phase);
  }

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

app.get('/api/results/:id', requireDatabase, async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    res.status(400).json({ error: 'Invalid id' });
    return;
  }

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

  const items = await pool.request().input('id', sql.Int, id).query(`
    SELECT
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
    items: items.recordset
  });
});

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

app.use((err, req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'Unexpected server error' });
});

app.listen(port, () => {
  console.log(`cloud_SCALAtor API listening on http://localhost:${port}`);
});
