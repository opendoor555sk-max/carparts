-- =====================================================================
--  MERA HISAB - server setup (Supabase). Paste this WHOLE file in
--  Supabase > SQL Editor > New query > Run.   Safe to run again.
-- =====================================================================
create schema if not exists mh;

create table if not exists mh.biz (
  id uuid primary key default gen_random_uuid(),
  name text not null default '',
  created timestamptz not null default now()
);
create table if not exists mh.users (
  id uuid primary key default gen_random_uuid(),
  biz_id uuid not null references mh.biz(id) on delete cascade,
  name text not null default '',
  phone text not null unique,
  role text not null default 'owner' check (role in ('owner','staff')),
  is_admin boolean not null default false,
  status text not null default 'active' check (status in ('active','blocked')),
  otp_hash text, otp_exp timestamptz, otp_fails int not null default 0,
  otp_req timestamptz,
  created timestamptz not null default now()
);
create table if not exists mh.sessions (
  token_hash text primary key,
  user_id uuid not null references mh.users(id) on delete cascade,
  device text not null default '',
  created timestamptz not null default now(),
  last_seen timestamptz not null default now()
);
create table if not exists mh.requests (
  id uuid primary key default gen_random_uuid(),
  name text not null, phone text not null, shop text not null default '',
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  created timestamptz not null default now()
);
create sequence if not exists mh.rec_seq;
create table if not exists mh.records (
  biz_id uuid not null references mh.biz(id) on delete cascade,
  kind text not null, id text not null,
  data jsonb not null default '{}'::jsonb,
  upd bigint not null default 0,
  del boolean not null default false,
  seq bigint not null,
  primary key (biz_id, kind, id)
);
create index if not exists records_seq_idx on mh.records (biz_id, seq);

alter table mh.biz enable row level security;
alter table mh.users enable row level security;
alter table mh.sessions enable row level security;
alter table mh.requests enable row level security;
alter table mh.records enable row level security;

-- ---------------- helpers (private schema, not reachable from the app) ----------------
create or replace function mh.norm(p text) returns text language sql immutable as
$$ select right(regexp_replace(coalesce(p,''), '\D', '', 'g'), 10) $$;

create or replace function mh.hash(p text) returns text language sql immutable as
$$ select encode(sha256(convert_to('mh|' || coalesce(p,''), 'UTF8')), 'hex') $$;

-- makes a fresh 6-digit OTP for a user, returns it in plain (shown only to whoever generated it)
create or replace function mh.new_otp(p_uid uuid) returns text language plpgsql as $$
declare v text;
begin
  v := lpad((floor(random() * 900000) + 100000)::int::text, 6, '0');
  update mh.users set otp_hash = mh.hash(v || p_uid::text), otp_exp = now() + interval '48 hours',
         otp_fails = 0, otp_req = null where id = p_uid;
  return v;
end $$;

-- the logged-in, allowed user for a session token (raises 'auth' otherwise)
create or replace function mh.me(p_tok text) returns mh.users language plpgsql as $$
declare u mh.users;
begin
  select us.* into u from mh.sessions s join mh.users us on us.id = s.user_id
   where s.token_hash = mh.hash(coalesce(p_tok,'')) and us.status = 'active'
     and not exists (select 1 from mh.users o where o.biz_id = us.biz_id and o.role = 'owner' and o.status = 'blocked');
  if u.id is null then raise exception 'auth'; end if;
  update mh.sessions set last_seen = now() where token_hash = mh.hash(p_tok) and last_seen < now() - interval '5 minutes';
  return u;
end $$;

-- ---- ADMIN (you) : run in SQL Editor:  select mh.make_admin('9XXXXXXXXX','Your Name');  -> gives your first OTP
create or replace function mh.make_admin(p_phone text, p_name text) returns text language plpgsql as $$
declare ph text := mh.norm(p_phone); u mh.users; b uuid;
begin
  if length(ph) <> 10 then raise exception 'phone must be 10 digits'; end if;
  select * into u from mh.users where phone = ph;
  if u.id is null then
    insert into mh.biz(name) values (coalesce(nullif(p_name,''),'Admin') || ' ni dukan') returning id into b;
    insert into mh.users(biz_id, name, phone, role, is_admin) values (b, coalesce(nullif(p_name,''),'Admin'), ph, 'owner', true) returning * into u;
  else
    update mh.users set is_admin = true, role = 'owner', status = 'active' where id = u.id;
  end if;
  return 'ADMIN OTP: ' || mh.new_otp(u.id);
end $$;

-- new OTP for anyone by phone (SQL Editor only):  select mh.admin_otp('9XXXXXXXXX');
create or replace function mh.admin_otp(p_phone text) returns text language plpgsql as $$
declare u mh.users;
begin
  select * into u from mh.users where phone = mh.norm(p_phone);
  if u.id is null then raise exception 'no such user'; end if;
  update mh.users set status = 'active' where id = u.id;
  return 'OTP: ' || mh.new_otp(u.id);
end $$;

-- ---------------- app functions (called through the API) ----------------
create or replace function public.mh_info() returns jsonb language sql security definer set search_path = '' as
$$ select jsonb_build_object('ok', true, 'admin_phone', coalesce((select phone from mh.users where is_admin order by created limit 1), '')) $$;

-- name + number -> asks for an OTP.  New number => request to admin.  Known number => marks "OTP wanted".
create or replace function public.mh_request(p_name text, p_phone text, p_shop text default '') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare ph text := mh.norm(p_phone); u mh.users; r mh.requests; adm text;
begin
  select phone into adm from mh.users where is_admin order by created limit 1;
  if length(ph) <> 10 then return jsonb_build_object('ok', false, 'err', 'phone'); end if;
  if length(trim(coalesce(p_name,''))) < 2 then return jsonb_build_object('ok', false, 'err', 'name'); end if;
  select * into u from mh.users where phone = ph;
  if u.id is not null then
    if u.status = 'blocked' then return jsonb_build_object('ok', false, 'err', 'blocked'); end if;
    update mh.users set otp_req = now() where id = u.id;
    return jsonb_build_object('ok', true, 'status', 'known', 'role', u.role, 'admin_phone', coalesce(adm,''),
      'owner_phone', coalesce((select o.phone from mh.users o where o.biz_id = u.biz_id and o.role = 'owner' order by o.created limit 1), ''));
  end if;
  select * into r from mh.requests where phone = ph and status = 'pending';
  if r.id is null then
    insert into mh.requests(name, phone, shop) values (trim(p_name), ph, trim(coalesce(p_shop,'')));
  else
    update mh.requests set name = trim(p_name), shop = trim(coalesce(p_shop,'')) where id = r.id;
  end if;
  return jsonb_build_object('ok', true, 'status', 'pending', 'admin_phone', coalesce(adm,''));
end $$;

create or replace function public.mh_login(p_phone text, p_otp text, p_device text default '') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare u mh.users; tok text; bn text;
begin
  select * into u from mh.users where phone = mh.norm(p_phone);
  if u.id is null or u.status = 'blocked' then return jsonb_build_object('ok', false, 'err', 'noaccess'); end if;
  if u.otp_hash is null or u.otp_exp < now() then return jsonb_build_object('ok', false, 'err', 'nootp'); end if;
  if u.otp_fails >= 5 then return jsonb_build_object('ok', false, 'err', 'locked'); end if;
  if u.otp_hash <> mh.hash(trim(coalesce(p_otp,'')) || u.id::text) then
    update mh.users set otp_fails = otp_fails + 1 where id = u.id;
    return jsonb_build_object('ok', false, 'err', 'wrong');
  end if;
  update mh.users set otp_hash = null, otp_exp = null, otp_fails = 0, otp_req = null where id = u.id;
  tok := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
  insert into mh.sessions(token_hash, user_id, device) values (mh.hash(tok), u.id, left(coalesce(p_device,''), 60));
  select name into bn from mh.biz where id = u.biz_id;
  return jsonb_build_object('ok', true, 'token', tok, 'name', u.name, 'phone', u.phone, 'role', u.role,
                            'is_admin', u.is_admin, 'biz', u.biz_id, 'biz_name', bn);
end $$;

create or replace function public.mh_logout(p_tok text) returns jsonb language plpgsql security definer set search_path = '' as $$
begin delete from mh.sessions where token_hash = mh.hash(coalesce(p_tok,'')); return jsonb_build_object('ok', true); end $$;

create or replace function public.mh_whoami(p_tok text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users; bn text;
begin
  u := mh.me(p_tok);
  select name into bn from mh.biz where id = u.biz_id;
  return jsonb_build_object('ok', true, 'name', u.name, 'phone', u.phone, 'role', u.role, 'is_admin', u.is_admin, 'biz', u.biz_id, 'biz_name', bn);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

-- sync: push changed records, then pull everything newer than p_since
create or replace function public.mh_sync(p_tok text, p_since bigint, p_push jsonb default '[]'::jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare u mh.users; x jsonb; k text; i text; isdel boolean; n int := 0; rows jsonb; mx bigint; more boolean;
begin
  begin u := mh.me(p_tok);
  exception when others then
    if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if; raise;
  end;
  perform pg_advisory_xact_lock(hashtextextended(u.biz_id::text, 7));
  for x in select * from jsonb_array_elements(coalesce(p_push, '[]'::jsonb)) loop
    k := x->>'kind'; i := x->>'id'; isdel := coalesce((x->>'del')::boolean, false);
    if k is null or i is null or k not in ('party','txn','product','review','learn','settings') then continue; end if;
    if u.role = 'staff' and (k = 'settings' or isdel) then continue; end if;
    insert into mh.records(biz_id, kind, id, data, upd, del, seq)
    values (u.biz_id, k, i, coalesce(x->'data', '{}'::jsonb), coalesce((x->>'upd')::bigint, 0), isdel, nextval('mh.rec_seq'))
    on conflict (biz_id, kind, id) do update
      set data = excluded.data, upd = excluded.upd, del = excluded.del, seq = excluded.seq
      where excluded.upd >= mh.records.upd and (u.role = 'owner' or not mh.records.del);
    n := n + 1;
  end loop;
  select coalesce(jsonb_agg(jsonb_build_object('kind', kind, 'id', id, 'data', data, 'upd', upd, 'del', del, 'seq', seq) order by seq), '[]'::jsonb),
         coalesce(max(seq), p_since), count(*) >= 1000
    into rows, mx, more
    from (select * from mh.records where biz_id = u.biz_id and seq > coalesce(p_since, 0) order by seq limit 1000) q;
  return jsonb_build_object('ok', true, 'rows', rows, 'max', mx, 'more', more, 'pushed', n);
end $$;

-- ---- owner: staff management
create or replace function public.mh_staff_list(p_tok text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users;
begin
  u := mh.me(p_tok);
  if u.role <> 'owner' then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  return jsonb_build_object('ok', true, 'rows', coalesce((select jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name, 'phone', s.phone,
      'status', s.status, 'otp_req', s.otp_req is not null,
      'seen', (select max(last_seen) from mh.sessions z where z.user_id = s.id)) order by s.created)
      from mh.users s where s.biz_id = u.biz_id and s.role = 'staff'), '[]'::jsonb));
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if; raise;
end $$;

create or replace function public.mh_staff_add(p_tok text, p_name text, p_phone text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users; ph text := mh.norm(p_phone); s mh.users;
begin
  u := mh.me(p_tok);
  if u.role <> 'owner' then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  if length(ph) <> 10 then return jsonb_build_object('ok', false, 'err', 'phone'); end if;
  if length(trim(coalesce(p_name,''))) < 2 then return jsonb_build_object('ok', false, 'err', 'name'); end if;
  if exists (select 1 from mh.users where phone = ph) then return jsonb_build_object('ok', false, 'err', 'exists'); end if;
  insert into mh.users(biz_id, name, phone, role) values (u.biz_id, trim(p_name), ph, 'staff') returning * into s;
  return jsonb_build_object('ok', true, 'id', s.id, 'otp', mh.new_otp(s.id), 'phone', ph);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

create or replace function public.mh_staff_otp(p_tok text, p_id uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users; s mh.users;
begin
  u := mh.me(p_tok);
  if u.role <> 'owner' then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  select * into s from mh.users where id = p_id and biz_id = u.biz_id and role = 'staff';
  if s.id is null then return jsonb_build_object('ok', false, 'err', 'nouser'); end if;
  return jsonb_build_object('ok', true, 'otp', mh.new_otp(s.id), 'phone', s.phone, 'name', s.name);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

-- p_action: 'block' | 'unblock' | 'remove'
create or replace function public.mh_staff_set(p_tok text, p_id uuid, p_action text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users; s mh.users;
begin
  u := mh.me(p_tok);
  if u.role <> 'owner' then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  select * into s from mh.users where id = p_id and biz_id = u.biz_id and role = 'staff';
  if s.id is null then return jsonb_build_object('ok', false, 'err', 'nouser'); end if;
  if p_action = 'remove' then delete from mh.users where id = s.id;
  elsif p_action = 'block' then update mh.users set status = 'blocked' where id = s.id; delete from mh.sessions where user_id = s.id;
  elsif p_action = 'unblock' then update mh.users set status = 'active' where id = s.id;
  end if;
  return jsonb_build_object('ok', true);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

-- ---- app admin (you): requests, owners, OTP, block
create or replace function public.mh_admin_requests(p_tok text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users;
begin
  u := mh.me(p_tok);
  if not u.is_admin then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  return jsonb_build_object('ok', true, 'rows', coalesce((select jsonb_agg(jsonb_build_object('id', r.id, 'name', r.name, 'phone', r.phone, 'shop', r.shop, 'at', r.created) order by r.created)
      from mh.requests r where r.status = 'pending'), '[]'::jsonb));
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

create or replace function public.mh_admin_approve(p_tok text, p_id uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users; r mh.requests; b uuid; n mh.users;
begin
  u := mh.me(p_tok);
  if not u.is_admin then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  select * into r from mh.requests where id = p_id and status = 'pending';
  if r.id is null then return jsonb_build_object('ok', false, 'err', 'norequest'); end if;
  if exists (select 1 from mh.users where phone = r.phone) then
    update mh.requests set status = 'approved' where id = r.id;
    select * into n from mh.users where phone = r.phone;
  else
    insert into mh.biz(name) values (coalesce(nullif(r.shop,''), r.name || ' ni dukan')) returning id into b;
    insert into mh.users(biz_id, name, phone, role) values (b, r.name, r.phone, 'owner') returning * into n;
    update mh.requests set status = 'approved' where id = r.id;
  end if;
  return jsonb_build_object('ok', true, 'otp', mh.new_otp(n.id), 'phone', n.phone, 'name', n.name);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

create or replace function public.mh_admin_reject(p_tok text, p_id uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users;
begin
  u := mh.me(p_tok);
  if not u.is_admin then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  update mh.requests set status = 'rejected' where id = p_id;
  return jsonb_build_object('ok', true);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

create or replace function public.mh_admin_owners(p_tok text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users;
begin
  u := mh.me(p_tok);
  if not u.is_admin then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  return jsonb_build_object('ok', true, 'rows', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name, 'phone', o.phone, 'biz', b.name,
      'status', o.status, 'otp_req', o.otp_req is not null, 'is_admin', o.is_admin,
      'staff', (select count(*) from mh.users s where s.biz_id = o.biz_id and s.role = 'staff'),
      'records', (select count(*) from mh.records r where r.biz_id = o.biz_id and not r.del)) order by o.created)
      from mh.users o join mh.biz b on b.id = o.biz_id where o.role = 'owner'), '[]'::jsonb));
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

create or replace function public.mh_admin_otp(p_tok text, p_id uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users; t mh.users;
begin
  u := mh.me(p_tok);
  if not u.is_admin then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  select * into t from mh.users where id = p_id;
  if t.id is null then return jsonb_build_object('ok', false, 'err', 'nouser'); end if;
  return jsonb_build_object('ok', true, 'otp', mh.new_otp(t.id), 'phone', t.phone, 'name', t.name);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

create or replace function public.mh_admin_block(p_tok text, p_id uuid, p_block boolean) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u mh.users;
begin
  u := mh.me(p_tok);
  if not u.is_admin then return jsonb_build_object('ok', false, 'err', 'forbidden'); end if;
  if p_id = u.id then return jsonb_build_object('ok', false, 'err', 'self'); end if;
  update mh.users set status = case when p_block then 'blocked' else 'active' end where id = p_id;
  if p_block then delete from mh.sessions where user_id = p_id; end if;
  return jsonb_build_object('ok', true);
exception when others then
  if sqlerrm = 'auth' then return jsonb_build_object('ok', false, 'err', 'auth'); end if;
  raise;
end $$;

-- let the app (anon key) call only the mh_ functions
do $$
declare f record;
begin
  if exists (select 1 from pg_roles where rolname = 'anon') then
    for f in select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.proname like 'mh\_%' loop
      execute format('grant execute on function %s to anon, authenticated', f.sig);
    end loop;
  end if;
end $$;
notify pgrst, 'reload schema';
