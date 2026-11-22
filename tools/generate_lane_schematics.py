#!/usr/bin/env python3
"""Generate and verify MonaBowling's one-lane Litematica/Sponge schematics.

The geometry is the Phase 5 default: the throw line is 18 blocks before the
head-pin marker, the 100-pin final-row marker is around 21 blocks after it,
and the service/switch marker is around 22 blocks after it.
"""

from __future__ import annotations

import argparse
import hashlib
import math
import sys
import time
from pathlib import Path

try:
    import nbtlib
    from nbtlib import ByteArray, Compound, File, Int, IntArray, List, Long
    from nbtlib import LongArray, Short, String
except ImportError as exc:  # pragma: no cover - friendly CLI failure
    raise SystemExit(
        "nbtlib is required. Run: python -m pip install -r tools/requirements-schematic.txt"
    ) from exc


WIDTH = 11
HEIGHT = 8
LENGTH = 33
DATA_VERSION = 4790  # Minecraft Java/Paper 26.1.2
REGION_NAME = "MonaBowling_OneLane"

AIR = "minecraft:air"
DEEPSLATE = "minecraft:deepslate_bricks"
FOUNDATION = "minecraft:polished_deepslate"
APPROACH = "minecraft:dark_oak_planks"
LANE = "minecraft:smooth_quartz"
GUTTER = "minecraft:black_concrete"
FOUL = "minecraft:red_concrete"
HEAD_PIN = "minecraft:gold_block"
FINAL_ROW = "minecraft:light_blue_concrete"
SERVICE = "minecraft:magenta_concrete"
RAIL = "minecraft:yellow_concrete"
LIGHT = "minecraft:sea_lantern"
GLASS = "minecraft:gray_stained_glass"


def block_index(x: int, y: int, z: int) -> int:
    """Return the Litematica/Sponge index (X fastest, then Z, then Y)."""
    return x + z * WIDTH + y * WIDTH * LENGTH


def build_blocks() -> list[str]:
    blocks = [AIR] * (WIDTH * HEIGHT * LENGTH)

    def set_block(x: int, y: int, z: int, state: str) -> None:
        assert 0 <= x < WIDTH and 0 <= y < HEIGHT and 0 <= z < LENGTH
        blocks[block_index(x, y, z)] = state

    # Foundation and walkable surface. The floor is Y=0, so player feet are Y=1.
    for z in range(LENGTH):
        for x in range(WIDTH):
            set_block(x, 0, z, FOUNDATION)

    # Approach: nine clear interior blocks, five blocks deep.
    for z in range(0, 5):
        for x in range(1, 10):
            set_block(x, 0, z, APPROACH)

    # Play surface and gutters. Foul/throw line is Z=5.
    for z in range(5, 29):
        set_block(1, 0, z, GUTTER)
        set_block(9, 0, z, GUTTER)
        for x in range(2, 9):
            set_block(x, 0, z, LANE)

    for x in range(2, 9):
        set_block(x, 0, 5, FOUL)

    # Registration/build markers.
    # Throw line center: (5, 1, 5); head pin: (5, 1, 23), exactly 18m ahead.
    set_block(5, 0, 23, HEAD_PIN)
    for x in range(2, 9):
        set_block(x, 0, 28, FINAL_ROW)
    set_block(5, 0, 29, SERVICE)
    set_block(2, 0, 29, RAIL)
    set_block(8, 0, 29, RAIL)

    # Rear pit and back wall.
    for z in range(30, 32):
        for x in range(1, 10):
            set_block(x, 0, z, GUTTER)

    # Side walls, back wall, and a roof with regular lighting.
    for z in range(LENGTH):
        for y in range(1, 7):
            set_block(0, y, z, DEEPSLATE)
            set_block(10, y, z, DEEPSLATE)
    for x in range(WIDTH):
        for y in range(1, 7):
            set_block(x, y, 32, DEEPSLATE)

    for z in range(LENGTH):
        for x in range(WIDTH):
            set_block(x, 7, z, DEEPSLATE if x in (0, 10) else GLASS)
    for z in (2, 8, 14, 20, 26, 31):
        for x in (2, 5, 8):
            set_block(x, 7, z, LIGHT)

    return blocks


def make_palette(blocks: list[str]) -> tuple[list[str], list[int]]:
    # Air must remain palette id 0 for maximum compatibility.
    states = [AIR]
    state_to_id = {AIR: 0}
    ids: list[int] = []
    for state in blocks:
        if state not in state_to_id:
            state_to_id[state] = len(states)
            states.append(state)
        ids.append(state_to_id[state])
    return states, ids


def signed_64(value: int) -> int:
    value &= 0xFFFFFFFFFFFFFFFF
    return value - (1 << 64) if value >= (1 << 63) else value


def pack_litematic(ids: list[int], palette_size: int) -> tuple[int, list[int]]:
    bits = max(2, math.ceil(math.log2(max(1, palette_size))))
    words = [0] * math.ceil(len(ids) * bits / 64)
    mask = (1 << bits) - 1
    for index, value in enumerate(ids):
        if value > mask:
            raise ValueError(f"palette id {value} does not fit in {bits} bits")
        start = index * bits
        word = start >> 6
        offset = start & 63
        words[word] |= value << offset
        words[word] &= 0xFFFFFFFFFFFFFFFF
        if offset + bits > 64:
            words[word + 1] |= value >> (64 - offset)
            words[word + 1] &= 0xFFFFFFFFFFFFFFFF
    return bits, [signed_64(value) for value in words]


def encode_varints(ids: list[int]) -> list[int]:
    encoded: list[int] = []
    for original in ids:
        value = original
        while True:
            byte = value & 0x7F
            value >>= 7
            if value:
                byte |= 0x80
            encoded.append(byte - 256 if byte >= 128 else byte)
            if not value:
                break
    return encoded


def palette_compound(state: str) -> Compound:
    # All template blocks use default states, so no Properties tag is needed.
    return Compound({"Name": String(state)})


def write_litematic(path: Path, states: list[str], ids: list[int], now_ms: int) -> None:
    _, packed = pack_litematic(ids, len(states))
    non_air = sum(value != 0 for value in ids)
    metadata = Compound(
        {
            "Name": String("MonaBowling Phase 5 - One Lane"),
            "Author": String("MONAKA SERVER"),
            "Description": String(
                "11x33 Phase 5 lane; red=throw line, gold=head pin, "
                "light blue=100-pin final row, magenta=service switch"
            ),
            "RegionCount": Int(1),
            "TotalVolume": Int(WIDTH * HEIGHT * LENGTH),
            "TotalBlocks": Int(non_air),
            "TimeCreated": Long(now_ms),
            "TimeModified": Long(now_ms),
            "EnclosingSize": Compound(
                {"x": Int(WIDTH), "y": Int(HEIGHT), "z": Int(LENGTH)}
            ),
        }
    )
    region = Compound(
        {
            "Position": Compound({"x": Int(0), "y": Int(0), "z": Int(0)}),
            "Size": Compound(
                {"x": Int(WIDTH), "y": Int(HEIGHT), "z": Int(LENGTH)}
            ),
            "BlockStatePalette": List[Compound](
                [palette_compound(state) for state in states]
            ),
            "BlockStates": LongArray(packed),
            "TileEntities": List[Compound]([]),
            "Entities": List[Compound]([]),
            "PendingBlockTicks": List[Compound]([]),
            "PendingFluidTicks": List[Compound]([]),
        }
    )
    root = Compound(
        {
            "Version": Int(7),
            "SubVersion": Int(1),
            "MinecraftDataVersion": Int(DATA_VERSION),
            "Metadata": metadata,
            "Regions": Compound({REGION_NAME: region}),
        }
    )
    File(root).save(path, gzipped=True)


def write_sponge(path: Path, states: list[str], ids: list[int], now_ms: int) -> None:
    palette = Compound({state: Int(index) for index, state in enumerate(states)})
    schematic = Compound(
        {
            "Version": Int(3),
            "DataVersion": Int(DATA_VERSION),
            "Metadata": Compound(
                {
                    "Name": String("MonaBowling Phase 5 - One Lane"),
                    "Author": String("MONAKA SERVER"),
                    "Date": Long(now_ms),
                    "RequiredMods": List[String]([]),
                }
            ),
            "Width": Short(WIDTH),
            "Height": Short(HEIGHT),
            "Length": Short(LENGTH),
            "Offset": IntArray([0, 0, 0]),
            "Blocks": Compound(
                {
                    "Palette": palette,
                    "Data": ByteArray(encode_varints(ids)),
                    "BlockEntities": List[Compound]([]),
                }
            ),
            "Entities": List[Compound]([]),
        }
    )
    # Sponge v3 requires Schematic nested inside the unnamed root compound.
    File(Compound({"Schematic": schematic})).save(path, gzipped=True)


def unpack_longs(words: list[int], count: int, bits: int) -> list[int]:
    unsigned = [value & 0xFFFFFFFFFFFFFFFF for value in words]
    mask = (1 << bits) - 1
    values: list[int] = []
    for index in range(count):
        start = index * bits
        word = start >> 6
        offset = start & 63
        value = unsigned[word] >> offset
        if offset + bits > 64:
            value |= unsigned[word + 1] << (64 - offset)
        values.append(value & mask)
    return values


def decode_varints(data: list[int], expected: int) -> list[int]:
    values: list[int] = []
    value = 0
    shift = 0
    for signed_byte in data:
        byte = signed_byte & 0xFF
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
            if shift > 35:
                raise ValueError("invalid VarInt in Sponge block data")
        else:
            values.append(value)
            value = 0
            shift = 0
    if shift != 0 or len(values) != expected:
        raise ValueError(f"decoded {len(values)} block ids, expected {expected}")
    return values


def verify_files(litematic_path: Path, schem_path: Path, expected: list[str]) -> None:
    volume = WIDTH * HEIGHT * LENGTH

    lite = nbtlib.load(litematic_path)
    assert int(lite["Version"]) == 7
    assert int(lite["SubVersion"]) == 1
    assert int(lite["MinecraftDataVersion"]) == DATA_VERSION
    region = lite["Regions"][REGION_NAME]
    lite_palette = [str(entry["Name"]) for entry in region["BlockStatePalette"]]
    bits = max(2, math.ceil(math.log2(max(1, len(lite_palette)))))
    lite_ids = unpack_longs([int(value) for value in region["BlockStates"]], volume, bits)
    lite_blocks = [lite_palette[value] for value in lite_ids]
    assert lite_blocks == expected, "Litematica block array did not round-trip"

    sponge_file = nbtlib.load(schem_path)
    sponge = sponge_file["Schematic"]
    assert int(sponge["Version"]) == 3
    assert int(sponge["DataVersion"]) == DATA_VERSION
    assert (int(sponge["Width"]), int(sponge["Height"]), int(sponge["Length"])) == (
        WIDTH,
        HEIGHT,
        LENGTH,
    )
    inverse = {int(value): key for key, value in sponge["Blocks"]["Palette"].items()}
    sponge_ids = decode_varints(
        [int(value) for value in sponge["Blocks"]["Data"]], volume
    )
    sponge_blocks = [inverse[value] for value in sponge_ids]
    assert sponge_blocks == expected, "Sponge block array did not round-trip"

    # Critical markers must remain exact in both exports.
    assert expected[block_index(5, 0, 5)] == FOUL
    assert expected[block_index(5, 0, 23)] == HEAD_PIN
    assert expected[block_index(5, 0, 28)] == FINAL_ROW
    assert expected[block_index(5, 0, 29)] == SERVICE


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "schematics",
    )
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    blocks = build_blocks()
    states, ids = make_palette(blocks)
    # Fixed metadata time keeps regenerated binary files reproducible.
    now_ms = 1_795_232_400_000  # 2026-11-21T11:00:00Z
    litematic_path = args.output / "MonaBowling-Phase5-OneLane.litematic"
    schem_path = args.output / "MonaBowling-Phase5-OneLane.schem"
    write_litematic(litematic_path, states, ids, now_ms)
    write_sponge(schem_path, states, ids, now_ms)
    verify_files(litematic_path, schem_path, blocks)

    checksum_path = args.output / "SHA256SUMS.txt"
    checksum_path.write_text(
        f"{sha256(litematic_path)}  {litematic_path.name}\n"
        f"{sha256(schem_path)}  {schem_path.name}\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"OK: {litematic_path}")
    print(f"OK: {schem_path}")
    print(f"palette={len(states)} blocks={len(blocks)} non_air={sum(v != 0 for v in ids)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
