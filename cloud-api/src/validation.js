function text(value, fallback = '') {
  if (value === undefined || value === null) {
    return fallback;
  }
  return String(value).trim();
}

function nullableText(value) {
  const cleaned = text(value);
  return cleaned === '' ? null : cleaned;
}

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

function validateResultPayload(payload) {
  const errors = [];
  const body = payload && typeof payload === 'object' ? payload : {};
  const phase = body.phase && typeof body.phase === 'object' ? body.phase : {};
  const dataset = body.dataset && typeof body.dataset === 'object' ? body.dataset : {};
  const executedAt = dateOrNow(body.executedAt);

  if (!executedAt) {
    errors.push('executedAt must be a valid date/time');
  }

  const items = Array.isArray(body.items) ? body.items : [];
  if (!Array.isArray(body.items)) {
    errors.push('items must be an array');
  }

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

  if (normalized.items.length > 5000) {
    errors.push('items cannot contain more than 5000 rows in one request');
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
