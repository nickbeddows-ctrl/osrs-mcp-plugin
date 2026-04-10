#!/usr/bin/env node
// Claude OSRS MCP Bridge
// Claude Desktop spawns this via stdio, it proxies to the RuneLite HTTP MCP server

const http = require('http');
const readline = require('readline');

const MCP_URL = process.env.OSRS_MCP_URL || 'http://127.0.0.1:8282/mcp';

const rl = readline.createInterface({ input: process.stdin });

function post(body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const url = new URL(MCP_URL);
    const opts = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    };
    const req = http.request(opts, res => {
      let raw = '';
      res.on('data', c => raw += c);
      res.on('end', () => {
        try { resolve(JSON.parse(raw)); }
        catch (e) { reject(new Error('Bad JSON from server: ' + raw)); }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

rl.on('line', async line => {
  if (!line.trim()) return;
  let msg;
  try { msg = JSON.parse(line); } catch { return; }

  // notifications (no id) — forward and don't wait for response
  if (msg.id === undefined || msg.id === null) {
    post(msg).catch(() => {});
    return;
  }

  try {
    const response = await post(msg);
    process.stdout.write(JSON.stringify(response) + '\n');
  } catch (err) {
    const error = {
      jsonrpc: '2.0',
      id: msg.id,
      error: {
        code: -32603,
        message: 'RuneLite MCP server unreachable. Is RuneLite running? ' + err.message
      }
    };
    process.stdout.write(JSON.stringify(error) + '\n');
  }
});

process.stderr.write('Claude OSRS MCP bridge started. Connecting to ' + MCP_URL + '\n');
