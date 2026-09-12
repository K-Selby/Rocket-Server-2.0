from flask import Blueprint


customer = Blueprint(
    "customer",
    __name__,
    template_folder="templates",
    static_folder="static",
    static_url_path="/customer-static",
)


from rocket_customer import routes  # noqa: E402,F401
