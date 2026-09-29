"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { useState } from "react";
import styles from "./customer.module.css";

const links = [
  { href: "/", label: "Home", icon: "⌂" },
  { href: "/customer/food-menu", label: "Food Menu", icon: "▤" },
  { href: "/customer/christmas-menu", label: "Christmas Menu", icon: "★" },
  { href: "/customer/allergens", label: "Allergens", icon: "A" },
  { href: "/staff/login", label: "Staff Login", icon: "→" },
];

export function CustomerNavigation() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      <aside className={`${styles.sidebar} ${open ? styles.sidebarOpen : ""}`}>
        <button
          className={styles.menuButton}
          type="button"
          aria-label={open ? "Close navigation" : "Open navigation"}
          aria-expanded={open}
          onClick={() => setOpen(value => !value)}
        >
          <span /><span /><span />
        </button>

        <div className={styles.brand}>
          <Image
            src="/assets/images/rocket-pub-sidebar-logo.png"
            alt="The Rocket Pub Liverpool"
            width={152}
            height={118}
            unoptimized
          />
          <h2>Customer Portal</h2>
        </div>

        <nav className={styles.links}>
          {links.map(link => (
            <Link key={link.href} href={link.href} className={pathname === link.href || (link.href === "/" && pathname === "/customer") ? styles.active : ""} onClick={() => setOpen(false)}>
              <span className={styles.icon} aria-hidden="true">{link.icon}</span>
              <span className={styles.linkText}>{link.label}</span>
            </Link>
          ))}
        </nav>
      </aside>

      {open && <button className={styles.backdrop} type="button" aria-label="Close navigation" onClick={() => setOpen(false)} />}
    </>
  );
}

export default function CustomerPortalHome() {
  return (
    <div className={styles.shell}>
      <CustomerNavigation />
      <main className={styles.main}>
        <div className={styles.contentPanel}>
          <section className={styles.hero}>
            <Image
              className={styles.heroLogo}
              src="/assets/images/rocket-pub-sidebar-logo.png"
              alt="The Rocket Pub Liverpool"
              width={210}
              height={210}
              unoptimized
              priority
            />
            <div className={styles.heroCopy}>
              <h1>The Rocket Pub</h1>
              <p className={styles.heroSubtitle}>Customer Portal</p>
              <p>Menus, allergen information and table bookings in one place.</p>
            </div>
          </section>

          <a className={styles.callButton} href="tel:+441512594694">
            <span className={styles.callIcon} aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.79 19.79 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.69 2.8a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.9.33 1.84.56 2.8.69A2 2 0 0 1 22 16.92Z" />
              </svg>
            </span>
            <span>
              <strong>Book your table with us</strong>
              <small>Call 0151 259 4694</small>
            </span>
          </a>

          <section className={styles.cards} aria-label="Customer options">
            <Link className={styles.card} href="/customer/food-menu">
              <span className={styles.cardIcon}>PDF</span>
              <span><strong>View Food Menu</strong><small>Open our current food menu.</small></span>
              <span className={styles.arrow} aria-hidden="true">→</span>
            </Link>
            <Link className={styles.card} href="/customer/allergens">
              <span className={styles.cardIcon}>A</span>
              <span><strong>Allergen Menu</strong><small>Search dishes and check allergen information.</small></span>
              <span className={styles.arrow} aria-hidden="true">→</span>
            </Link>
            <Link className={`${styles.card} ${styles.christmasCard}`} href="/customer/christmas-menu">
              <span className={styles.cardIcon} aria-hidden="true">★</span>
              <span>
                <strong>View Our Christmas Menu 🎄</strong>
                <small>Prebook your Christmas dinner with us</small>
              </span>
              <span className={styles.arrow} aria-hidden="true">→</span>
            </Link>
          </section>
        </div>
      </main>
    </div>
  );
}
