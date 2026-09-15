"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useInbox } from "../context/InboxContext";
import { useCurrentUser } from "../context/CurrentUserContext";
import RocketLogo from "./RocketBrand";

const API = "";
const STAFF_PORTAL = "/staff";
const BOOKING_PORTAL = process.env.NEXT_PUBLIC_BOOKING_PORTAL_URL || "https://rocketpubserver.co.uk/booking/dashboard";

function staffPortalUrl(path: string) {
  return `${STAFF_PORTAL}${path}`;
}

const links = [
  { href: "/rota", label: "Rota", icon: "calendar" },
  { href: "/diary", label: "Staff Diary", icon: "diary" },
  { href: "/inbox", label: "Inbox", icon: "inbox" },
];

function NavIcon({ name }: { name: string }) {
  const paths: Record<string, React.ReactNode> = {
    calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01" /></>,
    diary: <><path d="M6 3h12a2 2 0 0 1 2 2v16H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" /><path d="M8 3v18M11 8h5M11 12h5M11 16h3" /></>,
    inbox: <><path d="M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" /><path d="m2 7 10 7L22 7" /></>,
    manager: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
    external: <><path d="M15 3h6v6M10 14 21 3" /><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /></>,
  };

  return (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {paths[name]}
    </svg>
  );
}

function roleLabel(role: "STAFF" | "MANAGER" | "ADMIN") {
  if (role === "ADMIN") return "Admin";
  if (role === "MANAGER") return "Manager";
  return "Staff";
}

export default function Navigation() {
  const pathname = usePathname();
  const staffPath = pathname.startsWith(STAFF_PORTAL)
    ? pathname.slice(STAFF_PORTAL.length) || "/"
    : pathname;
  const { count } = useInbox();
  const { currentUser, clearCurrentUser } = useCurrentUser();

  const [accountOpen, setAccountOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const canManage = currentUser?.role === "MANAGER" || currentUser?.role === "ADMIN";
  const visibleLinks = canManage ? [...links, { href: "/manager", label: "Manager", icon: "manager" }] : links;

  const badge = count > 0 ? (
    <span className="navigation-badge" aria-label={`${count} Inbox items needing attention`}>
      {count}
    </span>
  ) : null;

  async function logout() {
    setLoggingOut(true);

    try {
      await fetch(`${API}/api/auth/logout`, { method: "POST", credentials: "include" });
    } finally {
      clearCurrentUser();
      window.location.assign(staffPortalUrl("/login"));
    }
  }

  return (
    <>
      <aside className={`desktop-sidebar ${mobileMenuOpen ? "mobile-sidebar-open" : ""}`}>
        <button
          type="button"
          className="mobile-sidebar-toggle"
          aria-label={mobileMenuOpen ? "Close navigation" : "Open navigation"}
          aria-expanded={mobileMenuOpen}
          onClick={() => setMobileMenuOpen(open => !open)}
        >
          <span />
          <span />
          <span />
        </button>

        <div className="sidebar-title">
          <RocketLogo className="sidebar-logo" compact decorative />
          <div className="sidebar-brand-copy">
            <h2>Staff Portal</h2>
          </div>
        </div>

        {currentUser && (
          <div className="sidebar-account">
            <button
              className="sidebar-account-trigger"
              type="button"
              aria-expanded={accountOpen}
              onClick={() => setAccountOpen(open => !open)}
            >
              <span className="sidebar-avatar" aria-hidden="true">
                {currentUser.name.trim().charAt(0).toUpperCase() || "U"}
              </span>
              <span className="sidebar-account-copy">
                  <div className="account-menu-name">
                    {currentUser.name}
                    {!currentUser.emailVerified && (
                      <span className="account-attention-badge" aria-label="Email setup needed">!</span>
                    )}
                  </div>
                  <span className="sidebar-account-role">
                    {roleLabel(currentUser.role)}
                  </span>
              </span>

                <span className="sidebar-account-chevron" aria-hidden="true">
                  {accountOpen ? "▾" : "▸"}
                </span>
            </button>

            {accountOpen && (
              <div className="sidebar-account-menu">
                <a href={BOOKING_PORTAL}>Booking Portal</a>

                <Link
                  href={staffPortalUrl("/settings")}
                  className={staffPath === "/settings" ? "active" : ""}
                >
                  Account settings
                  {!currentUser.emailVerified && <span className="account-link-warning" aria-hidden="true">!</span>}
                </Link>

                <button
                  type="button"
                  onClick={logout}
                  disabled={loggingOut}
                >
                  {loggingOut ? "Logging out..." : "Log out"}
                </button>
              </div>
            )}
          </div>
        )}

        <nav className="sidebar-links">
          {visibleLinks.map(link => (
            <Link
              key={link.href}
              href={staffPortalUrl(link.href)}
              className={staffPath === link.href ? "active" : ""}
              aria-label={link.label}
              onClick={() => setMobileMenuOpen(false)}
            >
              <NavIcon name={link.icon} />
              <span className="sidebar-link-label">{link.label}</span>
              {link.href === "/inbox" && badge}
            </Link>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <a href={BOOKING_PORTAL} aria-label="Booking Portal">
            <NavIcon name="external" />
            <span className="sidebar-link-label">Booking Portal</span>
            <span className="sidebar-link-label" aria-hidden="true">→</span>
          </a>
        </div>
      </aside>

      {mobileMenuOpen && (
        <button
          type="button"
          className="mobile-sidebar-backdrop"
          aria-label="Close navigation"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}

      <nav className="mobile-bottom-nav">
        {[...visibleLinks, { href: "/settings", label: "Account" }].map(link => (
          <Link key={link.href} href={staffPortalUrl(link.href)} className={staffPath === link.href ? "active" : ""}>
            {link.label === "Staff Diary" ? "Diary" : link.label}
            {link.href === "/inbox" && badge}
            {link.href === "/settings" && !currentUser?.emailVerified && (
              <span className="mobile-account-warning" aria-label="Email setup needed">!</span>
            )}
          </Link>
        ))}
        <a href={BOOKING_PORTAL}>Bookings</a>
      </nav>
    </>
  );
}
