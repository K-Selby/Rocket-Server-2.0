"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import { CustomerNavigation } from "./CustomerPortalHome";
import styles from "./customer.module.css";

type Menu = { slug: string; title: string; description: string | null; pdfUrl: string };

export default function CustomerMenuPage({ slug }: { slug: string }) {
  const [menu, setMenu] = useState<Menu | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`/api/customer/menus/${encodeURIComponent(slug)}`, { cache: "no-store" })
      .then(async response => {
        if (!response.ok) throw new Error();
        setMenu(await response.json());
      })
      .catch(() => {
        if (slug === "food") {
          setMenu({ slug: "food", title: "Food Menu", description: "Open our current food menu.", pdfUrl: "/assets/menus/the-rocket-pub-food-menu.pdf" });
        } else if (slug === "christmas") {
          setMenu({ slug: "christmas", title: "Christmas Menu", description: "Prebook your Christmas dinner with us.", pdfUrl: "/assets/menus/rocket-christmas-menu.pdf" });
        } else {
          setError("This menu is not currently available.");
        }
      });
  }, [slug]);

  return (
    <div className={styles.shell}>
      <CustomerNavigation />
      <main className={styles.main}>
        <div className={styles.contentPanel}>
          <section className={`${styles.hero} ${styles.allergenHero}`}>
            <Image className={styles.heroLogo} src="/assets/images/rocket-pub-sidebar-logo.png" alt="The Rocket Pub Liverpool" width={210} height={210} unoptimized priority />
            <div className={styles.heroCopy}>
              <h1>{menu?.title || "Menu"}</h1>
              <p>{menu?.description || "View the latest menu from The Rocket Pub."}</p>
            </div>
          </section>

          {error ? <section className={styles.menuUnavailable}><h2>Menu unavailable</h2><p>{error}</p></section> : menu ? (
            <section className={styles.pdfMenuPanel}>
              <div className={styles.pdfMenuActions}>
                <a href={menu.pdfUrl} target="_blank" rel="noreferrer">Open full-screen PDF</a>
                <a href={menu.pdfUrl} download>Download PDF</a>
              </div>
              <iframe className={styles.pdfMenuFrame} src={menu.pdfUrl} title={`${menu.title} PDF`} />
              {menu.slug === "christmas" && <p className={styles.christmasBookingNotice}>Christmas menu bookings only</p>}
            </section>
          ) : <section className={styles.menuUnavailable}><p>Loading menu...</p></section>}
        </div>
      </main>
    </div>
  );
}
