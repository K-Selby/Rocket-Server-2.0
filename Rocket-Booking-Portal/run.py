import os

from app import create_app


app = create_app()
port = int(os.environ.get("ROCKET_FLASK_PORT", "8001"))


if __name__ == "__main__":
    debug_mode = os.environ.get("ROCKET_DEBUG", "0") == "1"

    if debug_mode:
        app.run(
            host="0.0.0.0",
            port=port,
            debug=True,
        )
    else:
        from waitress import serve

        serve(
            app,
            host="0.0.0.0",
            port=port,
            threads=8,
            channel_timeout=120,
        )
