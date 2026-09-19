"""One-off, read-only script: print every user's name/phone/email/role/store.
Uses the exact same MONGO_URL/DB_NAME config server.py reads from .env.
Does not write, update, or delete anything.
"""
from pathlib import Path
from dotenv import load_dotenv
import os

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / ".env")

from pymongo import MongoClient  # noqa: E402 (import after load_dotenv, matching server.py's own order)

mongo_url = os.environ["MONGO_URL"]
db_name = os.environ["DB_NAME"]

client = MongoClient(mongo_url, serverSelectionTimeoutMS=8000)
db = client[db_name]

# Build a store_id -> store_name lookup once, same shape as server.py's own
# store resolution, so each user row can show a readable store name instead
# of just the raw id.
stores_by_id = {s.get("id"): s.get("name") for s in db.stores.find({}, {"_id": 0, "id": 1, "name": 1})}

users = list(db.users.find({}, {"_id": 0}))
print(f"Total users: {len(users)}\n")

# NOTE: checked the actual schema in server.py before writing this — user
# documents have no dedicated "phone" or "email" field. The closest thing is
# "contact" (only set on the admin/store-owner account created at signup;
# staff created via /admin/users have no contact field at all). Printing it
# under "phone" below since that's what it actually holds; "email" is kept
# in case it's ever populated by hand, but the schema never sets it.
for u in users:
    store_id = u.get("store_id")
    store_name = stores_by_id.get(store_id, "(none)" if not store_id else "(unknown store)")
    print(
        f"name={u.get('name')!r}  phone(contact)={u.get('contact')!r}  email={u.get('email')!r}  "
        f"role={u.get('role')!r}  store_id={store_id!r}  store_name={store_name!r}"
    )

client.close()
