# Blender: يصيّر مجسّم Car Concept (CC-BY 4.0) بخلفية شفافة وظل، من زاويتين وبعدة ألوان.
import bpy, sys, math, os
argv = sys.argv[sys.argv.index("--") + 1:]
glb, hdr, out = argv[0], argv[1], argv[2]
os.makedirs(out, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
objs = [o for o in bpy.context.scene.objects if o.type == 'MESH']
# حجم السيارة ومركزها
import mathutils
mn = mathutils.Vector((1e9, 1e9, 1e9)); mx = mathutils.Vector((-1e9, -1e9, -1e9))
for o in objs:
    for c in o.bound_box:
        w = o.matrix_world @ mathutils.Vector(c)
        mn = mathutils.Vector(map(min, mn, w)); mx = mathutils.Vector(map(max, mx, w))
center = (mn + mx) / 2; size = mx - mn
print("BBOX", mn, mx, "SIZE", size)
mats = sorted({s.material.name for o in objs for s in o.material_slots if s.material})
print("MATERIALS", mats)
# إخفاء أي شعار (الرخصة تستثني الشعارات)
for o in objs:
    names = [o.name.lower()] + [s.material.name.lower() for s in o.material_slots if s.material]
    if any(k in n for n in names for k in ("logo", "khronos", "decal", "badge", "emblem")):
        o.hide_render = True; print("HIDE", o.name)
# أرضية لالتقاط الظل
bpy.ops.mesh.primitive_plane_add(size=max(size.x, size.y) * 6, location=(center.x, center.y, mn.z))
plane = bpy.context.active_object; plane.is_shadow_catcher = True
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.samples = int(os.environ.get("SAMPLES", "96"))
try: scene.cycles.use_denoising = True
except Exception: pass
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'Filmic' if 'Filmic' in [i.identifier for i in scene.view_settings.bl_rna.properties['view_transform'].enum_items] else 'Standard'
world = bpy.data.worlds.new("w"); scene.world = world; world.use_nodes = True
nt = world.node_tree; env = nt.nodes.new('ShaderNodeTexEnvironment'); env.image = bpy.data.images.load(hdr)
nt.links.new(env.outputs['Color'], nt.nodes['Background'].inputs['Color']); nt.nodes['Background'].inputs['Strength'].default_value = 1.2
# المحور الطولي للسيارة = الأطول بين x و y
long_axis = 'x' if size.x >= size.y else 'y'
L = max(size.x, size.y)
cam_data = bpy.data.cameras.new("cam"); cam = bpy.data.objects.new("cam", cam_data); scene.collection.objects.link(cam); scene.camera = cam
def look(at):
    d = at - cam.location; cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
# إيجاد المؤخرة: نفترض أن مقدمة glTF نحو +Z (بعد الاستيراد نحو -Y في Blender). نصيّر الجهتين ونحفظ الاسم بالزاوية.
views = {
  # name: (azimuth deg around car from +Y, elevation deg, distance factor, res)
  "rear":   (0,   32, 1.55, (900, 760)),
  "rear34": (35,  22, 1.75, (1100, 700)),
  "front":  (180, 32, 1.55, (900, 760)),
}
paint = [bpy.data.materials[m] for m in mats if any(k in m.lower() for k in ("paint", "body", "carpaint"))]
print("PAINT", [m.name for m in paint])
colors = {"white": (0.86, 0.87, 0.88), "silver": (0.55, 0.57, 0.6), "black": (0.02, 0.02, 0.025), "red": (0.45, 0.02, 0.02),
          "blue": (0.03, 0.09, 0.32), "gray": (0.16, 0.17, 0.19)}
only = os.environ.get("COLORS", ",".join(colors)).split(",")
def set_color(rgb):
    for m in paint:
        if not m.use_nodes: continue
        for n in m.node_tree.nodes:
            if n.type == 'BSDF_PRINCIPLED':
                if n.inputs['Base Color'].is_linked:
                    for l in list(n.inputs['Base Color'].links): m.node_tree.links.remove(l)
                n.inputs['Base Color'].default_value = (rgb[0], rgb[1], rgb[2], 1)
for vname, (az, el, df, res) in views.items():
    if vname not in os.environ.get("VIEWS", "rear,rear34,front").split(","): continue
    scene.render.resolution_x, scene.render.resolution_y = res
    r = L * df
    a, e = math.radians(az), math.radians(el)
    if long_axis == 'y':
        off = mathutils.Vector((r * math.sin(a) * math.cos(e), r * math.cos(a) * math.cos(e), r * math.sin(e)))
    else:
        off = mathutils.Vector((r * math.cos(a) * math.cos(e), r * math.sin(a) * math.cos(e), r * math.sin(e)))
    for sign, tag in ((1, "p"), (-1, "n")):
        cam.location = center + mathutils.Vector((off.x * sign, off.y * sign, off.z))
        look(center + mathutils.Vector((0, 0, -size.z * 0.1)))
        cam_data.lens = 50
        for cname in only:
            set_color(colors[cname])
            scene.render.filepath = os.path.join(out, f"car_{vname}_{tag}_{cname}.png")
            bpy.ops.render.render(write_still=True)
            print("WROTE", scene.render.filepath)
        if os.environ.get("BOTH_SIDES", "1") != "1": break
