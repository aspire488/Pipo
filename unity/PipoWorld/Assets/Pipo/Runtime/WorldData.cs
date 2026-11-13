using System;
using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    // world.json is generated from the app's Kotlin catalog (WorldExportTest); never edit it by hand.
    [Serializable] public class PlaceDef { public string id, name, kind, npc, color, blurb; public int travelMin, opens, closes; public string[] sells, wildlife; public float mapX, mapY; }
    [Serializable] public class HomeObj { public string id; public float l, r, bottom, top; }
    [Serializable] public class HomeDef { public string id, name, kind; public float width; public HomeObj[] objects; }
    [Serializable] public class NpcDef { public string id, name, placeId, hello; }
    [Serializable] public class SpeciesDef { public string id, name, daytime; }
    [Serializable] public class WorldDef { public int v; public HomeDef home; public PlaceDef[] places; public NpcDef[] npcs; public SpeciesDef[] species; }

    /** One reachable location: the home room or a catalog place. */
    public class LocationSpec
    {
        public string Id, Name, Kind, Blurb, Npc;
        public Color Color;
        public string[] Sells = Array.Empty<string>(), Wildlife = Array.Empty<string>();
        public bool IsHome => Kind == "HOME";
    }

    public static class WorldData
    {
        static WorldDef cached;

        public static WorldDef Load()
        {
            if (cached != null) return cached;
            var text = Resources.Load<TextAsset>("world");
            if (text == null) throw new InvalidOperationException("Resources/world.json is missing (run the app's WorldExportTest with PIPO_EXPORT_WORLD=1)");
            cached = JsonUtility.FromJson<WorldDef>(text.text);
            if (cached == null || cached.v != 1 || cached.places == null) throw new InvalidOperationException("world.json is not a v1 world");
            return cached;
        }

        /** Every location, home first, in catalog order. Nothing is filtered out. */
        public static List<LocationSpec> Locations()
        {
            var w = Load();
            var list = new List<LocationSpec> { new LocationSpec { Id = "home", Name = w.home.name, Kind = "HOME", Blurb = "Pipo's room. Bed, plant, desk, workbench, arcade, kitchen, the front door.", Color = new Color(0.58f, 0.68f, 0.72f) } };
            foreach (var p in w.places)
            {
                ColorUtility.TryParseHtmlString(p.color, out var c);
                list.Add(new LocationSpec { Id = p.id, Name = p.name, Kind = p.kind, Blurb = p.blurb, Npc = p.npc, Color = c, Sells = p.sells ?? Array.Empty<string>(), Wildlife = p.wildlife ?? Array.Empty<string>() });
            }
            return list;
        }

        public static NpcDef Npc(string id)
        {
            if (string.IsNullOrEmpty(id)) return null;
            foreach (var n in Load().npcs) if (n.id == id) return n;
            return null;
        }
    }
}
