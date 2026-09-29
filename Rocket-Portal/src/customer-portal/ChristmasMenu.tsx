import Image from "next/image";
import { CustomerNavigation } from "./CustomerPortalHome";
import styles from "./customer.module.css";

export default function ChristmasMenu() {
  return (
    <div className={styles.shell}>
      <CustomerNavigation />
      <main className={styles.main}>
        <div className={styles.contentPanel}>
          <section className={`${styles.hero} ${styles.allergenHero}`}>
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
              <h1>Christmas Menu</h1>
              <p>Celebrate Christmas with us at The Rocket Pub.</p>
            </div>
          </section>

          <section className={styles.christmasMenuPanel}>
            <a href="/assets/menus/rocket-christmas-menu.pdf" target="_blank" rel="noreferrer" aria-label="Open the Christmas menu PDF">
              <Image
                className={styles.christmasMenuImage}
                src="/assets/menus/rocket-christmas-menu.png"
                alt="The Rocket Pub Christmas menu"
                width={1637}
                height={1157}
                unoptimized
                priority
              />
            </a>
            <p className={styles.christmasBookingNotice}>Christmas menu bookings only</p>
          </section>
        </div>
      </main>
    </div>
  );
}
