// Pure-JS DataMatrix (ECC200) generator -> scalable SVG string (no DOM / canvas).
// Matches the 2D matrix code printed on Hyundai/Kia (and most OEM) part labels,
// which use DataMatrix rather than QR. Works on-screen (SvgXml) and in print HTML.
import { encodeToMatrix } from "datamatrix-svg-ts";

export function dataMatrixSvg(value: string, opts: { margin?: number } = {}): string {
  const res = encodeToMatrix(value && value.length ? value : " ");
  const { width, height, matrix } = res;
  const margin = opts.margin ?? 1;
  const w = width + margin * 2;
  const h = height + margin * 2;
  let rects = "";
  for (let y = 0; y < height; y++) {
    const row = matrix[y];
    if (!row) continue;
    for (let x = 0; x < width; x++) {
      if (row[x]) rects += `<rect x="${x + margin}" y="${y + margin}" width="1.02" height="1.02" fill="#000"/>`;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" shape-rendering="crispEdges"><rect width="${w}" height="${h}" fill="#fff"/>${rects}</svg>`;
}
