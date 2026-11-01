"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import { PortalIcon, normalizePortalIcon } from "@/customer-portal/PortalIcon";
import { CallToBook } from "@/customer-portal/CallToBook";

type Menu = {
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

type Editor = Menu;
type Template = { id: number; createdAt: string };
const ALLERGEN_ID = -9007199254740991;

const iconGroups = [
  { name: "Menus and food", icons: ["picture_as_pdf", "restaurant_menu", "menu_book", "dinner_dining", "restaurant", "lunch_dining", "local_pizza", "bakery_dining", "ramen_dining", "icecream", "cake", "brunch_dining"] },
  { name: "Drinks", icons: ["local_bar", "sports_bar", "wine_bar", "liquor", "coffee", "emoji_food_beverage", "local_cafe", "water_full"] },
  { name: "Information", icons: ["info", "allergy", "event", "calendar_month", "schedule", "call", "location_on", "celebration", "favorite", "star"] },
];

function PortalPreview({ menus, editor, onSelect }: { menus: Menu[]; editor: Editor | null; onSelect: (menu: Menu) => void }) {
  const previewMenus = menus.map(menu => editor?.id === menu.id ? { ...menu, ...editor } : menu);
  if (editor?.id === 0) previewMenus.push(editor);
  const allergens = previewMenus.find(menu => menu.slug === "allergens");
  const standardMenus = previewMenus.filter(menu => menu.slug !== "christmas" && menu.slug !== "allergens");
  const christmasMenus = previewMenus.filter(menu => menu.slug === "christmas");
  const primaryMenu = standardMenus.find(menu => menu.primary) || standardMenus[0];
  const additionalMenus = standardMenus.filter(menu => menu.id !== primaryMenu?.id);

  function previewCard(menu: Menu) {
    return (
      <button
        className={`customer-portal-preview-card ${menu.slug === "christmas" ? "customer-portal-preview-christmas" : ""} ${editor?.id === menu.id ? "customer-portal-preview-selected" : ""}`}
        key={menu.id}
        onClick={() => onSelect(menu)}
        style={{ backgroundColor: menu.cardColor, color: menu.textColor }}
        type="button"
      >
        <span className="customer-portal-preview-card-icon"><PortalIcon value={menu.icon} /></span>
        <span><strong>{menu.title}</strong><small>{menu.description || "Open the current menu."}</small></span>
        <span aria-hidden="true">→</span>
      </button>
    );
  }

  return (
    <section className="customer-portal-visual-editor" aria-label="Interactive customer portal preview">
      <div className="customer-portal-preview-help">
        <strong>Interactive customer portal preview</strong>
        <span>Select any menu button in the preview to edit its wording, colours or PDF.</span>
      </div>
      <div className="customer-portal-preview-screen">
        <aside className="customer-portal-preview-sidebar">
          <Image src="/assets/images/rocket-pub-sidebar-logo.png" alt="" width={110} height={88} unoptimized />
          <strong>Customer Portal</strong>
          <span className="customer-portal-preview-nav-active">⌂ Home</span>
          {previewMenus.filter(menu => menu.slug !== "allergens").map(menu => (
            <button type="button" key={menu.id} onClick={() => onSelect(menu)}><PortalIcon value={menu.icon} monochrome /> {menu.sidebarName}</button>
          ))}
          {allergens && <button type="button" onClick={() => onSelect(allergens)}><PortalIcon value={allergens.icon} monochrome /> {allergens.sidebarName}</button>}
          <span className="customer-portal-preview-login">→ &nbsp; Staff Login</span>
        </aside>
        <div className="customer-portal-preview-page">
          <div className="customer-portal-preview-content">
            <div className="customer-portal-preview-hero">
              <Image src="/assets/images/rocket-pub-sidebar-logo.png" alt="" width={120} height={100} unoptimized />
              <div><strong>The Rocket Pub</strong><b>Customer Portal</b><small>Menus, allergen information and table bookings in one place.</small></div>
            </div>
            <CallToBook className="customer-portal-preview-call" iconClassName="customer-portal-preview-call-icon" />
            <div className="customer-portal-preview-cards">
              {primaryMenu && previewCard(primaryMenu)}
              {allergens && previewCard(allergens)}
              {additionalMenus.map(previewCard)}
              {christmasMenus.map(previewCard)}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function CustomerMenusPage() {
  const [menus, setMenus] = useState<Menu[]>([]);
  const [originalMenus, setOriginalMenus] = useState<Menu[]>([]);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [files, setFiles] = useState<Record<number, File>>({});
  const [templates, setTemplates] = useState<Template[]>([]);
  const [templatePreview, setTemplatePreview] = useState<{ id: number; menus: Menu[] } | null>(null);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  function updatePreview(field: "title" | "sidebarName" | "description" | "icon" | "cardColor" | "textColor", value: string) {
    if (!editor) return;
    const changed = { ...editor, [field]: field === "icon" ? normalizePortalIcon(value) : value };
    setEditor(changed);
    setMenus(items => items.map(menu => menu.id === changed.id ? changed : menu));
    setDirty(true);
  }

  async function load() {
    const [response, buttonResponse] = await Promise.all([
      fetch("/api/booking/menus", { credentials: "include", cache: "no-store" }),
      fetch("/api/booking/menus/portal-buttons", { credentials: "include", cache: "no-store" }),
    ]);
    if (!response.ok) throw new Error("Customer menus could not be loaded. Manager access is required.");
    const result: Menu[] = await response.json();
    if (buttonResponse.ok) {
      const buttons = await buttonResponse.json();
      const allergens = buttons.find((button: { key: string }) => button.key === "allergens");
      if (allergens) result.push({ id: ALLERGEN_ID, slug: "allergens", primary: true, pdfUrl: "/customer/allergens", ...allergens });
    }
    setMenus(result);
    setOriginalMenus(result);
    setFiles({});
    setDirty(false);
    const versions = await fetch("/api/booking/menus/templates", { credentials: "include", cache: "no-store" });
    if (versions.ok) setTemplates(await versions.json());
  }

  useEffect(() => {
    let active = true;
    async function initialise() {
      try {
        const [response, buttonResponse] = await Promise.all([
          fetch("/api/booking/menus", { credentials: "include", cache: "no-store" }),
          fetch("/api/booking/menus/portal-buttons", { credentials: "include", cache: "no-store" }),
        ]);
        if (!response.ok) throw new Error("Customer menus could not be loaded. Manager access is required.");
        const result: Menu[] = await response.json();
        if (buttonResponse.ok) {
          const buttons = await buttonResponse.json();
          const allergens = buttons.find((button: { key: string }) => button.key === "allergens");
          if (allergens) result.push({ id: ALLERGEN_ID, slug: "allergens", primary: true, pdfUrl: "/customer/allergens", ...allergens });
        }
        if (active) {
          setMenus(result);
          setOriginalMenus(result);
          const versions = await fetch("/api/booking/menus/templates", { credentials: "include", cache: "no-store" });
          if (versions.ok) setTemplates(await versions.json());
        }
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : "Customer menus could not be loaded.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void initialise();
    return () => { active = false; };
  }, []);

  async function publish() {
    setError("");
    setNotice("");
    const missingPdf = menus.find(menu => menu.id < 0 && menu.slug !== "allergens" && !files[menu.id]);
    if (missingPdf) {
      setError(`Choose a PDF for ${missingPdf.title || "the new menu"}.`);
      return;
    }
    setSaving(true);
    const snapshot = await fetch("/api/booking/menus/templates", { method: "POST", credentials: "include" });
    if (!snapshot.ok) {
      setError("A restorable version could not be created, so nothing was published.");
      setSaving(false);
      return;
    }
    try {
      for (const original of originalMenus.filter(item => !menus.some(menu => menu.id === item.id))) {
        const response = await fetch(`/api/booking/menus/${original.id}`, { method: "DELETE", credentials: "include" });
        if (!response.ok) throw new Error(`Could not remove ${original.title}.`);
      }
      for (const menu of menus) {
        const data = new FormData();
        data.set("title", menu.title);
        data.set("sidebarName", menu.sidebarName);
        data.set("description", menu.description || "");
        data.set("icon", menu.icon);
        data.set("cardColor", menu.cardColor);
        data.set("textColor", menu.textColor);
        if (menu.slug === "allergens") {
          const response = await fetch("/api/booking/menus/portal-buttons/allergens", { method: "PUT", credentials: "include", body: data });
          if (!response.ok) throw new Error("Could not publish the Allergen button.");
          continue;
        }
        if (files[menu.id]) data.set("pdf", files[menu.id]);
        const response = await fetch(menu.id < 0 ? "/api/booking/menus" : `/api/booking/menus/${menu.id}`, {
          method: menu.id < 0 ? "POST" : "PUT", credentials: "include", body: data,
        });
        if (!response.ok) throw new Error(`Could not publish ${menu.title}.`);
      }
      await load();
      setEditor(null);
      setNotice("Customer portal updated. The previous version has been saved.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The portal could not be published.");
    } finally {
      setSaving(false);
    }
  }

  function remove(menu: Menu) {
    if (menu.primary || !window.confirm(`Remove ${menu.title} from the customer portal?`)) return;
    setMenus(items => items.filter(item => item.id !== menu.id));
    if (editor?.id === menu.id) setEditor(null);
    setDirty(true);
  }

  async function restore(id: number) {
    if (!window.confirm("Restore this saved customer portal version?")) return;
    const response = await fetch(`/api/booking/menus/templates/${id}/restore`, { method: "POST", credentials: "include" });
    if (!response.ok) { setError("The saved version could not be restored."); return; }
    await load();
    setEditor(null);
    setNotice("Saved customer portal version restored.");
  }

  async function previewVersion(id: number) {
    const response = await fetch(`/api/booking/menus/templates/${id}`, { credentials: "include", cache: "no-store" });
    if (!response.ok) { setError("The saved version could not be previewed."); return; }
    setTemplatePreview({ id, menus: await response.json() });
  }

  return (
    <main className="booking-main">
      <section className="booking-page">
        <header className="booking-heading">
          <div>
            <span className="booking-page-kicker">Manager tools</span>
            <h1>Customer Portal Editor</h1>
            <p>Customise the public portal menu buttons and replace their PDF files.</p>
          </div>
          <div className="booking-row-actions">
            <button className="customer-portal-add-button" onClick={() => {
              const added: Menu = { id: -Date.now(), slug: "", title: "New menu", description: "", primary: false, sidebarName: "New menu", icon: "PDF", cardColor: "#ffffff", textColor: "#173326", pdfUrl: "" };
              setMenus(items => [...items, added]); setEditor(added); setDirty(true);
            }}>Add menu</button>
            <button className="primary-button" disabled={!dirty || saving} onClick={publish}>{saving ? "Publishing..." : "Update customer portal"}</button>
          </div>
        </header>

        {error && <p className="booking-alert">{error}</p>}
        {notice && <p className="booking-notice">{notice}</p>}

        {loading ? <div className="booking-empty">Loading menus...</div> : (
          <>
            <PortalPreview menus={menus} editor={editor} onSelect={setEditor} />
            <div className="customer-portal-version-bar">
              <strong>Saved versions</strong>
              {templates.length === 0 ? <span>No earlier versions yet.</span> : (
                <select defaultValue="" onChange={event => { if (event.target.value) void previewVersion(Number(event.target.value)); }}>
                  <option value="" disabled>Choose a version to preview</option>
                  {templates.map(template => <option key={template.id} value={template.id}>{new Date(`${template.createdAt}Z`).toLocaleString()}</option>)}
                </select>
              )}
            </div>
            <h2 className="customer-menu-list-heading">PDF files and menu controls</h2>
            <div className="customer-menu-manager-list">
            {menus.map(menu => (
              <article className="customer-menu-manager-card" key={menu.id}>
                <div className="customer-menu-pdf-icon"><PortalIcon value={menu.icon} /></div>
                <div className="customer-menu-manager-copy">
                  <div className="customer-menu-manager-title">
                    <h2>{menu.title}</h2>
                    {menu.primary && menu.slug !== "allergens" && <span>Primary menu</span>}
                  </div>
                  <p>{menu.description || "No description"}</p>
                  <div className="customer-menu-card-preview" style={{ backgroundColor: menu.cardColor, color: menu.textColor }}>
                    <span><PortalIcon value={menu.icon} /></span>
                    <span><strong>{menu.title}</strong><small>{menu.description}</small></span>
                    <span>→</span>
                  </div>
                  {menu.slug !== "allergens" && <a href={menu.pdfUrl} target="_blank" rel="noreferrer">Open current PDF</a>}
                </div>
                <div className="booking-row-actions">
                  <button onClick={() => setEditor(menu)}>{menu.slug === "allergens" ? "Edit button" : "Edit or replace PDF"}</button>
                  {!menu.primary && menu.slug !== "allergens" && <button className="danger-button customer-portal-remove-button" onClick={() => remove(menu)}>Remove</button>}
                </div>
              </article>
            ))}
            </div>
          </>
        )}
      </section>

      {editor && (
        <aside className="customer-portal-editor-drawer">
          <div className="customer-menu-editor" key={editor.id}>
            <div className="booking-modal-heading">
              <h2>{editor.id ? `Edit ${editor.title}` : "Add customer menu"}</h2>
              <button type="button" onClick={() => setEditor(null)}>×</button>
            </div>
            <div className="booking-form-grid">
              <label>Button title<input name="title" value={editor.title} onChange={event => updatePreview("title", event.target.value)} required placeholder="View Our Cocktail Menu" /></label>
              <label>Sidebar name<input name="sidebarName" value={editor.sidebarName} onChange={event => updatePreview("sidebarName", event.target.value)} required placeholder="Cocktail Menu" /></label>
              <label className="booking-form-wide">Button description<input name="description" value={editor.description || ""} onChange={event => updatePreview("description", event.target.value)} placeholder="Discover our current cocktails." /></label>
              <fieldset className="booking-form-wide customer-icon-picker"><legend>Icon</legend>
                {iconGroups.map(group => <div key={group.name}><strong>{group.name}</strong><div>{group.icons.map(icon => <button className={editor.icon === `material:${icon}` ? "active" : ""} key={icon} type="button" title={icon.replaceAll("_", " ")} onClick={() => updatePreview("icon", `material:${icon}`)}><PortalIcon value={`material:${icon}`} /></button>)}</div></div>)}
                <label>Use your own emoji<input maxLength={16} value={editor.icon.startsWith("emoji:") ? editor.icon.slice(6) : ""} onChange={event => updatePreview("icon", `emoji:${event.target.value}`)} placeholder="Type or paste an emoji" /></label>
              </fieldset>
              <label>Button colour<span className="customer-menu-colour-control"><input name="cardColor" type="color" value={editor.cardColor} style={{ backgroundColor: editor.cardColor }} onChange={event => updatePreview("cardColor", event.target.value)} /><code>{editor.cardColor}</code></span></label>
              <label>Text colour<span className="customer-menu-colour-control"><input name="textColor" type="color" value={editor.textColor} style={{ backgroundColor: editor.textColor }} onChange={event => updatePreview("textColor", event.target.value)} /><code>{editor.textColor}</code></span></label>
              {editor.slug !== "allergens" && <label className="booking-form-wide">{editor.id ? "Replacement PDF (leave empty to keep the current PDF)" : "Menu PDF"}
                <input name="pdf" type="file" accept="application/pdf,.pdf" onChange={event => {
                  const file = event.target.files?.[0];
                  if (file) { setFiles(current => ({ ...current, [editor.id]: file })); setDirty(true); }
                }} required={editor.id < 0 && !files[editor.id]} />
                <small>PDF only, maximum 20 MB.</small>
              </label>}
            </div>
          </div>
        </aside>
      )}
      {templatePreview && (
        <div className="booking-modal-backdrop customer-template-preview" role="dialog" aria-modal="true" aria-label="Saved customer portal preview">
          <div className="booking-modal customer-template-preview-modal">
            <div className="booking-modal-heading"><div><h2>Saved customer portal version</h2><p>Check this version before restoring it.</p></div><button type="button" onClick={() => setTemplatePreview(null)}>×</button></div>
            <PortalPreview menus={templatePreview.menus} editor={null} onSelect={() => {}} />
            <div className="booking-modal-actions"><button type="button" onClick={() => setTemplatePreview(null)}>Cancel</button><button className="primary-button" type="button" onClick={() => { const id = templatePreview.id; setTemplatePreview(null); void restore(id); }}>Restore this version</button></div>
          </div>
        </div>
      )}
    </main>
  );
}
