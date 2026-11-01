"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import styles from "./customer.module.css";
import { PortalIcon } from "./PortalIcon";
import { CallToBook } from "./CallToBook";

type CustomerMenu = {
  id: number;
  slug: string;
  title: string;
  description: string | null;
  primary: boolean;
  sidebarName: string;
  icon: string;
  cardColor: string;
  textColor: string;
  pdfUrl: string;
};

type PortalButton = { key: string; title: string; description: string; sidebarName: string; icon: string; cardColor: string; textColor: string };
const defaultAllergens: PortalButton = { key: "allergens", title: "Allergen Menu", description: "Search dishes and check allergen information.", sidebarName: "Allergens", icon: "material:allergy", cardColor: "#ffffff", textColor: "#173326" };

const defaultMenus: CustomerMenu[] = [
  { id: -1, slug: "food", title: "View Food Menu", sidebarName: "Food Menu", icon: "pdf", description: "Open our current food menu.", primary: true, cardColor: "#ffffff", textColor: "#173326", pdfUrl: "/assets/menus/the-rocket-pub-food-menu.pdf" },
  { id: -2, slug: "christmas", title: "View Our Christmas Menu 🎄", sidebarName: "Christmas Menu", icon: "star", description: "Prebook your Christmas dinner with us", primary: false, cardColor: "#7f202a", textColor: "#fffaf0", pdfUrl: "/assets/menus/rocket-christmas-menu.pdf" },
];

const baseLinks = [
  { href: "/", label: "Home", icon: "material:home" },
];

function useCustomerMenus() {
  const [menus, setMenus] = useState<CustomerMenu[]>(defaultMenus);
  useEffect(() => {
    let active = true;
    fetch("/api/customer/menus", { cache: "no-store" })
      .then(response => response.ok ? response.json() : Promise.reject())
      .then(result => { if (active && result.length) setMenus(result); })
      .catch(() => { /* Keep the built-in menus visible while the API restarts. */ });
    return () => { active = false; };
  }, []);
  return menus;
}

function useAllergenButton() {
  const [button, setButton] = useState(defaultAllergens);
  useEffect(() => {
    let active = true;
    fetch("/api/customer/portal-buttons", { cache: "no-store" }).then(response => response.ok ? response.json() : Promise.reject())
      .then(result => { const allergens = result.find((item: PortalButton) => item.key === "allergens"); if (active && allergens) setButton(allergens); })
      .catch(() => {});
    return () => { active = false; };
  }, []);
  return button;
}

export function CustomerNavigation() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const menus = useCustomerMenus();
  const allergens = useAllergenButton();
  const links = [
    ...baseLinks,
    ...menus.map(menu => ({ href: menu.pdfUrl, label: menu.sidebarName, icon: menu.icon })),
    { href: "/customer/allergens", label: allergens.sidebarName, icon: allergens.icon },
  ];

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
              <span className={styles.icon}><PortalIcon value={link.icon} monochrome /></span>
              <span className={styles.linkText}>{link.label}</span>
            </Link>
          ))}
        </nav>

        <div className={styles.sidebarFooter}>
          <Link href="/staff/login" onClick={() => setOpen(false)}>
            <span className={styles.icon} aria-hidden="true">→</span>
            <span className={styles.linkText}>Staff Login</span>
          </Link>
        </div>
      </aside>

      {open && <button className={styles.backdrop} type="button" aria-label="Close navigation" onClick={() => setOpen(false)} />}
    </>
  );
}

export default function CustomerPortalHome() {
  const menus = useCustomerMenus();
  const allergens = useAllergenButton();
  const christmasMenus = menus.filter(menu => menu.slug === "christmas");
  const standardMenus = menus.filter(menu => menu.slug !== "christmas");
  const primaryMenu = standardMenus.find(menu => menu.primary) || standardMenus[0];
  const additionalMenus = standardMenus.filter(menu => menu.id !== primaryMenu?.id);

  const menuCard = (menu: CustomerMenu) => (
    <Link
      className={`${styles.card} ${menu.slug === "christmas" ? styles.christmasCard : ""}`}
      href={menu.pdfUrl}
      key={menu.id}
      style={{ backgroundColor: menu.cardColor, color: menu.textColor }}
    >
      <span className={styles.cardIcon}><PortalIcon value={menu.icon} /></span>
      <span><strong>{menu.title}</strong><small>{menu.description || "Open the current menu."}</small></span>
      <span className={styles.arrow} aria-hidden="true">→</span>
    </Link>
  );

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

          <CallToBook className={styles.callButton} iconClassName={styles.callIcon} />


          <section className={styles.cards} aria-label="Customer options">
            {primaryMenu && menuCard(primaryMenu)}
            <Link className={styles.card} href="/customer/allergens" style={{ backgroundColor: allergens.cardColor, color: allergens.textColor }}>
              <span className={styles.cardIcon}><PortalIcon value={allergens.icon} /></span>
              <span><strong>{allergens.title}</strong><small>{allergens.description}</small></span>
              <span className={styles.arrow} aria-hidden="true">→</span>
            </Link>
            {additionalMenus.map(menuCard)}
            {christmasMenus.map(menuCard)}
          </section>
        </div>
      </main>
    </div>
  );
}
