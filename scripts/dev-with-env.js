const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");

const rootDir = path.resolve(__dirname, "..");
const envPath = path.join(rootDir, ".env");

function parseEnv(content) {
  const values = {};

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const equalsIndex = line.indexOf("=");
    if (equalsIndex === -1) continue;

    const key = line.slice(0, equalsIndex).trim();
    let value = line.slice(equalsIndex + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    values[key] = value;
  }

  return values;
}

if (fs.existsSync(envPath)) {
  const values = parseEnv(fs.readFileSync(envPath, "utf8"));
  for (const [key, value] of Object.entries(values)) {
    if (process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
} else {
  console.warn("No .env file found. Create one from .env.example for local dev secrets.");
}

const child = spawn(
  "npx",
  [
    "concurrently",
    "-n",
    "backend,frontend",
    "-c",
    "green,blue",
    "npm:dev:backend",
    "npm:dev:frontend",
  ],
  {
    cwd: rootDir,
    env: process.env,
    stdio: "inherit",
    shell: true,
  }
);

child.on("exit", (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
  }
  process.exit(code ?? 0);
});
