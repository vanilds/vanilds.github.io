// Starts the two micro frontends and the shell together (ports 4201, 4202, 4200). Ctrl+C stops all three.
// Each app stays an independent dev server, exactly like they will be independent deployments.
import { spawn } from 'node:child_process';

const apps = [
  ['mfe-customers', 'cyan'],
  ['mfe-proposals', 'magenta'],
  ['shell', 'yellow'],
];
const colors = { cyan: 36, magenta: 35, yellow: 33 };
const children = apps.map(([name, color]) => {
  const child = spawn('npx', ['ng', 'serve', name], { shell: process.platform === 'win32' });
  const tag = `\x1b[${colors[color]}m[${name}]\x1b[0m `;
  const pipe = (stream, out) =>
    stream.on('data', (chunk) =>
      chunk
        .toString()
        .split('\n')
        .filter(Boolean)
        .forEach((line) => out.write(tag + line + '\n')),
    );
  pipe(child.stdout, process.stdout);
  pipe(child.stderr, process.stderr);
  return child;
});
const stop = () => children.forEach((c) => c.kill('SIGINT'));
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
