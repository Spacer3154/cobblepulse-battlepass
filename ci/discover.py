#!/usr/bin/env python3
"""CI : liste les versions des dependances et extrait (javap) les API de Cobblemon, Battletower et LuckPerms utilisees."""
import json, os, re, subprocess, sys, urllib.request, zipfile

OUT = sys.argv[1] if len(sys.argv) > 1 else "out/discover"
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "cobblepulse-battlepass-ci/1.0 (github actions)"}
BT_JAR_URL = "https://github.com/Spacer3154/cobblepulse-battletower/raw/ci-output/cobblepulse-battletower-1.0.0.jar"


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


lines = []


def section(title, items):
    lines.append("== " + title)
    lines.extend(items)


SLUGS = ["cobblemon", "fabric-api", "fabric-language-kotlin", "luckperms", "placeholder-api"]
modrinth = {}
for slug in SLUGS:
    try:
        vs = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D"))
        modrinth[slug] = vs
        section("modrinth " + slug, [f'{v["version_number"]} | {v["date_published"]}' for v in vs[:5]])
    except Exception as e:
        section("modrinth " + slug, ["ERR " + str(e)])

with open(os.path.join(OUT, "modrinth.json"), "w") as fh:
    json.dump({k: [{"version_number": v["version_number"], "id": v["id"], "files": v["files"],
                    "dependencies": v.get("dependencies", [])} for v in vs[:10]] for k, vs in modrinth.items()}, fh, indent=1)


def pick(slug, want=None):
    for v in modrinth.get(slug, []):
        if want is None or want in v["version_number"]:
            return v
    vs = modrinth.get(slug) or []
    return vs[0] if vs else None


def download(v, name):
    f = [f for f in v["files"] if f["primary"]] or v["files"]
    path = os.path.join(OUT, name)
    with open(path, "wb") as fh:
        fh.write(get(f[0]["url"]))
    return path


def javap(jar, classes, flags=("-p",)):
    out = []
    for i in range(0, len(classes), 40):
        chunk = classes[i:i + 40]
        r = subprocess.run(["javap", *flags, "-cp", jar, *chunk], capture_output=True, text=True)
        out.append(r.stdout + r.stderr)
    return "\n".join(out)


def dump(jar, names, prefixes, fname, exclude=("$$", "$lambda", "$WhenMappings")):
    cls = []
    for n in names:
        if not n.endswith(".class") or any(x in n for x in exclude):
            continue
        if any(n.startswith(p) for p in prefixes):
            cls.append(n[:-6].replace("/", "."))
    cls = sorted(set(cls))
    with open(os.path.join(OUT, fname), "w") as fh:
        fh.write(javap(jar, cls))


cob = pick("cobblemon", "1.8.4")
if cob:
    jar = download(cob, "cobblemon.jar")
    z = zipfile.ZipFile(jar)
    names = z.namelist()
    with open(os.path.join(OUT, "cobblemon_all_classes.txt"), "w") as fh:
        fh.write("\n".join(n for n in names if n.endswith(".class") and "$$" not in n))
    C = "com/cobblemon/mod/common/"
    E = C + "api/events/"
    dump(jar, names, [E + "pokemon/PokemonCapturedEvent", E + "pokemon/evolution/EvolutionCompleteEvent", E + "pokemon/HatchEggEvent",
                      E + "pokemon/TradeEvent", E + "pokemon/LevelUpEvent", E + "pokemon/FossilRevivedEvent",
                      E + "pokemon/PokedexDataChangedEvent", E + "fishing/BobberSpawnPokemonEvent", E + "berry/BerryHarvestEvent",
                      E + "farming/ApricornHarvestEvent", E + "pokedex/scanning/PokemonScannedEvent", E + "storage/ReleasePokemonEvent",
                      E + "battles/BattleVictoryEvent", E + "battles/BattleFaintedEvent", E + "pokemon/PokemonSentEvent",
                      E + "pokemon/interaction/", E + "starter/StarterChosenEvent", E + "pokemon/CollectEggEvent"], "api_events.txt")
    dump(jar, names, [C + "item/PokemonItem", C + "api/pokedex/PokedexEntryProgress", C + "api/pokedex/PokedexManager",
                      C + "api/pokedex/AbstractPokedexManager", C + "api/pokedex/SpeciesDexRecord", C + "api/pokedex/Dexes",
                      C + "api/berry/Berry.class", C + "api/apricorn/Apricorn", C + "pokeball/PokeBall.class",
                      C + "entity/pokeball/EmptyPokeBallEntity.class", C + "entity/fishing/PokeRodFishingBobberEntity.class",
                      C + "api/pokemon/labels/CobblemonPokemonLabels", C + "pokemon/Gender", C + "pokemon/Nature.class",
                      C + "api/pokemon/Natures", C + "api/pokeball/PokeBalls", C + "trade/TradeParticipant",
                      C + "trade/PlayerTradeParticipant", C + "api/storage/pc/PCStore.class", C + "api/storage/party/PlayerPartyStore.class",
                      C + "api/storage/PokemonStore.class", C + "api/storage/StoreCoordinates", C + "api/storage/pc/PCPosition",
                      C + "api/storage/party/PartyPosition", C + "util/PlayerExtensionsKt", C + "api/pokemon/PokemonPropertyExtractor",
                      C + "pokemon/IVs", C + "pokemon/EVs", C + "api/pokemon/stats/Stats"], "api_misc.txt")
    dump(jar, names, [C + "client/gui/PokemonGuiUtilsKt", C + "client/render/models/blockbench/FloatingState",
                      C + "client/render/models/blockbench/PosableState", C + "entity/PoseType", C + "pokemon/RenderablePokemon",
                      C + "client/render/item/PokemonItemRenderer"], "api_client.txt")
    dump(jar, names, [C + "pokemon/Pokemon.class", C + "pokemon/Species.class", C + "pokemon/FormData.class",
                      C + "api/battles/model/PokemonBattle.class", C + "battles/pokemon/BattlePokemon.class",
                      C + "api/battles/model/actor/BattleActor.class", C + "api/battles/model/actor/ActorType",
                      C + "battles/actor/PlayerBattleActor.class", C + "battles/actor/PokemonBattleActor.class",
                      C + "entity/pokemon/PokemonEntity.class"], "api_core.txt")
    os.remove(jar)

# ---------------------------------------------------------------- Battletower (cibles des mixins)
try:
    path = os.path.join(OUT, "battletower.jar")
    with open(path, "wb") as fh:
        fh.write(get(BT_JAR_URL))
    z = zipfile.ZipFile(path)
    names = z.namelist()
    B = "fr/cobblepulse/battletower/"
    dump(path, names, [B + "tower/TowerManager", B + "tower/DailyBoss", B + "battle/TowerBattle", B + "data/RunState",
                       B + "economy/BattlePoints", B + "storage/PlayerStore", B + "data/PlayerData"], "api_battletower.txt")
    r = subprocess.run(["javap", "-p", "-s", "-cp", path, "fr.cobblepulse.battletower.tower.TowerManager",
                        "fr.cobblepulse.battletower.tower.DailyBoss"], capture_output=True, text=True)
    with open(os.path.join(OUT, "battletower_descriptors.txt"), "w") as fh:
        fh.write(r.stdout + r.stderr)
    try:
        with open(os.path.join(OUT, "battletower.fabric.mod.json"), "wb") as fh:
            fh.write(z.read("fabric.mod.json"))
    except KeyError:
        pass
    section("battletower", [f"{len(names)} entrees"])
except Exception as e:
    section("battletower", ["ERR " + str(e)])

with open(os.path.join(OUT, "versions.txt"), "w") as fh:
    fh.write("\n".join(lines) + "\n")
print("discover done")
