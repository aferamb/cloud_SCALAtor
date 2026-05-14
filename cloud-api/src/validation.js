/**
 * Validacion y normalizacion del JSON que llega a `POST /api/results`.
 *
 * La API recibe objetos construidos por la aplicacion Scala. Este modulo separa
 * la forma externa del JSON de la forma interna que `server.js` guarda en SQL:
 * valida campos obligatorios, convierte valores vacios a `null`, fuerza enteros
 * cuando corresponde y conserva cada item original como JSON completo.
 */
const MaxItemsPerRequest = 1000000;

/**
 * Convierte cualquier valor a texto recortado.
 *
 * `fallback` permite que los campos ausentes se conviertan en una cadena segura
 * sin tener que repetir comprobaciones de `undefined` o `null` en todo el
 * validador.
 */
function text(value, fallback = '') {
  if (value === undefined || value === null) {
    return fallback;
  }
  return String(value).trim();
}

/**
 * Normaliza texto opcional.
 *
 * En SQL se prefiere guardar ausencia de dato como `NULL`, no como cadena
 * vacia. Por eso los campos opcionales pasan por esta funcion.
 */
function nullableText(value) {
  const cleaned = text(value);
  return cleaned === '' ? null : cleaned;
}

/**
 * Convierte un valor a entero o `null`.
 *
 * La base de datos tiene columnas enteras para IDs, retrasos y contadores. Si el
 * JSON trae un valor no entero, se descarta como `null` en vez de guardar un dato
 * inconsistente.
 */
function integer(value) {
  if (value === undefined || value === null || value === '') {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    return null;
  }
  return parsed;
}

/**
 * Convierte una fecha recibida o usa la fecha actual.
 *
 * Si Scala no manda `executedAt`, la API puede seguir guardando el resultado con
 * la hora del servidor. Si manda una fecha invalida, se devuelve `null` para que
 * `validateResultPayload` registre un error.
 */
function dateOrNow(value) {
  if (!value) {
    return new Date();
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return parsed;
}

/**
 * Valida un string obligatorio y su longitud maxima.
 *
 * Devuelve siempre el texto normalizado para poder construir el objeto final,
 * pero acumula errores en el array compartido si el campo falta o se pasa del
 * tamano que soporta la columna SQL correspondiente.
 */
function requireString(errors, value, fieldName, maxLength) {
  const cleaned = text(value);
  if (cleaned === '') {
    errors.push(`${fieldName} is required`);
  }
  if (cleaned.length > maxLength) {
    errors.push(`${fieldName} must be ${maxLength} characters or less`);
  }
  return cleaned;
}

/**
 * Convierte un item libre del array `items` a la forma normalizada de SQL.
 *
 * La tabla `phase_output_items` es polimorfica: las fases rellenan columnas
 * distintas segun el tipo de resultado. Aqui se mapean los campos comunes de
 * Scala a esas columnas y, ademas, se conserva el objeto original entero en
 * `itemJson` para no perder campos futuros que todavia no tengan columna propia.
 */
function normalizeItem(raw, index) {
  const item = raw && typeof raw === 'object' ? raw : {};
  return {
    itemIndex: index,
    itemType: nullableText(item.itemType || item.type),
    flightId: integer(item.flightId),
    tailNum: nullableText(item.tailNum),
    airportCode: nullableText(item.airportCode),
    airportSeqId: integer(item.airportSeqId),
    delayKind: nullableText(item.delayKind),
    delayMinutes: integer(item.delayMinutes),
    reductionColumn: nullableText(item.reductionColumn),
    reductionType: nullableText(item.reductionType),
    reductionValue: integer(item.reductionValue),
    validCount: integer(item.validCount),
    airportKind: nullableText(item.airportKind),
    airportCount: integer(item.airportCount),
    barText: nullableText(item.barText),
    rawText: nullableText(item.rawText),
    itemJson: JSON.stringify(item)
  };
}

/**
 * Valida el payload completo de una ejecucion de fase.
 *
 * Entrada: `req.body` ya parseado por Express.
 * Salida correcta: `{ ok: true, value }`, donde `value` ya esta listo para que
 * `server.js` lo inserte con parametros SQL.
 * Salida incorrecta: `{ ok: false, errors, value }`, con mensajes pensados para
 * responder al cliente y registrar auditoria.
 */
function validateResultPayload(payload) {
  const errors = [];

  // Los objetos anidados son opcionales a nivel de JavaScript para evitar que
  // una peticion mal formada provoque excepciones antes de poder responder 400.
  const body = payload && typeof payload === 'object' ? payload : {};
  const phase = body.phase && typeof body.phase === 'object' ? body.phase : {};
  const dataset = body.dataset && typeof body.dataset === 'object' ? body.dataset : {};
  const executedAt = dateOrNow(body.executedAt);

  if (!executedAt) {
    errors.push('executedAt must be a valid date/time');
  }

  // `items` es obligatorio como array aunque venga vacio. Esto mantiene estable
  // el contrato de la API: cada run tiene siempre una lista de detalles.
  const items = Array.isArray(body.items) ? body.items : [];
  if (!Array.isArray(body.items)) {
    errors.push('items must be an array');
  }

  // Se aceptan nombres planos (`phaseCode`) como compatibilidad, pero el formato
  // principal usado por Scala es el objeto anidado `phase.code` / `phase.name`.
  const normalized = {
    userName: requireString(errors, body.userName, 'userName', 120),
    executedAt,
    phaseCode: requireString(errors, phase.code || body.phaseCode, 'phase.code', 20),
    phaseName: requireString(errors, phase.name || body.phaseName, 'phase.name', 160),
    inputOptionsJson: JSON.stringify(body.inputOptions || {}),
    resultSummary: requireString(errors, body.summary || body.resultSummary, 'summary', 1000),
    consoleOutput: nullableText(body.consoleOutput),
    sourceApp: nullableText(body.sourceApp) || process.env.SOURCE_APP || 'cloud_SCALAtor Scala',
    datasetPath: nullableText(dataset.path),
    rowsRead: integer(dataset.rowsRead),
    storedRows: integer(dataset.storedRows),
    discardedRows: integer(dataset.discardedRows),
    missingDepartureDelay: integer(dataset.missingDepartureDelay),
    missingArrivalDelay: integer(dataset.missingArrivalDelay),
    missingWeatherDelay: integer(dataset.missingWeatherDelay),
    items: items.map(normalizeItem)
  };

  // Limite defensivo de aplicacion. Express tambien tiene limite de tamano en
  // bytes; este limite controla la cantidad logica de filas a insertar.
  if (normalized.items.length > MaxItemsPerRequest) {
    errors.push(`items cannot contain more than ${MaxItemsPerRequest} rows in one request`);
  }

  return {
    ok: errors.length === 0,
    errors,
    value: normalized
  };
}

module.exports = {
  validateResultPayload
};
