"use client";

import { FormEvent, useEffect, useState } from "react";

type Option = { id: number; name?: string; number?: string; capacity?: number };
type Customer = {
  id: number; name: string; phone: string; email: string | null; notes: string | null;
  preferred_area_id: number | null; preferred_area: string | null;
  preferred_table_id: number | null; preferred_table: string | null;
  prefers_near_tv: number; avoids_bench: number; booking_count: number; last_booking_date: string | null;
};

export default function CustomersPage() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [areas, setAreas] = useState<Option[]>([]);
  const [tables, setTables] = useState<Option[]>([]);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Customer | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    fetch(`/api/booking/customers?q=${encodeURIComponent(search)}`, { credentials: "include" })
      .then(response => { if (!response.ok) throw new Error(); return response.json(); })
      .then(data => { if (active) { setCustomers(data.customers); setAreas(data.areas); setTables(data.tables); } })
      .catch(() => { if (active) setError("Customers could not be loaded."); });
    return () => { active = false; };
  }, [search]);

  function submitSearch(event: FormEvent) { event.preventDefault(); setError(""); setSearch(query.trim()); }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) return;
    const form = new FormData(event.currentTarget);
    const optionalNumber = (name: string) => form.get(name) ? Number(form.get(name)) : null;
    const response = await fetch(`/api/booking/customers/${editing.id}`, {
      method: "PUT", credentials: "include", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: form.get("name"), phone: form.get("phone"), email: form.get("email"), notes: form.get("notes"),
        preferredAreaId: optionalNumber("preferredAreaId"), preferredTableId: optionalNumber("preferredTableId"),
        prefersNearTv: form.get("prefersNearTv") === "on", avoidsBench: form.get("avoidsBench") === "on",
      }),
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) return setError(data?.message || "The customer could not be saved.");
    setEditing(null); setNotice("Customer updated."); setSearch(current => `${current} `); setTimeout(() => setSearch(value => value.trim()), 0);
  }

  async function remove(customer: Customer) {
    if (!window.confirm(`Delete ${customer.name}? This will permanently delete their current bookings, repeat bookings and complete booking history.`)) return;
    setError(""); setNotice("");
    const response = await fetch(`/api/booking/customers/${customer.id}`, { method: "DELETE", credentials: "include" });
    const data = await response.json().catch(() => null);
    if (!response.ok) return setError(data?.message || "The customer could not be deleted.");
    setCustomers(current => current.filter(item => item.id !== customer.id));
    setNotice(`${customer.name} and all linked bookings and history were deleted.`);
  }

  return <main className="booking-main"><section className="booking-page">
    <header className="booking-heading"><div><span className="booking-page-kicker">Booking Portal</span><h1>Customers</h1><p>Search returning customers and update their saved preferences.</p></div></header>
    <form className="booking-search" onSubmit={submitSearch}><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search by name or phone number" /><button type="submit">Search</button>{search && <button type="button" onClick={() => { setQuery(""); setSearch(""); }}>Clear</button>}</form>
    {error && <p className="booking-alert">{error}</p>}{notice && <p className="booking-notice">{notice}</p>}
    <div className="customer-list">{customers.map(customer => <article className="customer-row" key={customer.id}>
      <div><h2>{customer.name}</h2><p>{customer.phone}{customer.email ? ` · ${customer.email}` : ""}</p></div>
      <div className="customer-preferences"><span>{customer.booking_count} booking{customer.booking_count === 1 ? "" : "s"}</span>{customer.preferred_area && <span>{customer.preferred_area}</span>}{customer.preferred_table && <span>Table {customer.preferred_table}</span>}</div>
      <div className="customer-actions"><button type="button" onClick={() => { setError(""); setNotice(""); setEditing(customer); }}>Edit</button><button className="danger" type="button" onClick={() => void remove(customer)}>Delete</button></div>
    </article>)}</div>
    {customers.length === 0 && !error && <div className="booking-empty">No matching customers.</div>}

    {editing && <div className="booking-modal-backdrop" role="presentation"><form className="booking-modal" onSubmit={save}>
      <div className="booking-modal-heading"><h2>Edit {editing.name}</h2><button type="button" onClick={() => setEditing(null)}>×</button></div>
      <div className="booking-form-grid"><label>Name<input name="name" defaultValue={editing.name} required /></label><label>Phone<input name="phone" defaultValue={editing.phone} required /></label><label>Email<input name="email" type="email" defaultValue={editing.email ?? ""} /></label><label>Preferred area<select name="preferredAreaId" defaultValue={editing.preferred_area_id ?? ""}><option value="">No preference</option>{areas.map(area => <option value={area.id} key={area.id}>{area.name}</option>)}</select></label><label>Preferred table<select name="preferredTableId" defaultValue={editing.preferred_table_id ?? ""}><option value="">No preference</option>{tables.map(table => <option value={table.id} key={table.id}>Table {table.number} · {table.capacity} seats</option>)}</select></label><label className="booking-check"><input name="prefersNearTv" type="checkbox" defaultChecked={Boolean(editing.prefers_near_tv)} /> Prefer near a TV</label><label className="booking-check"><input name="avoidsBench" type="checkbox" defaultChecked={Boolean(editing.avoids_bench)} /> Avoid bench seating</label><label className="booking-form-wide">Notes<textarea name="notes" defaultValue={editing.notes ?? ""} rows={4} /></label></div>
      <div className="booking-modal-actions"><button type="button" onClick={() => setEditing(null)}>Cancel</button><button className="primary-button" type="submit">Save customer</button></div>
    </form></div>}
  </section></main>;
}
