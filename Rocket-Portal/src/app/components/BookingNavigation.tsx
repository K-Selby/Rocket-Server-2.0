"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useCurrentUser } from "../context/CurrentUserContext";
import RocketLogo from "./RocketBrand";

type BookingLink = {
  href: string;
  label: string;
  icon: "dashboard" | "booking" | "party" | "customer" | "allergen" | "table" | "archive" | "layout" | "external";
  managerOnly?: boolean;
};

const bookingLinks: BookingLink[] = [
  { href: "/booking", label: "Dashboard", icon: "dashboard" },
  { href: "/booking/bookings", label: "Table Bookings", icon: "booking" },
  { href: "/booking/large-parties", label: "Large Parties", icon: "party" },
  { href: "/booking/customers", label: "Customers", icon: "customer" },
  { href: "/booking/allergens", label: "Allergen Menu", icon: "allergen" },
  { href: "/booking/tables", label: "Tables", icon: "table", managerOnly: true },
  { href: "/booking/archive", label: "Archive", icon: "archive", managerOnly: true },
  { href: "/booking/table-layout", label: "Table Layout", icon: "layout", managerOnly: true },
];

function BookingIcon({ name }: { name: BookingLink["icon"] }) {
  const paths: Record<BookingLink["icon"], React.ReactNode> = {
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    booking: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 10h18M8 14h8M8 18h5" /></>,
    party: <><circle cx="8" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M2 21v-2a6 6 0 0 1 12 0v2M14 16a5 5 0 0 1 8 4v1" /></>,
    customer: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>,
    allergen: <><path d="M12 3c4 4 6 7 6 11a6 6 0 0 1-12 0c0-4 2-7 6-11Z" /><path d="M9 15c1 1 2 1.5 3 1.5s2-.5 3-1.5" /></>,
    table: <><path d="M4 7h16M6 7l-2 14M18 7l2 14M8 12h8" /></>,
    archive: <><path d="M3 6h18M5 6v15h14V6M4 3h16v3M9 11h6" /></>,
    layout: <><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M9 3v18M9 10h12M15 10v11" /></>,
    external: <><path d="M15 3h6v6M10 14 21 3" /><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /></>,
  };

  return <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

export default function BookingNavigation() {
  const pathname = usePathname();
  const router = useRouter();
  const { currentUser, clearCurrentUser } = useCurrentUser();
  const [accountOpen, setAccountOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const canManage = currentUser?.role === "MANAGER" || currentUser?.role === "ADMIN";
  const visibleLinks = bookingLinks.filter(link => !link.managerOnly || canManage);

  function toggleDesktopSidebar() {
    setSidebarCollapsed(current => !current);
  }

  function active(href: string) {
    return href === "/booking" ? pathname === href : pathname.startsWith(href);
  }

  async function logout() {
    setLoggingOut(true);
    try {
      await fetch("/api/auth/logout", { method: "POST", credentials: "include" });
    } finally {
      clearCurrentUser();
      router.push("/staff/login");
    }
  }

  return <>
    <aside className={`desktop-sidebar booking-sidebar ${sidebarCollapsed ? "sidebar-collapsed" : ""} ${mobileMenuOpen ? "mobile-sidebar-open" : ""}`}>
      <button type="button" className="desktop-sidebar-toggle" aria-label={sidebarCollapsed ? "Expand navigation" : "Collapse navigation"} aria-expanded={!sidebarCollapsed} onClick={toggleDesktopSidebar}><span /><span /><span /></button>
      <button type="button" className="mobile-sidebar-toggle" aria-label={mobileMenuOpen ? "Close navigation" : "Open navigation"} aria-expanded={mobileMenuOpen} onClick={() => setMobileMenuOpen(open => !open)}><span /><span /><span /></button>
      <div className="sidebar-title"><RocketLogo className="sidebar-logo" compact decorative /><div className="sidebar-brand-copy"><h2>Booking Portal</h2></div></div>

      {currentUser && <div className="sidebar-account">
        <button className="sidebar-account-trigger" type="button" aria-expanded={accountOpen} onClick={() => setAccountOpen(open => !open)}>
          <span className="sidebar-avatar">{currentUser.name.trim().charAt(0).toUpperCase() || "U"}</span>
          <span className="sidebar-account-copy"><strong>{currentUser.name}</strong><span className="sidebar-account-role">{currentUser.role === "STAFF" ? "Staff" : currentUser.role === "MANAGER" ? "Manager" : "Admin"}</span></span>
          <span className="sidebar-account-chevron">{accountOpen ? "▾" : "▸"}</span>
        </button>
        {accountOpen && <div className="sidebar-account-menu"><Link href="/staff/settings">Account settings</Link><button type="button" onClick={logout} disabled={loggingOut}>{loggingOut ? "Logging out..." : "Log out"}</button></div>}
      </div>}

      <nav className="sidebar-links booking-sidebar-links">
        {visibleLinks.map(link => <Link key={link.href} href={link.href} className={active(link.href) ? "active" : ""} onClick={() => setMobileMenuOpen(false)}><BookingIcon name={link.icon} /><span className="sidebar-link-label">{link.label}</span></Link>)}
      </nav>

      <div className="sidebar-bottom"><Link href="/staff/rota"><BookingIcon name="external" /><span className="sidebar-link-label">Staff Portal</span><span className="sidebar-link-label">→</span></Link></div>
    </aside>
    {mobileMenuOpen && <button type="button" className="mobile-sidebar-backdrop" aria-label="Close navigation" onClick={() => setMobileMenuOpen(false)} />}
  </>;
}
