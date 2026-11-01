"use client";

import Image from "next/image";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { CustomerNavigation } from "./CustomerPortalHome";
import styles from "./customer.module.css";
import { PortalIcon } from "./PortalIcon";

type AllergenStatus = "free" | "contains" | "may_contain";

type AllergenSide = {
  id: number;
  name: string;
  milkStatus: AllergenStatus;
  nutsStatus: AllergenStatus;
  eggStatus: AllergenStatus;
  glutenStatus: AllergenStatus;
  vegetarian: boolean;
  canMakeVegetarian: boolean;
  canMakeGlutenFree: boolean;
};

type AllergenItem = AllergenSide & {
  category: string;
  ingredients: string | null;
  vegetarianChanges: string | null;
  glutenFreeChanges: string | null;
  sides: AllergenSide[];
};

type Filters = {
  milkFree: boolean;
  nutFree: boolean;
  eggFree: boolean;
  glutenFree: boolean;
  vegetarian: boolean;
  canMakeVegetarian: boolean;
};

const emptyFilters: Filters = {
  milkFree: false,
  nutFree: false,
  eggFree: false,
  glutenFree: false,
  vegetarian: false,
  canMakeVegetarian: false,
};

const allergenIcons: Record<string,string> = { Milk: "water_full", Nuts: "peanut", Egg: "egg", Gluten: "grass" };

function AllergenBadge({ label, status }: { label: string; status: AllergenStatus }) {
  const text = status === "free" ? `${label} free` : status === "contains" ? `Contains ${label}` : `May contain ${label}`;
  return <span className={`allergen-${status} allergen-icon-pill`} title={text} aria-label={text}><PortalIcon value={`material:${allergenIcons[label]}`}/></span>;
}

function matchesFreeFilters(item: AllergenSide, filters: Filters) {
  return (!filters.milkFree || item.milkStatus === "free")
    && (!filters.nutFree || item.nutsStatus === "free")
    && (!filters.eggFree || item.eggStatus === "free")
    && (!filters.glutenFree || item.glutenStatus === "free" || item.canMakeGlutenFree)
    && (!filters.vegetarian || item.vegetarian)
    && (!filters.canMakeVegetarian || item.vegetarian || item.canMakeVegetarian);
}

export default function AllergenMenu() {
  const [items, setItems] = useState<AllergenItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [queryInput, setQueryInput] = useState("");
  const [categoryInput, setCategoryInput] = useState("");
  const [filterInput, setFilterInput] = useState<Filters>(emptyFilters);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [filters, setFilters] = useState<Filters>(emptyFilters);

  useEffect(() => {
    let active = true;
    fetch("/api/customer/allergens")
      .then(response => {
        if (!response.ok) throw new Error("Could not load the allergen menu.");
        return response.json() as Promise<AllergenItem[]>;
      })
      .then(data => { if (active) setItems(data); })
      .catch(() => { if (active) setError("The allergen menu could not be loaded. Please try again."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const categories = useMemo(() => [...new Set(items.map(item => item.category))], [items]);
  const filteredItems = useMemo(() => items.filter(item => {
    const term = query.toLowerCase();
    const matchesQuery = !term || [item.name, item.ingredients ?? ""].some(value => value.toLowerCase().includes(term));
    return matchesQuery && (!category || item.category === category) && matchesFreeFilters(item, filters);
  }), [items, query, category, filters]);

  const hasFreeFilter = filters.milkFree || filters.nutFree || filters.eggFree || filters.glutenFree;

  function submitFilters(event: FormEvent) {
    event.preventDefault();
    setQuery(queryInput.trim());
    setCategory(categoryInput);
    setFilters(filterInput);
  }

  function clearFilters() {
    setQueryInput("");
    setCategoryInput("");
    setFilterInput(emptyFilters);
    setQuery("");
    setCategory("");
    setFilters(emptyFilters);
  }

  function toggleFilter(key: keyof Filters) {
    setFilterInput(current => ({ ...current, [key]: !current[key] }));
  }

  return (
    <div className={styles.shell}>
      <CustomerNavigation />
      <main className={styles.main}>
        <div className={styles.contentPanel}>
          <section className={`${styles.hero} ${styles.allergenHero}`}>
            <Image className={styles.heroLogo} src="/assets/images/rocket-pub-sidebar-logo.png" alt="The Rocket Pub Liverpool" width={210} height={210} unoptimized priority />
            <div className={styles.heroCopy}>
              <h1>Allergen Menu</h1>
              <p>Search meals, check allergen status and find safer side choices.</p>
            </div>
          </section>

          <aside className={styles.allergenWarning}>
            <strong>Important allergen information</strong>
            <span>Please speak to a member of staff before ordering if you have an allergy or intolerance. Information can change when ingredients or suppliers change.</span>
          </aside>

          <form className={styles.filterPanel} onSubmit={submitFilters}>
            <div className={styles.searchFields}>
              <label>Search menu<input type="search" value={queryInput} onChange={event => setQueryInput(event.target.value)} placeholder="Search meal or ingredient…" /></label>
              <label>Category<select value={categoryInput} onChange={event => setCategoryInput(event.target.value)}><option value="">All categories</option>{categories.map(value => <option key={value}>{value}</option>)}</select></label>
            </div>
            <div className={styles.filterHeading}><strong>Only show meals that are strictly free from:</strong><span>“May contain” items are excluded from a free-from filter.</span></div>
            <div className={styles.filterGrid}>
              {([
                ["milkFree", "Milk free"], ["nutFree", "Nuts free"], ["eggFree", "Egg free"],
                ["glutenFree", "Gluten / can be made gluten free"], ["vegetarian", "Vegetarian"],
                ["canMakeVegetarian", "Vegetarian / can be made vegetarian"],
              ] as [keyof Filters, string][]).map(([key, label]) => (
                <label className={styles.filterToggle} key={key}><input type="checkbox" checked={filterInput[key]} onChange={() => toggleFilter(key)} /><span>{label}</span></label>
              ))}
            </div>
            <div className={styles.filterActions}><button type="submit">Search / filter</button><button type="button" className={styles.secondaryButton} onClick={clearFilters}>Clear</button></div>
          </form>

          <div className="allergen-status-key"><strong>How to read the allergens</strong><div className="allergen-icon-key">{Object.entries(allergenIcons).map(([label,icon])=><span key={label}><PortalIcon value={`material:${icon}`}/><b>{label}</b></span>)}</div><div><span className="allergen-free">✓ Free</span><span className="allergen-may_contain">? May contain</span><span className="allergen-contains">! Contains</span></div></div>

          {loading && <div className={styles.statusCard}>Loading allergen menu…</div>}
          {error && <div className={styles.errorCard}>{error}</div>}
          {!loading && !error && <section className="staff-allergen-grid customer-shared-allergens" aria-live="polite">
            {filteredItems.map(item => {
              const safeSides = item.sides.filter(side => matchesFreeFilters(side, filters));
              return <article className="staff-allergen-card" key={item.id}>
                <span className="booking-page-kicker">{item.category}</span><h2>{item.name}</h2>
                <div className="staff-allergen-badges"><AllergenBadge label="Milk" status={item.milkStatus} /><AllergenBadge label="Nuts" status={item.nutsStatus} /><AllergenBadge label="Egg" status={item.eggStatus} /><AllergenBadge label="Gluten" status={item.glutenStatus} /></div>
                <div className="staff-diet-row">{item.vegetarian ? <span>Vegetarian</span> : item.canMakeVegetarian ? <span>Can be made vegetarian</span> : null}{item.canMakeGlutenFree&&<span>Can be gluten free</span>}</div>
                {item.canMakeGlutenFree && <div className={styles.preparationNote}><strong>*Can be made gluten free when ordered as such.</strong>{item.glutenFreeChanges && <span>{item.glutenFreeChanges}</span>}</div>}
                {item.category === "Main Meals" && item.sides.length > 0 && <div className={styles.sideBox}><strong>{hasFreeFilter ? "Safer sides for selected allergen filters" : "Available sides"}</strong>{safeSides.length > 0 ? <div className={styles.sideList}>{safeSides.map(side => <span key={side.id}>{side.name}</span>)}</div> : <p>No linked side is strictly free from every selected allergen.</p>}</div>}
                {item.ingredients && <details className={styles.ingredients}><summary>Main ingredients</summary><p>{item.ingredients.split("\n").filter(Boolean).join(", ")}</p></details>}
              </article>;
            })}
            {filteredItems.length === 0 && <div className={styles.statusCard}><strong>No matching meals</strong><p>Try removing a filter.</p></div>}
          </section>}
        </div>
      </main>
    </div>
  );
}
