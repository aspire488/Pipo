using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    /** Something in a location the user can touch. What it does is decided by the host, not here. */
    public class InteractionTarget : MonoBehaviour
    {
        public string Id;        // "npc:grumble", "item:screws", "door:home", "object:bed", "critter:deer"
        public string Label;
    }

    /** How a location is lit and how its air looks. */
    public class LightLook
    {
        public Color Sky = new Color(0.62f, 0.74f, 0.86f), Equator = new Color(0.55f, 0.6f, 0.62f), Ground = new Color(0.32f, 0.3f, 0.26f);
        public Color Sun = new Color(1f, 0.95f, 0.86f); public float SunIntensity = 1.1f; public Vector3 SunEuler = new Vector3(50, -30, 0);
        public Color Background = new Color(0.62f, 0.76f, 0.9f); public Color Fog = new Color(0.7f, 0.8f, 0.88f); public float FogStart = 25, FogEnd = 90;
        public bool Interior;
    }

    public class LocationInstance
    {
        public GameObject Root;
        public Vector3 Spawn;
        public float MinX = -3, MaxX = 3, LaneZ = 0.3f;
        public LightLook Light = new LightLook();
        public readonly List<InteractionTarget> Interactions = new List<InteractionTarget>();
        public string Archetype;
        public bool ShowsArmorDock;
    }

    /*
     * Builds every location procedurally from catalog data: one builder per environment type, with
     * per-place identity (colours, props, stock, people, animals) taken from world.json. Units are
     * metres; Pipo is 1 m tall. Built on entry, destroyed on exit, so only one place is in memory.
     */
    public static class LocationKit
    {
        const float U = 0.025f;   // app room units -> metres (Pipo is 40 u in the app, 1 m here)
        static readonly Dictionary<int, Material> mats = new Dictionary<int, Material>();
        static Font font;

        public static string ArchetypeFor(LocationSpec s) => s.Id switch
        {
            "home" => "home",
            "field" or "cricket" or "court" => "sportsground",
            "lake" => "lake",
            "hills" => "hills",
            "forest" => "forest",
            "garden" or "park" => "greenspace",
            "library" => "library",
            "sports_hall" => "hall",
            "observatory" => "observatory",
            "otherside" => "otherside",
            _ => s.Kind == "SHOP" ? "shop" : s.Kind == "OUTDOOR" ? "greenspace" : s.Kind == "INDOOR" ? "library" : null,
        };

        public static LocationInstance Build(LocationSpec s, WorldDef world)
        {
            var li = new LocationInstance { Root = new GameObject("Location:" + s.Id), Archetype = ArchetypeFor(s) };
            switch (li.Archetype)
            {
                case "home": Home(li, world); break;
                case "shop": Shop(li, s); break;
                case "library": Library(li, s); break;
                case "hall": Hall(li, s); break;
                case "sportsground": SportsGround(li, s); break;
                case "lake": Outdoor(li, s, lake: true); break;
                case "hills": Outdoor(li, s, hills: true); break;
                case "forest": Outdoor(li, s, forest: true); break;
                case "observatory": Observatory(li, s); break;
                case "otherside": Otherside(li, s); break;
                default: Outdoor(li, s); break;
            }
            if (!s.IsHome) Sign(li, s.Name, new Vector3(li.MinX + 0.6f, 0, li.LaneZ + 1.2f));
            li.Root.GetComponentsInChildren(true, li.Interactions);
            return li;
        }

        /* ------------------------------------------------------------ home */
        static void Home(LocationInstance li, WorldDef w)
        {
            var r = li.Root.transform;
            float W = w.home.width * U, D = 3.7f, H = 2.6f;
            Box(r, "Floor", new Vector3(0, -0.05f, -0.2f), new Vector3(W + 0.4f, 0.1f, D), new Color(0.6f, 0.46f, 0.36f));
            for (float x = -W / 2; x < W / 2; x += 0.28f) Box(r, "Board", new Vector3(x, 0.002f, -0.2f), new Vector3(0.012f, 0.004f, D), new Color(0.45f, 0.33f, 0.26f));
            Box(r, "BackWall", new Vector3(0, H / 2, -1.6f), new Vector3(W + 0.4f, H, 0.1f), new Color(0.58f, 0.68f, 0.72f));
            Box(r, "SideWallL", new Vector3(-W / 2 - 0.2f, H / 2, -0.2f), new Vector3(0.1f, H, D), new Color(0.54f, 0.64f, 0.68f));
            Box(r, "SideWallR", new Vector3(W / 2 + 0.2f, H / 2, -0.2f), new Vector3(0.1f, H, D), new Color(0.54f, 0.64f, 0.68f));
            Box(r, "Skirting", new Vector3(0, 0.05f, -1.54f), new Vector3(W + 0.4f, 0.1f, 0.03f), new Color(0.93f, 0.9f, 0.84f));
            // the app's own room objects, at their real positions and heights
            foreach (var o in w.home.objects)
            {
                float x0 = (o.l - 148) * U, x1 = (o.r - 148) * U, cx = (x0 + x1) / 2, wdt = Mathf.Max(0.15f, x1 - x0);
                float y0 = Mathf.Max(0, o.bottom * U), y1 = o.top * U, hgt = Mathf.Max(0.08f, y1 - y0);
                bool onWall = o.bottom > 30;
                var go = onWall
                    ? Box(r, o.id, new Vector3(cx, y0 + hgt / 2, -1.53f), new Vector3(wdt, hgt, 0.05f), WallColor(o.id), Emit(o.id))
                    : HomeProp(r, o.id, cx, wdt, hgt);
                Touchable(go, "object:" + o.id, Pretty(o.id));
            }
            var lamp = new GameObject("Lamp").AddComponent<Light>();
            lamp.transform.SetParent(r); lamp.transform.localPosition = new Vector3(0.1f, 1.6f, -0.6f);
            lamp.type = LightType.Point; lamp.range = 6; lamp.intensity = 1.1f; lamp.color = new Color(1f, 0.82f, 0.6f); lamp.shadows = LightShadows.None;
            li.MinX = -W / 2 + 0.4f; li.MaxX = W / 2 - 0.4f; li.LaneZ = 0.35f;
            li.Spawn = new Vector3(0, 0, li.LaneZ);
            li.ShowsArmorDock = true;
            li.Light = new LightLook { Interior = true, Sky = new Color(0.78f, 0.78f, 0.8f), Equator = new Color(0.62f, 0.58f, 0.54f), Ground = new Color(0.38f, 0.3f, 0.26f), SunIntensity = 0.75f, SunEuler = new Vector3(40, 30, 0), Background = new Color(0.12f, 0.15f, 0.22f), FogStart = 40, FogEnd = 120 };
        }

        static GameObject HomeProp(Transform r, string id, float cx, float w, float h)
        {
            var g = new GameObject(id); g.transform.SetParent(r); g.transform.localPosition = new Vector3(cx, 0, -1.15f);
            var t = g.transform;
            switch (id)
            {
                case "bed":
                    Box(t, "Frame", new Vector3(0, 0.18f, 0), new Vector3(w, 0.36f, 0.8f), new Color(0.55f, 0.4f, 0.3f));
                    Box(t, "Mattress", new Vector3(0, 0.42f, 0.02f), new Vector3(w - 0.06f, 0.14f, 0.74f), new Color(0.9f, 0.9f, 0.95f));
                    Box(t, "Blanket", new Vector3(0.15f, 0.5f, 0.04f), new Vector3(w * 0.7f, 0.06f, 0.76f), new Color(0.42f, 0.55f, 0.82f));
                    Box(t, "Pillow", new Vector3(-w / 2 + 0.22f, 0.53f, 0.02f), new Vector3(0.3f, 0.09f, 0.4f), Color.white);
                    break;
                case "plant":
                    Cyl(t, "Pot", new Vector3(0, 0.15f, 0), 0.13f, 0.3f, new Color(0.78f, 0.45f, 0.34f));
                    for (int i = 0; i < 5; i++) Sphere(t, "Leaf", new Vector3(Mathf.Sin(i * 1.3f) * 0.1f, 0.42f + i * 0.06f, Mathf.Cos(i * 1.3f) * 0.08f), 0.16f, new Color(0.3f, 0.6f, 0.36f));
                    break;
                case "desk":
                    Box(t, "Top", new Vector3(0, 0.72f, 0), new Vector3(w, 0.05f, 0.6f), new Color(0.5f, 0.36f, 0.27f));
                    Box(t, "LegL", new Vector3(-w / 2 + 0.05f, 0.36f, 0), new Vector3(0.05f, 0.72f, 0.55f), new Color(0.4f, 0.29f, 0.22f));
                    Box(t, "LegR", new Vector3(w / 2 - 0.05f, 0.36f, 0), new Vector3(0.05f, 0.72f, 0.55f), new Color(0.4f, 0.29f, 0.22f));
                    Box(t, "Monitor", new Vector3(0, 0.95f, -0.1f), new Vector3(0.45f, 0.3f, 0.03f), new Color(0.12f, 0.14f, 0.18f), new Color(0.2f, 0.45f, 0.5f));
                    break;
                case "workbench": case "shelf":
                    Box(t, "Bench", new Vector3(0, 0.45f, 0), new Vector3(w, 0.08f, 0.6f), new Color(0.48f, 0.37f, 0.29f));
                    Box(t, "Legs", new Vector3(0, 0.22f, 0), new Vector3(w - 0.1f, 0.44f, 0.5f), new Color(0.36f, 0.27f, 0.21f));
                    for (int i = 0; i < 5; i++) Box(t, "Jar", new Vector3(-w / 2 + 0.15f + i * w / 5, 0.56f, -0.1f), new Vector3(0.09f, 0.14f, 0.09f), Color.HSVToRGB(i / 5f, 0.5f, 0.75f));
                    break;
                case "arcade":
                    Box(t, "Cabinet", new Vector3(0, h / 2, 0), new Vector3(w, h, 0.45f), new Color(0.29f, 0.27f, 0.44f));
                    Box(t, "Screen", new Vector3(0, h * 0.7f, 0.23f), new Vector3(w * 0.7f, h * 0.25f, 0.02f), Color.black, new Color(0.3f, 0.8f, 0.7f));
                    break;
                case "kitchen":
                    Box(t, "Counter", new Vector3(0, 0.45f, 0), new Vector3(w, 0.9f, 0.6f), new Color(0.85f, 0.85f, 0.82f));
                    Box(t, "Fridge", new Vector3(-w / 2 + 0.2f, 0.85f, 0), new Vector3(0.4f, 1.7f, 0.6f), new Color(0.9f, 0.93f, 0.95f));
                    break;
                case "door":
                    Box(t, "Door", new Vector3(0, 1f, -0.42f), new Vector3(w, 2f, 0.06f), new Color(0.62f, 0.46f, 0.34f));
                    Sphere(t, "Knob", new Vector3(w / 2 - 0.08f, 1f, -0.38f), 0.05f, new Color(0.85f, 0.7f, 0.35f));
                    break;
                case "charger":
                    Cyl(t, "Pad", new Vector3(0, 0.02f, 0.3f), 0.22f, 0.04f, new Color(0.17f, 0.2f, 0.24f));
                    Box(t, "Totem", new Vector3(w / 2 - 0.08f, 0.26f, 0), new Vector3(0.1f, 0.52f, 0.1f), new Color(0.17f, 0.2f, 0.27f), new Color(0.3f, 0.85f, 0.9f));
                    break;
                case "toys":
                    Box(t, "ToyBox", new Vector3(0, 0.14f, 0), new Vector3(Mathf.Min(w, 0.4f), 0.28f, 0.32f), new Color(0.84f, 0.68f, 0.42f));
                    Sphere(t, "Ball", new Vector3(0.25f, 0.1f, 0.3f), 0.2f, new Color(0.85f, 0.3f, 0.3f));
                    break;
                case "console":
                    Box(t, "TVStand", new Vector3(0, 0.2f, 0), new Vector3(0.6f, 0.4f, 0.35f), new Color(0.35f, 0.27f, 0.22f));
                    Box(t, "TV", new Vector3(0, 0.62f, 0), new Vector3(0.55f, 0.35f, 0.05f), new Color(0.08f, 0.09f, 0.12f), new Color(0.25f, 0.4f, 0.65f));
                    break;
                default:
                    Box(t, "Prop", new Vector3(0, h / 2, 0), new Vector3(w, h, 0.4f), new Color(0.6f, 0.55f, 0.5f));
                    break;
            }
            return g;
        }

        static Color WallColor(string id) => id switch
        {
            "window" => new Color(0.55f, 0.75f, 0.92f), "corkboard" => new Color(0.6f, 0.45f, 0.3f), "clock" => new Color(0.95f, 0.95f, 0.92f),
            "drawings" => new Color(0.96f, 0.93f, 0.86f), _ => new Color(0.8f, 0.78f, 0.74f),
        };
        static Color? Emit(string id) => id == "window" ? new Color(0.35f, 0.5f, 0.6f) : (Color?)null;

        /* ------------------------------------------------------------ shops */
        static void Shop(LocationInstance li, LocationSpec s)
        {
            var r = li.Root.transform;
            var wall = Color.Lerp(s.Color, Color.white, 0.45f);
            Box(r, "Floor", new Vector3(0, -0.05f, -0.5f), new Vector3(8, 0.1f, 5), Color.Lerp(s.Color, new Color(0.35f, 0.28f, 0.22f), 0.6f));
            Box(r, "BackWall", new Vector3(0, 1.4f, -2.6f), new Vector3(8, 2.8f, 0.1f), wall);
            Box(r, "WallL", new Vector3(-4, 1.4f, -0.5f), new Vector3(0.1f, 2.8f, 5), Color.Lerp(wall, Color.black, 0.08f));
            Box(r, "WallR", new Vector3(4, 1.4f, -0.5f), new Vector3(0.1f, 2.8f, 5), Color.Lerp(wall, Color.black, 0.08f));
            Box(r, "Awning", new Vector3(0, 2.5f, -2.45f), new Vector3(7.6f, 0.3f, 0.3f), s.Color);
            Label(r, s.Name, new Vector3(0, 2.15f, -2.5f), 0.16f, new Color(0.15f, 0.15f, 0.2f));
            // shelves with what this shop really sells (the catalog's list)
            var stock = s.Sells.Length > 0 ? s.Sells : new[] { "parts", "tools", "odds" };
            for (int row = 0; row < 3; row++) Box(r, "Shelf", new Vector3(-1.6f, 0.5f + row * 0.6f, -2.35f), new Vector3(3.6f, 0.05f, 0.4f), new Color(0.5f, 0.38f, 0.28f));
            for (int i = 0; i < stock.Length; i++)
            {
                var pos = new Vector3(-3.1f + (i % 5) * 0.72f, 0.66f + (i / 5) * 0.6f, -2.32f);
                var item = Good(r, stock[i], pos);
                Touchable(item, "item:" + stock[i], Pretty(stock[i]));
            }
            // counter + the shopkeeper
            Box(r, "Counter", new Vector3(1.9f, 0.5f, -1.2f), new Vector3(2.6f, 1f, 0.7f), Color.Lerp(s.Color, new Color(0.3f, 0.22f, 0.18f), 0.5f));
            switch (s.Id)
            {
                case "bakery": for (int i = 0; i < 4; i++) Sphere(r, "Loaf", new Vector3(1.0f + i * 0.5f, 1.08f, -1.2f), 0.22f, new Color(0.82f, 0.58f, 0.3f)); break;
                case "cafe": for (int i = 0; i < 3; i++) { Cyl(r, "Table", new Vector3(-2.5f + i * 1.6f, 0.38f, 0.4f), 0.35f, 0.04f, new Color(0.45f, 0.32f, 0.24f)); Cyl(r, "Stem", new Vector3(-2.5f + i * 1.6f, 0.19f, 0.4f), 0.04f, 0.38f, new Color(0.3f, 0.3f, 0.32f)); } break;
                case "electronics": for (int i = 0; i < 4; i++) Box(r, "Screen", new Vector3(-2.8f + i * 0.9f, 1.95f, -2.5f), new Vector3(0.7f, 0.4f, 0.04f), Color.black, Color.HSVToRGB(0.5f + i * 0.08f, 0.6f, 0.7f)); break;
                case "hardware": for (int i = 0; i < 6; i++) Box(r, "Tool", new Vector3(0.9f + i * 0.4f, 1.9f, -2.53f), new Vector3(0.06f, 0.4f, 0.03f), new Color(0.55f, 0.58f, 0.62f)); break;
                case "market": for (int i = 0; i < 4; i++) Box(r, "Crate", new Vector3(-3 + i * 0.9f, 0.18f, -0.4f), new Vector3(0.7f, 0.36f, 0.5f), new Color(0.6f, 0.46f, 0.3f)); break;
                case "secondhand": for (int i = 0; i < 9; i++) Box(r, "Thing", new Vector3(-3.2f + i * 0.75f, 0.12f + (i % 3) * 0.08f, -0.2f + (i % 2) * 0.3f), Vector3.one * (0.15f + (i % 4) * 0.06f), Color.HSVToRGB((i * 0.13f) % 1, 0.35f, 0.7f)); break;
                case "repair": for (int i = 0; i < 3; i++) Cyl(r, "Gear", new Vector3(1.2f + i * 0.5f, 1.12f, -1.2f), 0.16f, 0.05f, new Color(0.65f, 0.65f, 0.6f)); break;
            }
            Shopkeeper(r, s.Npc, new Vector3(2.3f, 0, -1.9f));
            var lamp = new GameObject("ShopLight").AddComponent<Light>();
            lamp.transform.SetParent(r); lamp.transform.localPosition = new Vector3(0, 2.4f, -0.6f);
            lamp.type = LightType.Point; lamp.range = 8; lamp.intensity = 1.2f; lamp.color = new Color(1f, 0.9f, 0.75f);
            li.MinX = -3.2f; li.MaxX = 0.6f; li.LaneZ = 0.2f; li.Spawn = new Vector3(-1, 0, 0.2f);
            li.Light = new LightLook { Interior = true, Sky = new Color(0.85f, 0.82f, 0.78f), Equator = new Color(0.65f, 0.6f, 0.55f), SunIntensity = 0.6f, Background = new Color(0.16f, 0.14f, 0.13f) };
        }

        static GameObject Good(Transform r, string id, Vector3 pos)
        {
            var c = Color.HSVToRGB(Mathf.Abs(id.GetHashCode() % 997) / 997f, 0.45f, 0.82f);
            var g = id.Contains("bread") || id.Contains("croissant") || id.Contains("cookie") || id.Contains("apple") || id.Contains("tomato") || id.Contains("egg") || id.Contains("marble")
                ? Sphere(r, id, pos + new Vector3(0, 0.06f, 0), 0.16f, c)
                : Box(r, id, pos + new Vector3(0, 0.08f, 0), new Vector3(0.22f, 0.16f, 0.16f), c);
            Label(g.transform, Pretty(id), new Vector3(0, 0.16f, 0.1f), 0.035f, new Color(0.1f, 0.1f, 0.12f), world: false);
            return g;
        }

        /* ------------------------------------------------------------ indoor */
        static void Library(LocationInstance li, LocationSpec s)
        {
            var r = li.Root.transform;
            Box(r, "Floor", new Vector3(0, -0.05f, -1), new Vector3(10, 0.1f, 6), new Color(0.42f, 0.32f, 0.26f));
            Box(r, "BackWall", new Vector3(0, 1.6f, -3.6f), new Vector3(10, 3.2f, 0.1f), new Color(0.75f, 0.7f, 0.6f));
            for (int k = 0; k < 4; k++)
            {
                float x = -4 + k * 2.4f;
                Box(r, "Bookcase", new Vector3(x, 1.1f, -3.2f), new Vector3(1.9f, 2.2f, 0.4f), new Color(0.38f, 0.26f, 0.18f));
                for (int row = 0; row < 4; row++) for (int b = 0; b < 9; b++)
                    Box(r, "Book", new Vector3(x - 0.8f + b * 0.2f, 0.35f + row * 0.5f, -3.0f), new Vector3(0.15f, 0.36f, 0.05f), Color.HSVToRGB(((b * 7 + row * 3 + k) % 13) / 13f, 0.5f, 0.6f));
            }
            Box(r, "ReadingTable", new Vector3(1.5f, 0.7f, -0.8f), new Vector3(2, 0.06f, 0.9f), new Color(0.5f, 0.36f, 0.25f));
            Shopkeeper(r, s.Npc, new Vector3(3.3f, 0, -1.6f));
            li.MinX = -4; li.MaxX = 3; li.LaneZ = 0.3f; li.Spawn = new Vector3(-1.5f, 0, 0.3f);
            li.Light = new LightLook { Interior = true, Sky = new Color(0.78f, 0.8f, 0.86f), Equator = new Color(0.6f, 0.58f, 0.55f), SunIntensity = 0.7f, Background = new Color(0.14f, 0.13f, 0.12f) };
        }

        static void Hall(LocationInstance li, LocationSpec s)
        {
            var r = li.Root.transform;
            Box(r, "Floor", new Vector3(0, -0.05f, -1), new Vector3(14, 0.1f, 8), new Color(0.78f, 0.58f, 0.36f));
            Box(r, "BackWall", new Vector3(0, 2.5f, -4.6f), new Vector3(14, 5, 0.1f), new Color(0.82f, 0.84f, 0.86f));
            for (int i = 0; i < 2; i++)
            {
                float x = -3 + i * 5;
                Box(r, "TTTable", new Vector3(x, 0.76f, -2), new Vector3(2.7f, 0.05f, 1.5f), new Color(0.12f, 0.35f, 0.3f));
                Box(r, "Net", new Vector3(x, 0.85f, -2), new Vector3(0.02f, 0.15f, 1.6f), new Color(0.9f, 0.9f, 0.9f));
                Box(r, "Base", new Vector3(x, 0.38f, -2), new Vector3(2.2f, 0.76f, 0.2f), new Color(0.2f, 0.2f, 0.22f));
            }
            Shopkeeper(r, s.Npc, new Vector3(4.5f, 0, -1.2f));
            li.MinX = -5; li.MaxX = 4; li.LaneZ = 0.4f; li.Spawn = new Vector3(-2, 0, 0.4f);
            li.Light = new LightLook { Interior = true, Sky = Color.white, Equator = new Color(0.75f, 0.75f, 0.75f), SunIntensity = 1f, Background = new Color(0.2f, 0.2f, 0.22f) };
        }

        /* ------------------------------------------------------------ outdoors */
        static void Outdoor(LocationInstance li, LocationSpec s, bool lake = false, bool hills = false, bool forest = false)
        {
            var r = li.Root.transform;
            var grass = Color.Lerp(s.Color, new Color(0.3f, 0.5f, 0.28f), 0.35f);
            Box(r, "Ground", new Vector3(0, -0.05f, -6), new Vector3(60, 0.1f, 40), grass);
            if (hills)
                for (int i = 0; i < 6; i++) Sphere(r, "Hill", new Vector3(-20 + i * 8, -6 + (i % 2) * 1.5f, -18 - (i % 3) * 4), 18 + (i % 3) * 4, Color.Lerp(grass, Color.white, 0.08f * (i % 3)));
            if (lake)
            {
                var water = Box(r, "Water", new Vector3(1.5f, 0.01f, -5), new Vector3(14, 0.02f, 7), new Color(0.32f, 0.55f, 0.72f));
                water.GetComponent<Renderer>().sharedMaterial = Mat(new Color(0.32f, 0.55f, 0.72f), 0.1f, 0.9f);
                for (int i = 0; i < 5; i++) Cyl(r, "LilyPad", new Vector3(-2 + i * 1.6f, 0.03f, -3.2f - (i % 2) * 1.2f), 0.3f, 0.01f, new Color(0.35f, 0.6f, 0.35f));
                for (int i = 0; i < 8; i++) Cyl(r, "Reed", new Vector3(-5.5f + (i % 4) * 0.2f, 0.4f, -2 - (i / 4) * 0.4f), 0.02f, 0.8f, new Color(0.4f, 0.5f, 0.3f));
            }
            if (forest)
            {
                for (int i = 0; i < 40; i++)
                {
                    float a = i * 2.399f, d = 7 + (i % 7) * 2.2f;
                    var p = new Vector3(Mathf.Cos(a) * d, 0, -6 + Mathf.Sin(a) * d * 0.8f);
                    if (p.z > 1.5f) continue;   // keep the camera side open
                    Pine(r, p, 0.9f + (i % 4) * 0.25f);
                }
            }
            else if (!lake)
            {
                for (int i = 0; i < 8; i++) Tree(r, new Vector3(-9 + i * 2.6f, 0, -6.5f - (i % 3) * 1.5f), 0.9f + (i % 3) * 0.2f);
            }
            if (s.Id == "park") { Bench(r, new Vector3(2.5f, 0, -1.5f)); Box(r, "Slide", new Vector3(-3, 0.7f, -3), new Vector3(0.6f, 1.4f, 2), new Color(0.9f, 0.4f, 0.35f)); }
            if (s.Id == "garden") for (int i = 0; i < 12; i++) Sphere(r, "Flower", new Vector3(-3 + i * 0.5f, 0.12f, -1.8f + (i % 2) * 0.3f), 0.14f, Color.HSVToRGB((i * 0.17f) % 1, 0.55f, 0.95f));
            if (s.Id == "garden") for (int i = 0; i < 14; i++) Box(r, "Fence", new Vector3(-7 + i, 0.4f, -3.2f), new Vector3(0.08f, 0.8f, 0.08f), new Color(0.92f, 0.9f, 0.84f));
            Critters(r, s);
            Shopkeeper(r, s.Npc, new Vector3(3.2f, 0, -0.8f));
            li.MinX = -4; li.MaxX = 4; li.LaneZ = 0.4f; li.Spawn = new Vector3(0, 0, 0.4f);
            li.Light = forest
                ? new LightLook { Sky = new Color(0.6f, 0.74f, 0.82f), Equator = new Color(0.4f, 0.5f, 0.42f), Ground = new Color(0.2f, 0.26f, 0.18f), Fog = new Color(0.62f, 0.72f, 0.74f), FogStart = 10, FogEnd = 40, Background = new Color(0.62f, 0.72f, 0.74f) }
                : new LightLook();
        }

        static void SportsGround(LocationInstance li, LocationSpec s)
        {
            Outdoor(li, s);
            var r = li.Root.transform;
            var white = new Color(0.95f, 0.95f, 0.95f);
            switch (s.Id)
            {
                case "field":
                    Box(r, "Halfway", new Vector3(0, 0.005f, -4), new Vector3(0.08f, 0.01f, 10), white);
                    foreach (var x in new[] { -7f, 7f })
                    {
                        Box(r, "PostA", new Vector3(x, 0.6f, -5.2f), new Vector3(0.08f, 1.2f, 0.08f), white);
                        Box(r, "PostB", new Vector3(x, 0.6f, -2.8f), new Vector3(0.08f, 1.2f, 0.08f), white);
                        Box(r, "Bar", new Vector3(x, 1.2f, -4f), new Vector3(0.08f, 0.08f, 2.48f), white);
                    }
                    Sphere(r, "Ball", new Vector3(1.2f, 0.11f, -0.6f), 0.22f, white);
                    break;
                case "cricket":
                    Box(r, "Pitch", new Vector3(0, 0.005f, -3.5f), new Vector3(1.2f, 0.01f, 8), new Color(0.78f, 0.7f, 0.5f));
                    foreach (var z in new[] { -7.3f, 0.3f }) for (int i = -1; i <= 1; i++) Box(r, "Stump", new Vector3(i * 0.09f, 0.36f, z), new Vector3(0.04f, 0.72f, 0.04f), new Color(0.9f, 0.85f, 0.7f));
                    break;
                case "court":
                    Box(r, "Court", new Vector3(0, 0.005f, -3), new Vector3(6, 0.01f, 3), new Color(0.3f, 0.55f, 0.4f));
                    Box(r, "Net", new Vector3(0, 0.78f, -3), new Vector3(0.03f, 0.75f, 3.2f), new Color(0.95f, 0.95f, 0.95f));
                    break;
            }
        }

        static void Observatory(LocationInstance li, LocationSpec s)
        {
            var r = li.Root.transform;
            Box(r, "Ground", new Vector3(0, -0.05f, -6), new Vector3(60, 0.1f, 40), new Color(0.3f, 0.34f, 0.3f));
            Sphere(r, "Dome", new Vector3(0, 0.5f, -6), 6, s.Color);
            Box(r, "Slot", new Vector3(0, 3.2f, -3.3f), new Vector3(0.8f, 2.5f, 0.6f), new Color(0.08f, 0.08f, 0.14f));
            Cyl(r, "Telescope", new Vector3(2.5f, 0.9f, -1), 0.15f, 1.6f, new Color(0.7f, 0.7f, 0.75f)).transform.localRotation = Quaternion.Euler(0, 0, 50);
            Stars(r);
            li.MinX = -3; li.MaxX = 3; li.LaneZ = 0.4f; li.Spawn = new Vector3(-1, 0, 0.4f);
            li.Light = new LightLook { Sky = new Color(0.22f, 0.24f, 0.4f), Equator = new Color(0.18f, 0.2f, 0.3f), Ground = new Color(0.08f, 0.08f, 0.1f), Sun = new Color(0.7f, 0.75f, 1f), SunIntensity = 0.45f, Background = new Color(0.04f, 0.05f, 0.12f), Fog = new Color(0.05f, 0.06f, 0.14f), FogStart = 30, FogEnd = 90 };
        }

        static void Otherside(LocationInstance li, LocationSpec s)
        {
            // "The same street. Not the same street." Mirrored houses under a wrong-coloured sky.
            var r = li.Root.transform;
            Box(r, "Street", new Vector3(0, -0.05f, -3), new Vector3(60, 0.1f, 30), new Color(0.22f, 0.3f, 0.32f));
            for (int i = 0; i < 6; i++)
            {
                float x = -10 + i * 4;
                Box(r, "House", new Vector3(x, 1.4f, -7), new Vector3(3, 2.8f, 2.5f), Color.HSVToRGB(0.45f + i * 0.02f, 0.25f, 0.55f));
                Box(r, "Window", new Vector3(x - 0.6f, 1.6f, -5.73f), new Vector3(0.6f, 0.6f, 0.04f), Color.black, new Color(0.4f, 1f, 0.85f));
            }
            li.MinX = -4; li.MaxX = 4; li.LaneZ = 0.4f; li.Spawn = new Vector3(0, 0, 0.4f);
            li.Light = new LightLook { Sky = new Color(0.55f, 0.95f, 0.88f), Equator = new Color(0.3f, 0.5f, 0.5f), Ground = new Color(0.1f, 0.2f, 0.2f), Sun = new Color(0.6f, 1f, 0.9f), SunIntensity = 0.6f, SunEuler = new Vector3(30, 150, 0), Background = new Color(0.36f, 0.66f, 0.62f), Fog = new Color(0.36f, 0.66f, 0.62f), FogStart = 8, FogEnd = 30 };
        }

        /* ------------------------------------------------------------ people + animals */
        static void Shopkeeper(Transform r, string npcId, Vector3 pos)
        {
            var npc = WorldData.Npc(npcId);
            if (npc == null) return;
            var g = new GameObject("npc:" + npc.id); g.transform.SetParent(r); g.transform.localPosition = pos;
            var tint = Color.HSVToRGB(Mathf.Abs(npc.id.GetHashCode() % 360) / 360f, 0.35f, 0.75f);
            Cyl(g.transform, "Body", new Vector3(0, 0.45f, 0), 0.22f, 0.7f, tint);
            Box(g.transform, "Head", new Vector3(0, 1.0f, 0), new Vector3(0.5f, 0.38f, 0.38f), Color.Lerp(tint, Color.white, 0.5f));
            Box(g.transform, "Screen", new Vector3(0, 1.0f, 0.2f), new Vector3(0.38f, 0.24f, 0.02f), new Color(0.1f, 0.13f, 0.2f), new Color(0.2f, 0.5f, 0.45f));
            Label(g.transform, npc.name, new Vector3(0, 1.38f, 0), 0.05f, Color.white, world: false);
            Touchable(g, "npc:" + npc.id, npc.name);
        }

        static void Critters(Transform r, LocationSpec s)
        {
            int i = 0;
            foreach (var sp in s.Wildlife)
            {
                var pos = new Vector3(-3 + (i * 1.7f) % 7, 0, -1.6f - (i % 3) * 0.9f);
                GameObject g;
                switch (sp)
                {
                    case "deer":
                        g = new GameObject("critter:deer"); g.transform.SetParent(r); g.transform.localPosition = pos + new Vector3(0, 0, -2);
                        Box(g.transform, "Body", new Vector3(0, 0.75f, 0), new Vector3(0.9f, 0.4f, 0.32f), new Color(0.69f, 0.48f, 0.29f));
                        Box(g.transform, "Neck", new Vector3(0.45f, 1.05f, 0), new Vector3(0.14f, 0.45f, 0.14f), new Color(0.69f, 0.48f, 0.29f));
                        Box(g.transform, "Head", new Vector3(0.55f, 1.3f, 0), new Vector3(0.3f, 0.18f, 0.16f), new Color(0.69f, 0.48f, 0.29f));
                        foreach (var lx in new[] { -0.35f, 0.35f }) foreach (var lz in new[] { -0.1f, 0.1f }) Box(g.transform, "Leg", new Vector3(lx, 0.28f, lz), new Vector3(0.06f, 0.56f, 0.06f), new Color(0.42f, 0.29f, 0.19f));
                        break;
                    case "rabbit": case "squirrel": case "cat": case "fox":
                        var fur = sp == "rabbit" ? new Color(0.85f, 0.81f, 0.76f) : sp == "fox" ? new Color(0.85f, 0.45f, 0.23f) : sp == "cat" ? new Color(0.55f, 0.56f, 0.6f) : new Color(0.72f, 0.5f, 0.31f);
                        g = new GameObject("critter:" + sp); g.transform.SetParent(r); g.transform.localPosition = pos;
                        Sphere(g.transform, "Body", new Vector3(0, 0.12f, 0), 0.24f, fur);
                        Sphere(g.transform, "Head", new Vector3(0.14f, 0.24f, 0), 0.15f, fur);
                        break;
                    case "frog":
                        g = Sphere(r, "critter:frog", pos + new Vector3(0, 0.06f, 0), 0.14f, new Color(0.42f, 0.7f, 0.38f));
                        break;
                    case "duck":
                        g = Sphere(r, "critter:duck", pos + new Vector3(2.5f, 0.1f, -2.5f), 0.26f, new Color(0.93f, 0.9f, 0.82f));
                        break;
                    case "firefly": case "moth":
                        g = Sphere(r, "critter:" + sp, pos + new Vector3(0, 1.2f, 0), 0.06f, new Color(1f, 0.95f, 0.6f), new Color(1f, 0.9f, 0.4f));
                        break;
                    default: // birds, butterflies, bees: a small bright flier
                        var col = sp == "robin" ? new Color(0.88f, 0.48f, 0.29f) : sp == "butterfly" || sp == "bee" ? new Color(0.95f, 0.76f, 0.35f) : new Color(0.42f, 0.33f, 0.27f);
                        g = new GameObject("critter:" + sp); g.transform.SetParent(r); g.transform.localPosition = pos + new Vector3(0, 1.6f + (i % 2) * 0.5f, 0);
                        Sphere(g.transform, "Body", Vector3.zero, 0.12f, col);
                        g.AddComponent<Flier>();
                        break;
                }
                Touchable(g, "critter:" + sp, Pretty(sp));
                i++;
            }
        }

        public class Flier : MonoBehaviour
        {
            Vector3 home; float ph;
            void Start() { home = transform.localPosition; ph = Random.value * 6; }
            void Update() { float t = Time.time + ph; transform.localPosition = home + new Vector3(Mathf.Sin(t * 0.7f) * 1.2f, Mathf.Sin(t * 2.3f) * 0.15f, Mathf.Cos(t * 0.7f) * 0.6f); }
        }

        /* ------------------------------------------------------------ props */
        static void Tree(Transform r, Vector3 p, float s)
        {
            Cyl(r, "Trunk", p + new Vector3(0, 0.6f * s, 0), 0.1f * s, 1.2f * s, new Color(0.42f, 0.3f, 0.22f));
            Sphere(r, "Canopy", p + new Vector3(0, 1.5f * s, 0), 1.2f * s, new Color(0.34f, 0.58f, 0.34f));
            Sphere(r, "Canopy", p + new Vector3(0.35f * s, 1.25f * s, 0.2f), 0.8f * s, new Color(0.38f, 0.63f, 0.36f));
        }

        static void Pine(Transform r, Vector3 p, float s)
        {
            Cyl(r, "Trunk", p + new Vector3(0, 0.3f * s, 0), 0.08f * s, 0.6f * s, new Color(0.35f, 0.25f, 0.18f));
            for (int k = 0; k < 3; k++) Cone(r, "Needles", p + new Vector3(0, (0.7f + k * 0.5f) * s, 0), (0.75f - k * 0.18f) * s, 0.9f * s, new Color(0.24f + k * 0.03f, 0.47f + k * 0.03f, 0.3f));
        }

        static void Bench(Transform r, Vector3 p)
        {
            Box(r, "Seat", p + new Vector3(0, 0.42f, 0), new Vector3(1.4f, 0.06f, 0.4f), new Color(0.55f, 0.4f, 0.28f));
            Box(r, "Back", p + new Vector3(0, 0.7f, -0.18f), new Vector3(1.4f, 0.4f, 0.05f), new Color(0.55f, 0.4f, 0.28f));
            Box(r, "Legs", p + new Vector3(0, 0.2f, 0), new Vector3(1.2f, 0.4f, 0.3f), new Color(0.25f, 0.25f, 0.27f));
        }

        static void Stars(Transform r)
        {
            for (int i = 0; i < 60; i++)
            {
                float a = i * 2.399f, e = 0.25f + (i % 9) * 0.07f;
                Sphere(r, "Star", new Vector3(Mathf.Cos(a) * 40, 6 + e * 30, -20 + Mathf.Sin(a) * 10), 0.12f, Color.white, Color.white);
            }
        }

        static void Sign(LocationInstance li, string name, Vector3 p)
        {
            var r = li.Root.transform;
            Cyl(r, "SignPost", p + new Vector3(0, 0.55f, 0), 0.03f, 1.1f, new Color(0.45f, 0.33f, 0.24f));
            Box(r, "SignBoard", p + new Vector3(0, 1.1f, 0), new Vector3(0.9f, 0.28f, 0.04f), new Color(0.92f, 0.88f, 0.78f));
            Label(r, name, p + new Vector3(0, 1.1f, 0.03f), 0.035f, new Color(0.18f, 0.16f, 0.14f));
        }

        /* ------------------------------------------------------------ primitives */
        public static Material Mat(Color c, float metal = 0f, float smooth = 0.35f, Color? emit = null)
        {
            int key = (c.GetHashCode() * 31 + metal.GetHashCode()) * 31 + smooth.GetHashCode() + (emit?.GetHashCode() ?? 0);
            if (mats.TryGetValue(key, out var m) && m != null) return m;
            m = new Material(Shader.Find("Standard")) { color = c };
            m.SetFloat("_Metallic", metal); m.SetFloat("_Glossiness", smooth);
            if (emit.HasValue) { m.EnableKeyword("_EMISSION"); m.SetColor("_EmissionColor", emit.Value); }
            return mats[key] = m;
        }

        public static GameObject Box(Transform parent, string name, Vector3 pos, Vector3 size, Color c, Color? emit = null)
        {
            var g = GameObject.CreatePrimitive(PrimitiveType.Cube);
            g.name = name; g.transform.SetParent(parent, false); g.transform.localPosition = pos; g.transform.localScale = size;
            g.GetComponent<Renderer>().sharedMaterial = Mat(c, 0, 0.3f, emit);
            return g;
        }

        public static GameObject Sphere(Transform parent, string name, Vector3 pos, float diameter, Color c, Color? emit = null)
        {
            var g = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            g.name = name; g.transform.SetParent(parent, false); g.transform.localPosition = pos; g.transform.localScale = Vector3.one * diameter;
            g.GetComponent<Renderer>().sharedMaterial = Mat(c, 0, 0.3f, emit);
            return g;
        }

        public static GameObject Cyl(Transform parent, string name, Vector3 pos, float radius, float height, Color c)
        {
            var g = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            g.name = name; g.transform.SetParent(parent, false); g.transform.localPosition = pos; g.transform.localScale = new Vector3(radius * 2, height / 2, radius * 2);
            g.GetComponent<Renderer>().sharedMaterial = Mat(c);
            return g;
        }

        static Mesh coneMesh;
        public static GameObject Cone(Transform parent, string name, Vector3 pos, float radius, float height, Color c)
        {
            if (coneMesh == null)
            {
                const int n = 10;
                var v = new List<Vector3> { new Vector3(0, 0.5f, 0), new Vector3(0, -0.5f, 0) };
                var t = new List<int>();
                for (int i = 0; i < n; i++) { float a = i * Mathf.PI * 2 / n; v.Add(new Vector3(Mathf.Cos(a) * 0.5f, -0.5f, Mathf.Sin(a) * 0.5f)); }
                for (int i = 0; i < n; i++) { int a = 2 + i, b = 2 + (i + 1) % n; t.AddRange(new[] { 0, b, a, 1, a, b }); }
                coneMesh = new Mesh { name = "Cone", vertices = v.ToArray(), triangles = t.ToArray() };
                coneMesh.RecalculateNormals(); coneMesh.RecalculateBounds();
            }
            var g = new GameObject(name);
            g.transform.SetParent(parent, false); g.transform.localPosition = pos; g.transform.localScale = new Vector3(radius * 2, height, radius * 2);
            g.AddComponent<MeshFilter>().sharedMesh = coneMesh;
            g.AddComponent<MeshRenderer>().sharedMaterial = Mat(c);
            return g;
        }

        public static TextMesh Label(Transform parent, string text, Vector3 pos, float size, Color c, bool world = true)
        {
            font ??= Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            var g = new GameObject("Label");
            g.transform.SetParent(parent, false); g.transform.localPosition = pos;
            if (!world) g.transform.localScale = new Vector3(1 / Mathf.Max(0.01f, parent.lossyScale.x), 1 / Mathf.Max(0.01f, parent.lossyScale.y), 1) * 0.5f;
            var tm = g.AddComponent<TextMesh>();
            tm.text = text; tm.font = font; tm.fontSize = 64; tm.characterSize = size; tm.anchor = TextAnchor.MiddleCenter; tm.color = c;
            g.GetComponent<MeshRenderer>().sharedMaterial = font.material;
            return tm;
        }

        static void Touchable(GameObject g, string id, string label)
        {
            if (g == null) return;
            if (g.GetComponent<Collider>() == null)
            {
                var b = new Bounds(g.transform.position, Vector3.zero);
                foreach (var rr in g.GetComponentsInChildren<Renderer>()) b.Encapsulate(rr.bounds);
                var bc = g.AddComponent<BoxCollider>();
                bc.center = g.transform.InverseTransformPoint(b.center);
                var ls = g.transform.lossyScale;
                bc.size = new Vector3(b.size.x / Mathf.Max(1e-4f, ls.x), b.size.y / Mathf.Max(1e-4f, ls.y), b.size.z / Mathf.Max(1e-4f, ls.z));
            }
            var it = g.AddComponent<InteractionTarget>(); it.Id = id; it.Label = label;
        }

        public static string Pretty(string id) => string.IsNullOrEmpty(id) ? id : char.ToUpper(id[0]) + id.Substring(1).Replace('_', ' ');
    }
}
