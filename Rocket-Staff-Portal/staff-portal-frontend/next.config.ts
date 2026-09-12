import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  agentRules: false,
  basePath: "/staff",
  reactCompiler: true,
  async rewrites() {
    return {
      beforeFiles: [
        { source: "/", destination: "http://127.0.0.1:8001/", basePath: false },
        { source: "/customer", destination: "http://127.0.0.1:8001/customer", basePath: false },
        { source: "/customer/:path*", destination: "http://127.0.0.1:8001/customer/:path*", basePath: false },
        { source: "/booking/:path*", destination: "http://127.0.0.1:8001/booking/:path*", basePath: false },
        { source: "/assets/:path*", destination: "http://127.0.0.1:8001/assets/:path*", basePath: false },
        { source: "/static/:path*", destination: "http://127.0.0.1:8001/static/:path*", basePath: false },
        { source: "/customer-static/:path*", destination: "http://127.0.0.1:8001/customer-static/:path*", basePath: false },
      ],
      afterFiles: [],
      fallback: [],
    };
  },
};

export default nextConfig;
