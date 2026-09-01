from pathlib import Path
import json


def replace_one(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one occurrence, found {count}: {old!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Standalone resource syntax defects found by parsing every JSON resource.
replace_one(
    "forge-gui/res/adventure/common/ui/quests_portrait.json",
    '      "height": 413\n\t  "fontColor":"black"',
    '      "height": 413,\n\t  "fontColor":"black"',
)
replace_one(
    "forge-gui/res/adventure/common/ui/statistic_portrait.json",
    '      "y": 440\n    }\n    {\n      "type": "TextButton",\n      "name": "toggleAward"',
    '      "y": 440\n    },\n    {\n      "type": "TextButton",\n      "name": "toggleAward"',
)
replace_one(
    "forge-gui/res/adventure/common/world/biomes/outlands.json",
    '  "spriteNames": []\n \t"structures": [',
    '  "spriteNames": [],\n \t"structures": [',
)

# Java String values must be compared by value, not object identity.
replace_one(
    "forge-ai/src/main/java/forge/ai/ComputerUtilMana.java",
    'if (manaSourceType != "") {',
    'if (!manaSourceType.isEmpty()) {',
)
replace_one(
    "forge-gui-mobile/src/forge/card/CardRenderer.java",
    'if (imageKey == "" && cardArt == null)',
    'if ("".equals(imageKey) && cardArt == null)',
)
replace_one(
    "forge-gui-mobile/src/forge/screens/settings/GuiDownloader.java",
    'callback.accept(getButton(0).getText() == "OK");',
    'callback.accept("OK".equals(getButton(0).getText()));',
)
replace_one(
    "forge-gui-desktop/src/main/java/forge/download/GuiDownloader.java",
    'callback.accept(btnStart.getText() == "OK");',
    'callback.accept("OK".equals(btnStart.getText()));',
)
replace_one(
    "forge-game/src/main/java/forge/game/spellability/TargetRestrictions.java",
    'maxTargets != "1");',
    '!"1".equals(maxTargets));',
)
replace_one(
    "forge-game/src/main/java/forge/game/card/Card.java",
    'if (meld != "" && !hasMeldEffect) {',
    'if (!meld.isEmpty() && !hasMeldEffect) {',
)

# Use the rarity enum directly instead of String identity.
group = Path("forge-gui/src/main/java/forge/itemmanager/GroupDef.java")
g = group.read_text(encoding="utf-8")
if "import forge.card.CardRarity;" in g:
    raise RuntimeError("GroupDef already imports CardRarity unexpectedly")
if g.count("import forge.card.CardEdition;") != 1:
    raise RuntimeError("GroupDef CardEdition import guard failed")
old_rarity = 'if (((PaperCard) item).getRarity().toString() == "R"){' 
if g.count(old_rarity) != 1:
    raise RuntimeError("GroupDef rarity comparison guard failed")
g = g.replace(
    "import forge.card.CardEdition;\n",
    "import forge.card.CardEdition;\nimport forge.card.CardRarity;\n",
    1,
)
g = g.replace(old_rarity, "if (((PaperCard) item).getRarity() == CardRarity.Rare) {", 1)
group.write_text(g, encoding="utf-8")

# The prior MakeCard hardening must get null instead of throwing before its null fallback.
replace_one(
    "forge-game/src/main/java/forge/game/ability/effects/MakeCardEffect.java",
    'pc = pack.stream().filter(p -> p.getRules().getMainPart().getName().equals(name)).findAny().get();',
    'pc = pack.stream().filter(p -> p.getRules().getMainPart().getName().equals(name)).findAny().orElse(null);',
)

# Card-only reward conversion must skip non-card rewards rather than dereferencing/retaining null.
reward = Path("forge-gui-mobile/src/forge/adventure/data/RewardData.java")
r = reward.read_text(encoding="utf-8")
old = """            for (Reward data : dataList) {
                PaperCard card = data.getCard();
                if (card.isVeryBasicLand()) {"""
new = """            for (Reward data : dataList) {
                PaperCard card = data.getCard();
                if (card == null) {
                    continue;
                }
                if (card.isVeryBasicLand()) {"""
if r.count(old) != 1:
    raise RuntimeError("RewardData variant branch guard failed")
r = r.replace(old, new, 1)
old = """        } else {
            for (Reward data : dataList) {
                ret.add(data.getCard());
            }
        }"""
new = """        } else {
            for (Reward data : dataList) {
                PaperCard card = data.getCard();
                if (card != null) {
                    ret.add(card);
                }
            }
        }"""
if r.count(old) != 1:
    raise RuntimeError("RewardData normal branch guard failed")
reward.write_text(r.replace(old, new, 1), encoding="utf-8")

# #10730: HIDDEN is an implementation marker and must not leak into Pump stack text.
pump = Path("forge-game/src/main/java/forge/game/ability/effects/PumpEffect.java")
t = pump.read_text(encoding="utf-8")
old = """                keywords.addAll(Arrays.asList(sa.getParam(\"KW\").split(\" & \")));
            }

            if (sa.hasParam(\"IfDesc\")) {"""
new = """                keywords.addAll(Arrays.asList(sa.getParam(\"KW\").split(\" & \")));
            }
            final boolean cantBlockThisTurn = keywords.remove(\"HIDDEN CARDNAME can't block.\");

            if (sa.hasParam(\"IfDesc\")) {"""
if t.count(old) != 1:
    raise RuntimeError("PumpEffect keyword parse guard failed")
t = t.replace(old, new, 1)
old = """            for (int i = 0; i < keywords.size(); i++) {
                sb.append(keywords.get(i).toLowerCase());
                sb.append(keywords.size() > 2 && i+1 != keywords.size() ? \", \" : \"\");
                sb.append(keywords.size() == 2 && i == 0 ? \" \" : \"\");
                sb.append(i+2 == keywords.size() ? \"and \" : \"\");
            }

            if (sa.hasParam(\"CanBlockAny\")) {
                if (gets || gains) {"""
new = """            for (int i = 0; i < keywords.size(); i++) {
                sb.append(keywords.get(i).toLowerCase());
                sb.append(keywords.size() > 2 && i+1 != keywords.size() ? \", \" : \"\");
                sb.append(keywords.size() == 2 && i == 0 ? \" \" : \"\");
                sb.append(i+2 == keywords.size() ? \"and \" : \"\");
            }

            if (cantBlockThisTurn) {
                if (gains) {
                    sb.append(\" and \" );
                } else if (gets) {
                    // P/T formatting above already leaves a trailing space.
                    sb.append(\"and \" );
                }
                sb.append(\"can't block\");
            }

            if (sa.hasParam(\"CanBlockAny\")) {
                if (gets || gains || cantBlockThisTurn) {"""
if t.count(old) != 1:
    raise RuntimeError("PumpEffect description guard failed")
t = t.replace(old, new, 1)
old = """            } else if (sa.hasParam(\"CanBlockAmount\")) {
                if (gets || gains) {"""
new = """            } else if (sa.hasParam(\"CanBlockAmount\")) {
                if (gets || gains || cantBlockThisTurn) {"""
if t.count(old) != 1:
    raise RuntimeError("PumpEffect block-amount guard failed")
pump.write_text(t.replace(old, new, 1), encoding="utf-8")

# #8611: do not synthesize CanBlockAny text if the intrinsic ability text already contains it.
replace_one(
    "forge-game/src/main/java/forge/game/card/CardView.java",
    '        if (getCanBlockAny()) {\n            sb.append("\\r\\n\\r\\n");\n            sb.append("CARDNAME can block any number of creatures.".replaceAll("CARDNAME", getName()));',
    '        if (getCanBlockAny() && !state.getAbilityText().contains("can block any number of creatures")) {\n            sb.append("\\r\\n\\r\\n");\n            sb.append("CARDNAME can block any number of creatures.".replaceAll("CARDNAME", getName()));',
)

# Every standalone JSON resource must be syntactically valid after the three repairs.
failures = []
count = 0
for p in Path(".").rglob("*.json"):
    if ".git" in p.parts or "target" in p.parts:
        continue
    count += 1
    try:
        json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        failures.append(f"{p}: {e}")
if failures:
    raise RuntimeError("Standalone JSON validation failures:\n" + "\n".join(failures))
print(f"Validated {count} standalone JSON resources")
