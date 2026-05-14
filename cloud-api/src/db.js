const sql = require('mssql');

let poolPromise = null;

function requiredEnv(name) {
  const value = process.env[name];
  if (!value || value.trim() === '') {
    throw new Error(`Missing required environment variable ${name}`);
  }
  return value;
}

function boolEnv(name, defaultValue) {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return defaultValue;
  }
  return value.toLowerCase() === 'true';
}

function isDbConfigured() {
  return Boolean(
    process.env.DB_SERVER &&
      process.env.DB_NAME &&
      process.env.DB_USER &&
      process.env.DB_PASSWORD
  );
}

function buildConfig() {
  return {
    server: requiredEnv('DB_SERVER'),
    port: Number(process.env.DB_PORT || 1433),
    database: requiredEnv('DB_NAME'),
    user: requiredEnv('DB_USER'),
    password: requiredEnv('DB_PASSWORD'),
    options: {
      encrypt: boolEnv('DB_ENCRYPT', true),
      trustServerCertificate: boolEnv('DB_TRUST_SERVER_CERTIFICATE', false)
    },
    pool: {
      max: 10,
      min: 0,
      idleTimeoutMillis: 30000
    }
  };
}

async function getPool() {
  if (!poolPromise) {
    poolPromise = sql.connect(buildConfig());
  }
  return poolPromise;
}

async function closePool() {
  if (poolPromise) {
    const pool = await poolPromise;
    poolPromise = null;
    await pool.close();
  }
}

module.exports = {
  sql,
  getPool,
  closePool,
  isDbConfigured
};
