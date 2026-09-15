"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { useState } from "react";
import styles from "./customer.module.css";

const links = [
  { href: "/", label: "Home", icon: "⌂" },
  { href: "/customer/food-menu", label: "Food Menu", icon: "▤" },
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
              <h1>Food Menu &amp; Allergens</h1>
              <p>Choose what you would like to view.</p>
            </div>
          </section>

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
          </section>
        </div>
      </main>
    </div>
  );
}
