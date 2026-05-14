/**
 * Modulo de acceso a Azure SQL.
 *
 * Este archivo concentra toda la configuracion de conexion a la base de datos
 * para que el resto de la API no tenga que conocer nombres de variables de
 * entorno, opciones del driver `mssql` ni detalles del pool de conexiones.
 */
const sql = require('mssql');

// Promesa compartida del pool. Se inicializa una sola vez y se reutiliza entre
// peticiones para evitar abrir una conexion nueva en cada request HTTP.
let poolPromise = null;

/**
 * Lee una variable de entorno obligatoria.
 *
 * Se usa para valores sin los que la API no puede conectarse a SQL Server. Si
 * falta una variable, se lanza un error temprano y explicito en vez de dejar que
 * el driver falle mas tarde con un mensaje menos claro.
 */
function requiredEnv(name) {
  const value = process.env[name];
  if (!value || value.trim() === '') {
    throw new Error(`Missing required environment variable ${name}`);
  }
  return value;
}

/**
 * Lee una variable booleana de entorno.
 *
 * Azure App Service entrega las Application Settings como strings, por eso el
 * codigo convierte valores como "true" o "false" a booleanos reales. Si la
 * variable no existe, conserva el valor por defecto que necesita Azure SQL.
 */
function boolEnv(name, defaultValue) {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return defaultValue;
  }
  return value.toLowerCase() === 'true';
}

/**
 * Comprueba si hay configuracion minima de base de datos.
 *
 * No abre conexion ni valida credenciales: solo sirve para que `/api/health` y
 * el middleware de rutas puedan distinguir entre "la API esta viva pero no
 * configurada" y "la API puede intentar usar SQL".
 */
function isDbConfigured() {
  return Boolean(
    process.env.DB_SERVER &&
      process.env.DB_NAME &&
      process.env.DB_USER &&
      process.env.DB_PASSWORD
  );
}

/**
 * Construye el objeto de configuracion que entiende el paquete `mssql`.
 *
 * Las variables `DB_*` se configuran en local como variables de entorno y en
 * Azure dentro de App Service > Application Settings. `encrypt=true` es el valor
 * esperado para Azure SQL y `trustServerCertificate=false` evita aceptar
 * certificados no confiables en produccion.
 */
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
    // Pool pequeno y suficiente para esta API academica. Reutiliza conexiones y
    // cierra las inactivas tras 30 segundos para no consumir recursos de Azure.
    pool: {
      max: 10,
      min: 0,
      idleTimeoutMillis: 30000
    }
  };
}

/**
 * Devuelve el pool compartido de SQL Server.
 *
 * La primera llamada crea la conexion; las siguientes reutilizan la misma
 * promesa. Esto es importante en Express porque muchas rutas pueden pedir acceso
 * a SQL simultaneamente.
 */
async function getPool() {
  if (!poolPromise) {
    poolPromise = sql.connect(buildConfig());
  }
  return poolPromise;
}

/**
 * Cierra el pool compartido.
 *
 * Actualmente se expone sobre todo para pruebas o apagados controlados. La API
 * en App Service normalmente vive hasta que Azure reinicia el proceso.
 */
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
