"""
LPC -> Twigi asset preprocessor v4: everything + body types + ALL animations.

Extracts, for every renderable LPC item, the front-facing (down) static frame AND the
down-row strip of every animation, for male+female variants. Covers standard wearables,
head add-ons (brows/nose/ears), dresses, and held/back gear (weapon/tools/shield/
backpack/quiver, which use the <item>/<anim>/<variant>.png layout).

Run:  python tools/lpc_pack.py
"""
import os, re, json, shutil, glob
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LPC = os.environ.get("LPC_SRC") or os.path.join(
    os.environ.get("LOCALAPPDATA", ""), "Temp", "claude",
    "C--Users-ATPL-ADMIN-Downloads-gigi",
    "16f2abea-7802-4d84-bbb4-1bf84793f7dc", "scratchpad", "lpc")
SHEETS = os.path.join(LPC, "spritesheets")
OUT = os.path.join(ROOT, "gigi-server", "twigi_assets", "lpc")
FRAMES = os.path.join(OUT, "frames")
DOWN_Y = 128

ANIMS = ["idle", "walk", "run", "jump", "sit", "spellcast", "slash", "thrust",
         "shoot", "hurt", "climb", "emote"]
ANIM_FPS = {"idle": 3, "walk": 8, "run": 10, "jump": 7, "sit": 3, "spellcast": 9,
            "slash": 10, "thrust": 10, "shoot": 10, "hurt": 6, "climb": 6, "emote": 3}

CATS = {
    "hair":      {"out": "hair",   "z": 120, "recolor": "hair", "back": True},
    "beards":    {"out": "beards", "z": 118, "recolor": "hair"},
    "facial":    {"out": "facial", "z": 110, "recolor": None},
    "eyes":      {"out": "eyes",   "z": 101, "recolor": None, "expr": "neutral"},
    "torso":     {"out": "top",    "z": 35,  "recolor": None},
    "dress":     {"out": "dress",  "z": 38,  "recolor": None},
    "legs":      {"out": "bottom", "z": 20,  "recolor": None},
    "feet":      {"out": "shoes",  "z": 18,  "recolor": None},
    "hat":       {"out": "hat",    "z": 140, "recolor": None},
    "cape":      {"out": "cape",   "z": 5,   "recolor": None},
    "neck":      {"out": "neck",   "z": 45,  "recolor": None},
    "arms":      {"out": "arms",   "z": 25,  "recolor": None},
    "shoulders": {"out": "shoulders", "z": 40, "recolor": None},
}
HEAD_SUBS = {  # head/<sub>/** add-ons composited onto the auto-included head
    "eyebrows": {"out": "brows", "z": 103, "recolor": "hair"},
    "nose":     {"out": "nose",  "z": 102, "recolor": "skin"},
    "ears":     {"out": "ears",  "z": 101, "recolor": "skin"},
}
# held / back gear using the <class>/<item>/[bg]/<anim>/<variant>.png layout
WSTYLE = {"weapon": ("weapon", 150, 6), "tools": ("tool", 150, 6),
          "shield": ("shield", 155, 7), "backpack": ("backpack", 6, 6),
          "quiver": ("quiver", 6, 6)}

BT_TOKENS = {"male", "female", "muscular", "pregnant", "teen", "child", "thin",
             "adult", "universal"}
PREF = {"m": ["male", "adult", "universal", "teen", "muscular"],
        "f": ["female", "thin", "adult", "universal", "teen", "pregnant"]}
EXPR_TOKENS = {"neutral", "happy", "sad", "angry", "anger", "shock", "shame",
               "eyeroll", "closing", "look_r", "look_l", "default"}
BG_TOKENS = {"bg", "behind", "universal_behind", "background"}

def slug(s): return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")
def sheet_ok(im): return im.size[1] >= DOWN_Y + 64 and im.size[0] >= 64

def static_frame(folder):
    for a in ("idle.png", "walk.png"):
        p = os.path.join(folder, a)
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            if sheet_ok(im):
                return im.crop((0, DOWN_Y, 64, DOWN_Y + 64))
    return None

def anim_strip(path):
    if not path or not os.path.exists(path):
        return None
    im = Image.open(path).convert("RGBA")
    if not sheet_ok(im):
        return None
    n = im.size[0] // 64
    return im.crop((0, DOWN_Y, n * 64, DOWN_Y + 64)) if n >= 2 else None

def load_palette(pal):
    p = os.path.join(LPC, "palette_definitions", pal, f"{pal}_ulpc.json")
    return {k: [c.lower() for c in v] for k, v in json.load(open(p)).items()}

def swatch(ramp, kind):
    i = len(ramp) - 2 if kind == "body" else len(ramp) // 2
    return ramp[max(0, min(i, len(ramp) - 1))]

SKIN_COLORS = ["light", "amber", "olive", "taupe", "bronze", "brown", "black"]
HAIR_COLORS = ["black", "dark_brown", "chestnut", "light_brown", "ginger",
               "blonde", "gold", "red", "gray", "white", "pink", "purple"]

saved = 0
def export_variant(folder, base_name, anims_from_files=None):
    global saved
    if anims_from_files is None:
        st = static_frame(folder)
        anim_paths = {a: os.path.join(folder, f"{a}.png") for a in ANIMS}
    else:
        st = None
        for a in ("idle", "walk"):
            s = anim_strip(anims_from_files.get(a)) if anims_from_files.get(a) else None
            if s is not None:
                st = s.crop((0, 0, 64, 64)); break
        anim_paths = anims_from_files
    if st is None:
        return None
    st.save(os.path.join(FRAMES, base_name + ".png")); saved += 1
    entry = {"s": base_name + ".png", "a": {}}
    for a in ANIMS:
        strip = anim_strip(anim_paths.get(a))
        if strip is not None:
            fn = f"{base_name}_{a}.png"
            strip.save(os.path.join(FRAMES, fn)); saved += 1
            entry["a"][a] = [fn, strip.size[0] // 64]
    return entry

def cat_cfg_for(segs):
    """Resolve category config for a folder path, incl. head/<sub> add-ons."""
    top = segs[0]
    if top == "head":
        return HEAD_SUBS.get(segs[1]) if len(segs) > 1 else None
    return CATS.get(top)

def build():
    global saved
    if not os.path.isdir(SHEETS):
        raise SystemExit("LPC spritesheets not found at: " + SHEETS)
    if os.path.isdir(FRAMES):
        shutil.rmtree(FRAMES)
    os.makedirs(FRAMES, exist_ok=True)
    saved = 0

    body_pal, hair_pal = load_palette("body"), load_palette("hair")
    manifest = {
        "frame_size": 64, "anims": ANIMS, "fps": ANIM_FPS,
        "palettes": {"body": body_pal, "hair": hair_pal},
        "recolor_base": {"skin": "light", "hair": "orange"},
        "skinColors": [{"name": n, "hex": swatch(body_pal[n], "body")} for n in SKIN_COLORS if n in body_pal],
        "hairColors": [{"name": n, "hex": swatch(hair_pal[n], "hair")} for n in HAIR_COLORS if n in hair_pal],
        "bodies": {}, "heads": {}, "categories": {},
    }

    # mandatory body + head (the head is PART of the character, never optional)
    for bt, rb, rh in (("m", "body/bodies/male", "head/heads/human/male"),
                       ("f", "body/bodies/female", "head/heads/human/female")):
        vb = export_variant(os.path.join(SHEETS, rb), f"body_{bt}")
        vh = export_variant(os.path.join(SHEETS, rh), f"head_{bt}")
        if vb: manifest["bodies"][bt] = vb
        if vh: manifest["heads"][bt] = vh
    assert "m" in manifest["heads"] and "f" in manifest["heads"], "head export failed!"

    # standard wearables (folder-per-bodytype layout)
    folders = set()
    for a in ANIMS:
        folders.update(os.path.dirname(p) for p in
                       glob.glob(os.path.join(SHEETS, "**", f"{a}.png"), recursive=True))
    groups = {}
    for folder in folders:
        segs = os.path.relpath(folder, SHEETS).replace("\\", "/").split("/")
        if segs[0] in WSTYLE or segs[0] in ("body",):
            continue
        cfg = cat_cfg_for(segs)
        if not cfg:
            continue
        segset = set(segs)
        if cfg.get("expr") and EXPR_TOKENS & segset and cfg["expr"] not in segset:
            continue
        side = "bg" if BG_TOKENS & segset else "fg"
        drop = BT_TOKENS | BG_TOKENS | EXPR_TOKENS | {"head"}
        item_id = slug("_".join(s for s in segs[1:] if s not in drop)) or slug(segs[-1])
        for bt in ("m", "f"):
            rank = 999
            for i, tok in enumerate(PREF[bt]):
                if tok in segset:
                    rank = i; break
            if rank == 999 and not (BT_TOKENS & segset):
                rank = 50
            if rank == 999:
                continue
            g = groups.setdefault(cfg["out"], {}).setdefault(item_id, {}).setdefault(side, {})
            if bt not in g or rank < g[bt][0]:
                g[bt] = (rank, folder)

    cfg_by_out = {c["out"]: c for c in list(CATS.values()) + list(HEAD_SUBS.values())}
    for out, items in groups.items():
        cfg = cfg_by_out[out]
        cat = manifest["categories"].setdefault(
            out, {"z": cfg["z"], "zback": 8, "recolor": cfg["recolor"], "items": {}})
        for item_id, sides in sorted(items.items()):
            entry = {}
            for side, bts in sides.items():
                for bt, (rank, folder) in bts.items():
                    v = export_variant(folder, f"{out}_{item_id}_{side}_{bt}")
                    if v:
                        entry[f"{side}_{bt}"] = v
            if entry:
                cat["items"][item_id] = entry
        if not cat["items"]:
            del manifest["categories"][out]

    # held/back gear: <class>/<item>/[bg|bodytype]/(<anim>/<variant>.png | <anim>.png)
    NOISE = BG_TOKENS | BT_TOKENS | {"universal", "foreground"}
    for top, (out, zfg, zbg) in WSTYLE.items():
        witems = {}   # witems[item_id][side][bt][anim] = path
        def add(a, p, key_parts, variant, all_toks):
            side = "bg" if (BG_TOKENS & all_toks) else "fg"
            if variant in ("bg", "behind", "background"):
                side, variant = "bg", ""
            elif variant in ("fg", "foreground"):
                side, variant = "fg", ""
            bt = "f" if {"female", "thin", "pregnant"} & all_toks else \
                 "m" if {"male", "muscular"} & all_toks else "u"
            toks = [s for s in key_parts if s not in NOISE]
            if variant and variant not in toks:
                toks.append(variant)
            item_id = slug("_".join(toks))
            if item_id:
                witems.setdefault(item_id, {}).setdefault(side, {}).setdefault(bt, {})[a] = p
        for a in ANIMS:
            for p in glob.glob(os.path.join(SHEETS, top, "**", a, "*.png"), recursive=True):
                segs = os.path.relpath(p, SHEETS).replace("\\", "/").split("/")
                add(a, p, segs[1:-2], os.path.splitext(segs[-1])[0], set(segs))
            for p in glob.glob(os.path.join(SHEETS, top, "**", f"{a}.png"), recursive=True):
                segs = os.path.relpath(p, SHEETS).replace("\\", "/").split("/")
                add(a, p, segs[1:-1], "", set(segs[:-1]))
        wcat = {"z": zfg, "zback": zbg, "recolor": None, "items": {}}
        PAINTS = {"red", "blue", "green", "navy", "gold", "black", "white",
                  "purple", "pink", "orange", "silver", "maroon"}
        for item_id, sides in sorted(witems.items()):
            # the heater shield ships a full heraldry system (2600+ combos) — keep
            # base shapes plus a curated set of solid paints
            if top == "shield":
                toks = item_id.split("_")
                if not (len(toks) <= 3 or ("paint" in toks and toks[-1] in PAINTS)):
                    continue
            entry = {}
            for side, bts in sides.items():
                for bt, animfiles in bts.items():
                    v = export_variant(None, f"{out}_{item_id}_{side}_{bt}",
                                       anims_from_files=animfiles)
                    if v:
                        entry[f"{side}_{bt}"] = v
            if entry:
                wcat["items"][item_id] = entry
        if wcat["items"]:
            manifest["categories"][out] = wcat

    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w"))
    src = os.path.join(LPC, "CREDITS.csv")
    if os.path.exists(src):
        shutil.copy(src, os.path.join(OUT, "CREDITS.csv"))
    print(f"exported {saved} frame files | heads: {list(manifest['heads'])}")
    for out, cat in sorted(manifest["categories"].items()):
        print(f"  {out:10s} {len(cat['items']):4d} items")

if __name__ == "__main__":
    build()
