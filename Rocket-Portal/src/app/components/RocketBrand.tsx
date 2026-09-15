const SHARED_ASSET_ROOT = (
  process.env.NEXT_PUBLIC_SHARED_ASSETS_URL || "https://rocketpubserver.co.uk/assets"
).replace(/\/$/, "");

export function sharedAssetUrl(path: string) {
  return `${SHARED_ASSET_ROOT}/${path.replace(/^\//, "")}`;
}

type RocketLogoProps = {
  className?: string;
  compact?: boolean;
  decorative?: boolean;
};

export default function RocketLogo({ className, compact = false, decorative = false }: RocketLogoProps) {
  return (
    // The image is served by the shared Flask asset route, outside Next.js.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      className={className}
      src={sharedAssetUrl(compact ? "images/rocket-pub-sidebar-logo.png" : "images/rocket-pub-header-logo.png")}
      alt={decorative ? "" : "The Rocket Pub Liverpool"}
    />
  );
}
