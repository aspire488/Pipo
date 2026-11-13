"""
Builds Pipo's 3D assets from scratch and exports them for the app's 3D stage.

Run inside Blender (Blender MCP, or: blender -b -P art/blender/build_pipo3d.py).
Outputs (paths relative to the repo's Pipo/ folder):
  art/pipo3d.blend                          editable source
  app/src/main/assets/stage/models/pipo.glb     character + MK1 armor (one rig)
  app/src/main/assets/stage/models/lab.glb      armor dock, status halo, holo panel

Units are the stage's room units (the room is 296 wide; Pipo is ~100 tall before the stage
scales him by 0.4). Pivots are Blender empties named exactly like the stage's rig groups
(Body, Head, Antenna, Arm.L/R, Leg.L/R), so the stage animates them directly.
Armor parts are prefixed MK1_ and parented to the rig groups; the stage shows/hides them and
swaps their materials for Mk 0 (cardboard) / Mk II / Mk III.
"""
import bpy, math, os

ROOT = os.environ.get("PIPO_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "stage", "models")

def T(x, y, z):
    """Stage coordinates (y up, +z towards the camera) -> Blender (z up, -y forward)."""
    return (x, -z, y)

# ---------------------------------------------------------------- reset
# Data API only (no mesh operators): works headless and in a live MCP session alike.
import bmesh
from mathutils import Matrix, Vector
scene = bpy.context.scene
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights):
    for d in list(coll):
        coll.remove(d)
for c in list(bpy.data.collections):
    bpy.data.collections.remove(c)
scene.unit_settings.system = 'NONE'

def collection(name):
    c = bpy.data.collections.new(name)
    scene.collection.children.link(c)
    return c

COL_PIPO = collection("Pipo")
COL_ARMOR = collection("Armor_MK1")
COL_LAB = collection("Lab")
COL_STUDIO = collection("Studio")

# ---------------------------------------------------------------- materials
def material(name, color, metal=0.0, rough=0.5, emit=None, strength=0.0, coat=0.0, alpha=1.0):
    m = bpy.data.materials.new(name)
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*color, 1)
    bsdf.inputs["Metallic"].default_value = metal
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Coat Weight"].default_value = coat
    if emit:
        bsdf.inputs["Emission Color"].default_value = (*emit, 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    if alpha < 1:
        bsdf.inputs["Alpha"].default_value = alpha
        m.blend_method = 'BLEND' if hasattr(m, "blend_method") else None
    m.diffuse_color = (*color, alpha)
    return m

def srgb(h):
    """#rrggbb -> linear rgb (Principled wants linear)."""
    h = h.lstrip('#')
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in c)

M = {
    "Shell": material("Pipo_Shell", srgb("#F2F5F9"), rough=0.42, coat=0.15),
    "Shade": material("Pipo_ShellShade", srgb("#C9D2DE"), rough=0.45),
    "Arm": material("Pipo_Arm", srgb("#D9E0E9"), rough=0.4),
    "Joint": material("Pipo_Joint", srgb("#465266"), metal=0.35, rough=0.45),
    "Visor": material("Pipo_Visor", srgb("#3B4862"), rough=0.36, coat=0.3),   # graphite bezel around the screen
    "Glow": material("Pipo_Glow", srgb("#8FF5E2"), rough=0.3, emit=srgb("#8FF5E2"), strength=1.5),
    "Red": material("Armor_Red", srgb("#B3202A"), metal=0.45, rough=0.36, coat=0.3),
    "Gold": material("Armor_Gold", srgb("#E2B24A"), metal=0.85, rough=0.32),
    "Core": material("Armor_Core", srgb("#DDFBFF"), rough=0.2, emit=srgb("#9FF3FF"), strength=2.0),
    "Dock": material("Lab_Dock", srgb("#2B323E"), metal=0.6, rough=0.35),
    "DockTrim": material("Lab_DockTrim", srgb("#5C6B82"), metal=0.8, rough=0.3),
    "Halo": material("Lab_Halo", srgb("#8FF5E2"), rough=0.3, emit=srgb("#8FF5E2"), strength=2.5),
    "Holo": material("Lab_HoloFrame", srgb("#1A2433"), metal=0.7, rough=0.25),
}

# ---------------------------------------------------------------- shape helpers
def _obj(name, bm, mat, at, parent, col, rot=(0, 0, 0), smooth=True):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me); bm.free()
    if smooth:
        for p in me.polygons:
            p.use_smooth = True
    if mat:
        me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    o.location = at
    o.rotation_euler = rot
    col.objects.link(o)
    if parent:
        o.parent = parent
    return o

def _bevel(o, width, segs, angle_only=False):
    b = o.modifiers.new("Round", 'BEVEL')
    b.width = width
    b.segments = segs
    b.limit_method = 'ANGLE' if angle_only else 'NONE'
    b.use_clamp_overlap = True
    return o

def rbox(name, w, h, d, r, mat, at, parent=None, col=COL_PIPO, segs=5):
    """Rounded box, w x h x d in stage axes, corner radius r."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1)
    bmesh.ops.scale(bm, vec=(w, d, h), verts=bm.verts)
    o = _obj(name, bm, mat, at, parent, col)
    if r > 0.05:
        _bevel(o, min(r, w / 2 - 0.01, h / 2 - 0.01, d / 2 - 0.01), segs)
    return o

def cyl(name, r, h, mat, at, parent=None, col=COL_PIPO, rot=(0, 0, 0), verts=32, r2=None):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=r if r2 is None else r2, depth=h)
    o = _obj(name, bm, mat, at, parent, col, rot)
    return _bevel(o, min(0.6, h / 4, r / 4), 2, angle_only=True)

def ball(name, r, mat, at, parent=None, col=COL_PIPO, scale=(1, 1, 1), segs=32):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=segs // 2, radius=r)
    bmesh.ops.scale(bm, vec=scale, verts=bm.verts)
    return _obj(name, bm, mat, at, parent, col)

def torus_bm(major, minor, seg=40, mseg=10, arc=None):
    """Torus in the XY plane; arc=(a0, a1) keeps only that slice of the ring."""
    bm = bmesh.new()
    a0, a1 = arc if arc else (0, math.tau)
    n = seg if not arc else max(4, int(seg * (a1 - a0) / math.tau))
    closed = arc is None
    rings = []
    for i in range(n + (0 if closed else 1)):
        a = a0 + (a1 - a0) * i / n
        c, s_ = math.cos(a), math.sin(a)
        rings.append([bm.verts.new(((major + minor * math.cos(b)) * c, (major + minor * math.cos(b)) * s_, minor * math.sin(b)))
                      for b in (j / mseg * math.tau for j in range(mseg))])
    for i in range(len(rings) - (0 if closed else 1)):
        r0, r1 = rings[i], rings[(i + 1) % len(rings)]
        for j in range(mseg):
            bm.faces.new((r0[j], r1[j], r1[(j + 1) % mseg], r0[(j + 1) % mseg]))
    if not closed:
        for rr in (rings[0], rings[-1]):
            bm.faces.new(rr)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return bm

def ring(name, major, minor, mat, at, parent=None, col=COL_PIPO, rot=(0, 0, 0)):
    return _obj(name, torus_bm(major, minor), mat, at, parent, col, rot)

def empty(name, at, parent=None, col=COL_PIPO):
    o = bpy.data.objects.new(name, None)
    o.empty_display_size = 3
    o.location = at
    col.objects.link(o)
    if parent:
        o.parent = parent
    return o

FACE_FRONT = (math.pi / 2, 0, 0)   # a Blender cylinder (z axis) turned to face the stage camera (+z stage = -y Blender)
SIDEWAYS = (0, math.pi / 2, 0)     # axis along x

# ================================================================ PIPO
pipo = empty("Pipo", (0, 0, 0))
body = empty("Body", (0, 0, 0), pipo)

rbox("Torso", 25, 25, 20, 9, M["Shell"], T(0, 24.5, 0), body)
rbox("Belly", 15, 12, 1.6, 3, M["Shade"], T(0, 23.0, 9.5), body)
ring("Torso_Seam", 12.2, 0.55, M["Joint"], T(0, 30.5, 0), body).scale = (1.0, 0.8, 1)
cyl("Core", 4.8, 1.0, M["Glow"], T(0, 24.5, 10.6), body, rot=FACE_FRONT)
ring("Core_Ring", 5.6, 0.75, M["Joint"], T(0, 24.5, 10.6), body, rot=FACE_FRONT)
cyl("Neck", 4.6, 6, M["Joint"], T(0, 38, 0), body)

head = empty("Head", T(0, 40, 0), body)
rbox("Skull", 64, 50, 42, 13, M["Shell"], T(0, 25, 0), head, segs=6)
rbox("Visor", 48, 33.5, 2.4, 9.5, M["Visor"], T(0, 24.5, 20.4), head)
rbox("Brow_Line", 30, 1.2, 1.2, 0.5, M["Shade"], T(0, 47.0, 18.0), head)
for side, tag in ((-1, "L"), (1, "R")):
    ear = empty(f"Ear.{tag}", T(side * 33, 25, 0), head)
    cyl(f"Ear_Pod.{tag}", 6.5, 7, M["Shade"], (0, 0, 0), ear, rot=SIDEWAYS)
    cyl(f"Ear_Glow.{tag}", 3.3, 7.6, M["Glow"], (0, 0, 0), ear, rot=SIDEWAYS)
    ring(f"Ear_Ring.{tag}", 5.4, 0.7, M["Joint"], (side * 3.8, 0, 0), ear, rot=SIDEWAYS)
antenna = empty("Antenna", T(0, 49, -2), head)
cyl("Antenna_Stem", 1.25, 11, M["Joint"], T(0, 5.5, 0), antenna, r2=1.0)
ball("Antenna_Bulb", 4, M["Glow"], T(0, 13, 0), antenna)

for side, tag in ((-1, "L"), (1, "R")):
    arm = empty(f"Arm.{tag}", T(side * 14, 31, 0), body)
    ball(f"Shoulder.{tag}", 3.7, M["Joint"], (0, 0, 0), arm)
    cyl(f"UpperArm.{tag}", 2.6, 13, M["Arm"], T(0, -6.5, 0), arm, r2=2.3)
    ball(f"Hand.{tag}", 3.9, M["Shade"], T(0, -13.2, 0), arm, scale=(1, 0.9, 1.1))
    ball(f"Thumb.{tag}", 1.5, M["Shade"], T(-side * 2.6, -12.0, 2.0), arm)

    leg = empty(f"Leg.{tag}", T(side * 8, 12, 0), body)
    cyl(f"Shin.{tag}", 3.0, 9.5, M["Joint"], T(0, -4.75, 0), leg)
    ball(f"Foot.{tag}", 6, M["Shade"], T(0, -10.2, 3.5), leg, scale=(1, 1.15, 0.42))
    rbox(f"Sole.{tag}", 9, 1.0, 11, 0.45, M["Joint"], T(0, -12.0, 3.6), leg)

# ================================================================ MK1 ARMOR (same rig)
rbox("MK1_Chest", 27.5, 17, 23.5, 8, M["Red"], T(0, 28.2, 0.3), body, col=COL_ARMOR)
rbox("MK1_ChestPlate", 17, 8, 1.4, 3, M["Gold"], T(0, 31.2, 12.0), body, col=COL_ARMOR)
cyl("MK1_Core", 4.0, 1.4, M["Core"], T(0, 25.0, 12.3), body, col=COL_ARMOR, rot=FACE_FRONT)
ring("MK1_CoreRing", 5.0, 1.0, M["Gold"], T(0, 25.0, 12.2), body, col=COL_ARMOR, rot=FACE_FRONT)
ring("MK1_Collar", 6.4, 1.6, M["Gold"], T(0, 36.6, 0), body, col=COL_ARMOR)
rbox("MK1_Belt", 26.5, 3.6, 21.6, 3, M["Gold"], T(0, 13.6, 0), body, col=COL_ARMOR)
rbox("MK1_Buckle", 7, 4.4, 1.4, 1.2, M["Red"], T(0, 13.6, 11.0), body, col=COL_ARMOR)

rbox("MK1_Helm", 62, 9, 44, 8, M["Red"], T(0, 47.5, -1), head, col=COL_ARMOR, segs=6)
rbox("MK1_Brow", 44, 2.6, 3.0, 1.2, M["Gold"], T(0, 44.6, 21.4), head, col=COL_ARMOR)
rbox("MK1_Crest", 6, 4.2, 30, 2, M["Gold"], T(0, 52.2, -2), head, col=COL_ARMOR)
for side, tag in ((-1, "L"), (1, "R")):
    cyl(f"MK1_EarCap.{tag}", 7.6, 3.0, M["Gold"], T(side * 36.8, 25, 0), head, col=COL_ARMOR, rot=SIDEWAYS)
    cyl(f"MK1_EarGlow.{tag}", 3.0, 3.4, M["Core"], T(side * 37.4, 25, 0), head, col=COL_ARMOR, rot=SIDEWAYS)
    rbox(f"MK1_Cheek.{tag}", 3.2, 18, 10, 1.5, M["Red"], T(side * 31.6, 16, 15), head, col=COL_ARMOR)

arms = {o.name.split(".")[1]: o for o in bpy.data.objects if o.name.startswith("Arm.")}
legs = {o.name.split(".")[1]: o for o in bpy.data.objects if o.name.startswith("Leg.")}
for tag, arm in arms.items():
    ball(f"MK1_Pauldron.{tag}", 5.4, M["Red"], T(0, 0.8, 0), arm, col=COL_ARMOR, scale=(1.05, 1.05, 0.78))
    ring(f"MK1_PauldronRim.{tag}", 4.9, 0.75, M["Gold"], T(0, -1.2, 0), arm, col=COL_ARMOR)
    cyl(f"MK1_Gauntlet.{tag}", 3.5, 7.2, M["Red"], T(0, -8.4, 0), arm, col=COL_ARMOR, r2=3.2)
    ring(f"MK1_Cuff.{tag}", 3.55, 0.7, M["Gold"], T(0, -4.9, 0), arm, col=COL_ARMOR)
    ring(f"MK1_Repulsor.{tag}", 3.0, 0.55, M["Core"], T(0, -12.0, 0), arm, col=COL_ARMOR)
for tag, leg in legs.items():
    cyl(f"MK1_ShinGuard.{tag}", 3.8, 7.0, M["Red"], T(0, -5.0, 0), leg, col=COL_ARMOR)
    ring(f"MK1_KneeRing.{tag}", 3.85, 0.7, M["Gold"], T(0, -1.6, 0), leg, col=COL_ARMOR)
    ball(f"MK1_Boot.{tag}", 6.7, M["Red"], T(0, -9.7, 3.6), leg, col=COL_ARMOR, scale=(1, 1.16, 0.5))
    ball(f"MK1_ToeCap.{tag}", 3.2, M["Gold"], T(0, -10.6, 8.4), leg, col=COL_ARMOR, scale=(1.4, 1.0, 0.75))
    ring(f"MK1_Thruster.{tag}", 3.0, 0.8, M["Core"], T(0, -12.6, 3.6), leg, col=COL_ARMOR)

# ================================================================ LAB PROPS
# Armor dock: where the MK1 hangs when Pipo isn't wearing it (stage room coords).
dock = empty("ArmorDock", T(200, 0, 24), None, COL_LAB)
cyl("Dock_Base", 11, 2.2, M["Dock"], T(0, 1.1, 0), dock, col=COL_LAB, verts=48)
ring("Dock_BaseGlow", 9.4, 0.5, M["Halo"], T(0, 2.3, 0), dock, col=COL_LAB)
cyl("Dock_Spine", 1.6, 46, M["DockTrim"], T(0, 24, -5.5), dock, col=COL_LAB)
rbox("Dock_Yoke", 26, 2.4, 3, 1, M["DockTrim"], T(0, 38, -5.5), dock, col=COL_LAB)
for side in (-1, 1):
    cyl(f"Dock_Arm{'L' if side < 0 else 'R'}", 1.1, 9, M["DockTrim"], T(side * 12, 33.5, -5.5), dock, col=COL_LAB)
rbox("Dock_Header", 24, 6, 3, 2, M["Dock"], T(0, 48, -5.5), dock, col=COL_LAB)
rbox("Dock_Light", 18, 1.2, 0.8, 0.4, M["Halo"], T(0, 48, -3.7), dock, col=COL_LAB)
# Pegs that hold the hanging armor (the stage places armor clones at these anchors).
empty("Dock_Anchor", T(0, 2.2, 0), dock, COL_LAB)

# Status halo: follows Pipo's feet; 12 arc segments the stage lights up per AI state.
halo = empty("StatusHalo", (0, 0, 0), None, COL_LAB)
for i in range(12):
    a0 = i / 12 * math.tau
    _obj(f"Halo_Seg.{i:02d}", torus_bm(19, 0.7, 96, 8, (a0 + math.radians(3), a0 + math.radians(27))), M["Halo"], (0, 0, 0.6), halo, COL_LAB)

# Holo panel: frame + emitter; the stage draws live tool status onto "Holo_Screen".
holo = empty("HoloPanel", (0, 0, 0), None, COL_LAB)
rbox("Holo_Frame", 44, 26, 1.2, 3, M["Holo"], T(0, 0, 0), holo, col=COL_LAB)
rbox("Holo_Emitter", 10, 2, 3, 1, M["Halo"], T(0, -14.2, 0), holo, col=COL_LAB)
bm = bmesh.new()
bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=0.5)
bmesh.ops.scale(bm, vec=(40, 22, 1), verts=bm.verts)
# UVs: full 0..1 so the stage's canvas texture fills the screen
uv = bm.loops.layers.uv.new("UVMap")
for f in bm.faces:
    for l in f.loops:
        l[uv].uv = (l.vert.co.x / 40 + 0.5, l.vert.co.y / 22 + 0.5)
_obj("Holo_Screen", bm, M["Halo"], T(0, 0, 0.7), holo, COL_LAB, rot=FACE_FRONT, smooth=False)

# ================================================================ studio (for turntable checks only; not exported)
cam_data = bpy.data.cameras.new("Studio_Cam"); cam_data.lens = 60
cam = bpy.data.objects.new("Studio_Cam", cam_data)
cam.location = (70, -190, 70)
COL_STUDIO.objects.link(cam); scene.camera = cam
look = cam.constraints.new('TRACK_TO'); look.track_axis = 'TRACK_NEGATIVE_Z'; look.up_axis = 'UP_Y'
target = empty("Studio_Target", (0, 0, 45), None, COL_STUDIO); look.target = target
for name, loc, energy in (("Key", (80, -90, 140), 90000), ("Fill", (-120, -60, 60), 30000), ("Rim", (0, 120, 120), 60000)):
    ld = bpy.data.lights.new(name, 'AREA'); ld.energy = energy; ld.size = 60
    l = bpy.data.objects.new(name, ld); l.location = loc
    tc = l.constraints.new('TRACK_TO'); tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'; tc.target = target
    COL_STUDIO.objects.link(l)
world = bpy.data.worlds.get("Studio") or bpy.data.worlds.new("Studio"); scene.world = world
world.color = (0.03, 0.035, 0.05)

# ================================================================ save + export
os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.join(ROOT, "art"), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(ROOT, "art", "pipo3d.blend"))

def export(path, cols):
    for o in scene.objects: o.select_set(False)
    for c in cols:
        for o in c.all_objects:
            o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=path, export_format='GLB', use_selection=True, export_apply=True,
                              export_yup=True, export_cameras=False, export_lights=False,
                              export_extras=False, export_animations=False)

export(os.path.join(OUT, "pipo.glb"), [COL_PIPO, COL_ARMOR])
export(os.path.join(OUT, "lab.glb"), [COL_LAB])
result = {
    "objects": len(bpy.data.objects),
    "pipo.glb": os.path.getsize(os.path.join(OUT, "pipo.glb")),
    "lab.glb": os.path.getsize(os.path.join(OUT, "lab.glb")),
}
