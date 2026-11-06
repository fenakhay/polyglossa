import { readFileSync, writeFileSync } from "node:fs";

const root = new URL("../", import.meta.url);
const catalog = readFileSync(new URL("catalog.tsv", root), "utf8");
writeFileSync(new URL("dist/data.js", root), `export default ${JSON.stringify(catalog)};\n`);
writeFileSync(new URL("dist/data.d.ts", root), readFileSync(new URL("src/data.d.ts", root)));
