using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    /*
     * The whole world: every location from the app's catalog, one registry. Navigation builds the
     * new place, moves Pipo (and Nib) in, relights the scene, and reports through the bridge. Only
     * the current place exists in memory. An unknown place is refused, never faked.
     */
    public class WorldDirector : MonoBehaviour
    {
        public static WorldDirector Instance { get; private set; }
        public readonly Dictionary<string, LocationSpec> Locations = new Dictionary<string, LocationSpec>();
        public readonly List<string> Order = new List<string>();
        public LocationSpec Current { get; private set; }
        public LocationInstance Instance_ { get; private set; }
        public bool Busy { get; private set; }
        public float Fade { get; private set; }     // 0 clear .. 1 black (drawn by the host UI)

        public PipoCharacter Pipo, DockSuit;
        public NibCritter Nib;
        public Light Sun;
        public PipoCamera Cam;
        GameObject dock;

        void Awake()
        {
            Instance = this;
            foreach (var l in WorldData.Locations()) { Locations[l.Id] = l; Order.Add(l.Id); }
        }

        public bool Has(string id) => id != null && Locations.ContainsKey(id);

        /** Go somewhere. Instant=true skips the fade (tests, first load). */
        public Coroutine Goto(string id, bool instant = false)
        {
            if (!Has(id)) { PipoBridge.Emit(new OutEvent { type = "error", place = id, message = "no such place: " + id }); return null; }
            if (Busy) { PipoBridge.Emit(new OutEvent { type = "error", place = id, message = "already travelling" }); return null; }
            return StartCoroutine(Travel(id, instant));
        }

        IEnumerator Travel(string id, bool instant)
        {
            Busy = true;
            PipoBridge.Emit(new OutEvent { type = "loading", place = id });
            if (!instant) for (float t = 0; t < 0.3f; t += Time.deltaTime) { Fade = t / 0.3f; yield return null; }
            Fade = 1;
            if (Current != null) PipoBridge.Emit(new OutEvent { type = "locationExit", place = Current.Id });
            if (Instance_ != null) Destroy(Instance_.Root);
            LocationInstance li = null;
            var dest = Locations[id];
            try { li = LocationKit.Build(dest, WorldData.Load()); }
            catch (Exception ex)
            {
                // keep him where he was: a broken place must not strand him in the dark
                PipoBridge.Emit(new OutEvent { type = "error", place = id, message = "could not build " + id + ": " + ex.Message });
                dest = Current;
                if (dest != null) li = LocationKit.Build(dest, WorldData.Load());
            }
            if (li != null)
            {
                Instance_ = li;
                Current = dest;
                ApplyLight(li.Light);
                Pipo.transform.position = li.Spawn;
                Pipo.transform.rotation = Quaternion.identity;
                if (Nib) { Nib.transform.position = li.Spawn + new Vector3(-0.6f, 0, 0.35f); Nib.gameObject.SetActive(true); }
                PlaceDock(li);
                if (Cam) Cam.Snap(Pipo.transform);
                PipoBridge.Emit(new OutEvent { type = "locationEnter", place = dest.Id });
            }
            if (!instant) for (float t = 0; t < 0.35f; t += Time.deltaTime) { Fade = 1 - t / 0.35f; yield return null; }
            Fade = 0;
            Busy = false;
            PipoBridge.Emit(new OutEvent { type = "navComplete", place = Current?.Id });
        }

        void PlaceDock(LocationInstance li)
        {
            if (dock == null)
            {
                var lab = Resources.Load<GameObject>("Models/lab");
                if (lab != null)
                {
                    var labGo = Instantiate(lab);
                    var d = FindDeep(labGo.transform, "ArmorDock");
                    if (d != null)
                    {
                        d.SetParent(null, true);
                        dock = new GameObject("ArmorDock");
                        d.SetParent(dock.transform, false);
                        d.localPosition = Vector3.zero;
                        d.localScale = Vector3.one * 0.025f;   // lab.glb is in app room units
                    }
                    Destroy(labGo);
                }
            }
            if (dock == null) return;
            dock.SetActive(li.ShowsArmorDock);
            // in the room it stands where the app's stage puts it (x=200 u), against the back wall
            dock.transform.position = new Vector3((200 - 148) * 0.025f, 0, -0.9f);
            if (DockSuit != null) { DockSuit.transform.SetParent(dock.transform, false); DockSuit.transform.localPosition = new Vector3(0, 0.055f, 0); }
        }

        static Transform FindDeep(Transform t, string n)
        {
            if (t.name == n) return t;
            foreach (Transform c in t) { var f = FindDeep(c, n); if (f) return f; }
            return null;
        }

        void ApplyLight(LightLook l)
        {
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = l.Sky; RenderSettings.ambientEquatorColor = l.Equator; RenderSettings.ambientGroundColor = l.Ground;
            RenderSettings.fog = true; RenderSettings.fogMode = FogMode.Linear; RenderSettings.fogColor = l.Fog;
            RenderSettings.fogStartDistance = l.FogStart; RenderSettings.fogEndDistance = l.FogEnd;
            if (Sun) { Sun.color = l.Sun; Sun.intensity = l.SunIntensity; Sun.transform.rotation = Quaternion.Euler(l.SunEuler); }
            if (Cam) Cam.Background = l.Background;
        }

        /** Where Pipo may walk in the current place. */
        public float ClampX(float x) => Instance_ == null ? x : Mathf.Clamp(x, Instance_.MinX, Instance_.MaxX);
    }

    /** Nib: small, round, amber, one big eye. Follows Pipo. */
    public class NibCritter : MonoBehaviour
    {
        public Transform Follow;
        Transform body;
        public static NibCritter Spawn(Transform parent)
        {
            var g = new GameObject("Nib"); g.transform.SetParent(parent, false);
            var n = g.AddComponent<NibCritter>();
            n.body = new GameObject("Body").transform; n.body.SetParent(g.transform, false);
            var dome = LocationKit.Sphere(n.body, "Dome", new Vector3(0, 0.16f, 0), 0.34f, new Color(1f, 0.72f, 0.42f)); dome.transform.localScale = new Vector3(0.34f, 0.4f, 0.34f);
            LocationKit.Cyl(n.body, "Band", new Vector3(0, 0.07f, 0), 0.18f, 0.06f, new Color(0.23f, 0.27f, 0.35f));
            LocationKit.Sphere(n.body, "Eye", new Vector3(0, 0.2f, 0.15f), 0.13f, new Color(0.08f, 0.1f, 0.15f));
            LocationKit.Sphere(n.body, "Pupil", new Vector3(0, 0.2f, 0.21f), 0.06f, new Color(0.56f, 0.96f, 0.89f), new Color(0.4f, 0.9f, 0.8f));
            foreach (var c in g.GetComponentsInChildren<Collider>()) Destroy(c);
            g.AddComponent<SphereCollider>().radius = 0.25f;
            var it = g.AddComponent<InteractionTarget>(); it.Id = "nib"; it.Label = "Nib";
            return n;
        }
        void Update()
        {
            if (!Follow) return;
            var goal = Follow.position + new Vector3(-0.6f, 0, 0.35f);
            transform.position = Vector3.Lerp(transform.position, goal, 1 - Mathf.Exp(-3 * Time.deltaTime));
            float moving = Mathf.Clamp01((goal - transform.position).magnitude * 3);
            body.localPosition = new Vector3(0, Mathf.Abs(Mathf.Sin(Time.time * 10)) * 0.04f * moving, 0);
            body.localScale = new Vector3(1, 1 + Mathf.Sin(Time.time * 2.2f) * 0.015f, 1);
        }
    }
}
