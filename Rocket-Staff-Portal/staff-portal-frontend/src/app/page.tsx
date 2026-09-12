"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

const API = "";

export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    async function checkLogin() {
      try {
        const response = await fetch(
          `${API}/api/auth/me`,
          {
            credentials: "include",
          }
        );

        router.replace(
          response.ok ? "/rota" : "/login"
        );
      } catch {
        router.replace("/login");
      }
    }

    checkLogin();
  }, [router]);

  return (
    <main>
      <p>Opening Staff Portal...</p>
    </main>
  );
}
