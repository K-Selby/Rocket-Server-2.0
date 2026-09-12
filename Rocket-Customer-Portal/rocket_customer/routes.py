from pathlib import Path

from flask import current_app, render_template, request, send_from_directory

from app import db
from app.models import AllergenMenuItem
from rocket_customer import customer

@customer.app_context_processor
def portal_links():
    return {"staff_portal_url": current_app.config["STAFF_PORTAL_URL"]}


@customer.route("/")
@customer.route("/customer")
@customer.route("/customer/")
def customer_home():
    menu_path = Path(current_app.config["ROCKET_SHARED_ASSETS_PATH"]) / "menus" / "the-rocket-pub-food-menu.pdf"
    return render_template("customer_home.html", food_menu_available=menu_path.exists())


@customer.route("/customer/food-menu")
def customer_food_menu():
    menu_directory = Path(current_app.config["ROCKET_SHARED_ASSETS_PATH"]) / "menus"
    filename = "the-rocket-pub-food-menu.pdf"

    if not (menu_directory / filename).exists():
        return render_template("customer_menu_unavailable.html"), 404

    return send_from_directory(
        menu_directory,
        filename,
        mimetype="application/pdf",
        as_attachment=False,
    )


@customer.route("/customer/allergens")
def customer_allergens():
    return render_template("customer_allergens.html", **allergen_menu_context())


def allergen_menu_context():
    search = request.args.get("q", "").strip()
    category = request.args.get("category", "").strip()
    filters = {
        "milk_free": request.args.get("milk_free") == "1",
        "nut_free": request.args.get("nut_free") == "1",
        "egg_free": request.args.get("egg_free") == "1",
        "gluten_free": request.args.get("gluten_free") == "1",
        "vegetarian": request.args.get("vegetarian") == "1",
        "can_make_vegetarian": request.args.get("can_make_vegetarian") == "1",
    }

    statement = db.select(AllergenMenuItem).where(AllergenMenuItem.active.is_(True))

    if search:
        term = f"%{search.lower()}%"
        statement = statement.where(
            db.or_(
                db.func.lower(AllergenMenuItem.name).like(term),
                db.func.lower(AllergenMenuItem.description).like(term),
                db.func.lower(AllergenMenuItem.ingredients).like(term),
            )
        )

    if category:
        statement = statement.where(AllergenMenuItem.category == category)
    if filters["milk_free"]:
        statement = statement.where(AllergenMenuItem.milk_status == "free")
    if filters["nut_free"]:
        statement = statement.where(AllergenMenuItem.nuts_status == "free")
    if filters["egg_free"]:
        statement = statement.where(AllergenMenuItem.egg_status == "free")
    if filters["gluten_free"]:
        statement = statement.where(
            db.or_(
                AllergenMenuItem.gluten_status == "free",
                AllergenMenuItem.can_make_gluten_free.is_(True),
            )
        )
    if filters["vegetarian"]:
        statement = statement.where(AllergenMenuItem.vegetarian.is_(True))
    if filters["can_make_vegetarian"]:
        statement = statement.where(
            db.or_(
                AllergenMenuItem.vegetarian.is_(True),
                AllergenMenuItem.can_make_vegetarian.is_(True),
            )
        )

    items = db.session.scalars(
        statement.order_by(AllergenMenuItem.category, AllergenMenuItem.name)
    ).all()

    selected_allergens = []
    if filters["milk_free"]:
        selected_allergens.append("milk_status")
    if filters["nut_free"]:
        selected_allergens.append("nuts_status")
    if filters["egg_free"]:
        selected_allergens.append("egg_status")
    if filters["gluten_free"]:
        selected_allergens.append("gluten_status")

    side_options = {}
    safe_side_options = {}

    for item in items:
        if item.category != "Main Meals":
            continue

        sides = [
            db.session.get(AllergenMenuItem, link.side_id)
            for link in item.allowed_side_links
        ]
        sides = sorted(
            (side for side in sides if side and side.active),
            key=lambda side: side.name.lower(),
        )
        side_options[item.id] = sides

        def side_matches(side, field):
            if field == "gluten_status":
                return side.gluten_status == "free" or side.can_make_gluten_free
            return getattr(side, field) == "free"

        safe_side_options[item.id] = [
            side
            for side in sides
            if all(side_matches(side, field) for field in selected_allergens)
        ]

    return {
        "items": items,
        "categories": ["Main Meals", "Starters", "Sides", "Kids Meals", "Desserts"],
        "side_options": side_options,
        "safe_side_options": safe_side_options,
        "selected_allergens": selected_allergens,
        "search": search,
        "selected_category": category,
        "filters": filters,
    }
