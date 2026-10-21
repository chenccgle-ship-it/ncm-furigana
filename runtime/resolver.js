'use strict';
// Local adapter; upstream provider sources are bundled without modification.
process.env.LOG_LEVEL = 'silent';
process.env.JSON_LOG = 'true';
const http = require('http');
const fs = require('fs');
const { timingSafeEqual, createHash } = require('crypto');
const settings = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const token = settings.token || '';
if (token.length < 32) throw new Error('Missing local authentication');
const sources = [settings.source === 'qq' ? 'qq' : 'kuwo'];
if (sources[0] === 'qq' && typeof settings.qqCookie === 'string') process.env.QQ_COOKIE = settings.qqCookie;
const match = require('./src/provider/match');
require('./src/provider/insure').disable = true;
const cache = new Map(), jobs = new Map();
function send(res, status, value) {
  if (res.writableEnded || res.destroyed) return;
  res.writeHead(status, {'Content-Type':'application/json', 'Cache-Control':'no-store'});
  res.end(JSON.stringify(value));
}
function authorized(value) {
  const input = Buffer.from(value || ''), expected = Buffer.from('Bearer ' + token);
  return input.length === expected.length && timingSafeEqual(input, expected);
}
function resolve(id) {
  const old = cache.get(id);
  if (old && old.expires > Date.now()) return Promise.resolve(old.result);
  if (jobs.has(id)) return jobs.get(id);
  if (jobs.size >= 2) return Promise.reject(new Error('busy'));
  const job = match(id, sources).then(song => {
    const parsed = new URL(song.url);
    if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password)
      throw new Error('Invalid media URL');
    const result = {id, url:song.url, br:song.br || 128000, size:song.size || 0,
      md5:song.md5 || createHash('md5').update(song.url).digest('hex'), source:song.source};
    cache.set(id, {result, expires:Date.now() + 180000});
    if (cache.size > 128) cache.delete(cache.keys().next().value);
    return result;
  }).finally(() => jobs.delete(id));
  jobs.set(id, job);
  return job;
}
const server = http.createServer(async (req, res) => {
  if (req.method !== 'GET' || !authorized(req.headers.authorization))
    return send(res, 403, {error:'denied'});
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname === '/health') return send(res, 200, {ok:true, node:process.version, engine:'UnblockNeteaseMusic 0.28.0', sources});
  const id = url.searchParams.get('id');
  if (url.pathname !== '/resolve' || !/^[1-9][0-9]{0,15}$/.test(id || ''))
    return send(res, 400, {error:'invalid-song-id'});
  const timer = setTimeout(() => send(res, 504, {error:'match-timeout'}), 11500);
  try { send(res, 200, await resolve(id)); }
  catch (_) { send(res, 404, {error:'no-matching-source'}); }
  finally { clearTimeout(timer); }
});
server.headersTimeout = 5000;
server.requestTimeout = 15000;
server.maxConnections = 8;
server.listen(0, '127.0.0.1', () => console.log('READY ' + server.address().port));
const cleanup = setInterval(() => {
  for (const [id, entry] of cache) if (entry.expires <= Date.now()) cache.delete(id);
  require('./src/cache').CacheStorageGroup.getInstance().cleanup();
}, 60000);
process.on('SIGTERM', () => {clearInterval(cleanup); server.close(); process.exit(0);});
