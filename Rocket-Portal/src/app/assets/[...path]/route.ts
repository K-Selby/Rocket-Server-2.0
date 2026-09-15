import { readFile } from "node:fs/promises";
import path from "node:path";
import { NextResponse } from "next/server";

const contentTypes: Record<string, string> = {
  ".ico": "image/x-icon",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".pdf": "application/pdf",
  ".png": "image/png",
};

export async function GET(
  _request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const { path: requestedPath } = await context.params;
  const assetRoot = path.resolve(process.cwd(), "../data/assets");
  const assetPath = path.resolve(assetRoot, ...requestedPath);

  if (!assetPath.startsWith(`${assetRoot}${path.sep}`)) {
    return new NextResponse("Not found", { status: 404 });
  }

  try {
    const file = await readFile(assetPath);
    return new NextResponse(file, {
      headers: {
        "Cache-Control": "public, max-age=3600",
        "Content-Type": contentTypes[path.extname(assetPath).toLowerCase()] ?? "application/octet-stream",
      },
    });
  } catch {
    return new NextResponse("Not found", { status: 404 });
  }
}
