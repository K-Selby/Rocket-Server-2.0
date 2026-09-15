"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function StaffHomePage() {
  const router = useRouter();

  useEffect(() => {
    async function checkLogin() {
      try {
        const response = await fetch("/api/auth/me", { credentials: "include" });
        router.replace(response.ok ? "/staff/rota" : "/staff/login");
      } catch {
        router.replace("/staff/login");
      }
    }

    checkLogin();
  }, [router]);

  return <main><p>Opening Staff Portal...</p></main>;
}
