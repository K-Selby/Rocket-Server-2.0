"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import Navigation from "./Navigation";
import { useCurrentUser } from "../context/CurrentUserContext";

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { currentUser, loadingUser } = useCurrentUser();
  const staffPath = pathname.startsWith("/staff")
    ? pathname.slice("/staff".length) || "/"
    : pathname;
  const isStaffPage = pathname.startsWith("/staff");
  const isLoginPage = staffPath === "/login";
  const isSetupPage = staffPath === "/setup";
  const isAuthPage = isLoginPage || isSetupPage;

  useEffect(() => {
    if (!isStaffPage) return;
    if (loadingUser) return;
    if (!currentUser && !isLoginPage) router.replace("/staff/login");
    else if (currentUser?.mustChangePassword && !isSetupPage) router.replace("/staff/setup");
    else if (currentUser && isLoginPage) router.replace(currentUser.mustChangePassword ? "/staff/setup" : "/staff/rota");
  }, [currentUser, loadingUser, isLoginPage, isSetupPage, isStaffPage, router]);

  if (!isStaffPage) return <>{children}</>;
  // Login and first-time setup are separate from the portal layout.
  if (currentUser?.mustChangePassword && !isSetupPage) return null;
  if (isAuthPage) return <>{children}</>;

  if (loadingUser) return <div className="portal-shell"><main><p>Checking sign in...</p></main></div>;
  if (!currentUser) return null;

  return (
    <div className="portal-shell">
      <Navigation />
      {children}
    </div>
  );
}
