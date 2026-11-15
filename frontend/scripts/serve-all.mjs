// Starts the two remotes and the shell dev servers together (Ctrl+C stops all).
import { spawn } from 'node:child_process';

const apps = ['claimant', 'staff', 'shell'];
const children = apps.map((app) =>
  spawn('npx', ['ng', 'serve', app], { stdio: 'inherit', shell: true }),
);
const stop = () => children.forEach((c) => c.kill());
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
