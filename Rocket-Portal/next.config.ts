import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  agentRules: false,
  reactCompiler: true,
  async rewrites() {
    return {
      beforeFiles: [
        { source: "/api/:path*", destination: "http://127.0.0.1:8080/api/:path*" },
      ],
      afterFiles: [],
      fallback: [],
    };
  },
};

export default nextConfig;
