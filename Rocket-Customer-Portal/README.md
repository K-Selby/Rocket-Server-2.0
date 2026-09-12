# Rocket Customer Portal

Public menu and allergen pages for The Rocket Pub.

This folder contains a standalone Flask blueprint. The Booking Portal loads it
as a neighbouring local package, so the customer and booking source remain
separate while both are served by one Flask process on port 8000.

The Customer Portal owns `/`, `/customer`, the public food menu and the public
allergen search. It does not contain staff authentication or booking controls.
