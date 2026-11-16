import {copyFileSync, mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname} from 'node:path';

const master = new URL('../../branding/maryk-logo.svg', import.meta.url);
for (const target of ['../src/generated/brand/maryk-logo.svg', '../public/brand/maryk-logo.svg', '../public/brand/favicon.svg']) {
    const destination = fileURLToPath(new URL(target, import.meta.url));
    mkdirSync(dirname(destination), {recursive: true});
    copyFileSync(master, destination);
}
