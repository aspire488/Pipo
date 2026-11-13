using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    /*
     * Editor / desktop stand-in for the Android app, so the world can be reviewed in Play Mode.
     * It plays the app's role through the real bridge: it sends v1 JSON state (exactly what the app
     * sends) and answers navRequest/interaction events. On a phone the app does this instead.
     * Real data only: names, shop stock, people's lines and animals all come from world.json.
     */
    public class PreviewHost : MonoBehaviour
    {
        public static bool PointerOverUi;
        PipoBridge bridge; WorldDirector world;
        readonly StateDto state = new StateDto();
        string anim = "idle", bubble = "Hi. I'm Pipo. Tap things, or pick a place.", listFilter = "";
        float animUntil, bubbleUntil = 6, nextIdea = 6, t, goalX;
        bool showWorld, suitOn; int suitMk = 1;
        Vector2 scroll;
        readonly string[] aiStates = { "idle", "listening", "thinking", "working", "success", "error" };
        string ai = "idle";
        GUIStyle bubbleStyle, panel, title, small;

        void Start()
        {
            bridge = PipoBridge.Instance; world = WorldDirector.Instance;
            PipoBridge.OnEvent += OnEvent;
            state.suit.owned = 1;   // the preview's Pipo has built Mk I
            goalX = state.x;
        }
        void OnDestroy() => PipoBridge.OnEvent -= OnEvent;

        void Say(string s, float secs = 4f) { bubble = s; bubbleUntil = Time.time + secs; }
        void Play(string a, float secs) { anim = a; animUntil = t + secs; }

        void OnEvent(OutEvent e)
        {
            switch (e.type)
            {
                case "navRequest": Travel(e.target.Substring("door:".Length)); break;
                case "interaction": React(e.target, e.message); break;
                case "locationEnter": if (e.place != "home") Say(world.Locations[e.place].Blurb, 5); break;
                case "error": Say("Hm. " + e.message, 4); break;
            }
        }

        /** The app's job on a phone: decide the trip. Here the preview approves it. */
        void Travel(string id)
        {
            if (!world.Has(id)) return;
            state.away = id != "home"; state.place = id == "home" ? null : id;
            goalX = 148;
        }

        void React(string target, string label)
        {
            if (target == "pipo") { Play("wave", 1.4f); Say(new[] { "Hi!", "Hehe. That tickles.", "Beep.", "Oh! Hi." }[Random.Range(0, 4)]); return; }
            if (target == "nib") { Say("Nib: Beep boop!"); return; }
            var parts = target.Split(':');
            switch (parts[0])
            {
                case "npc": { var n = WorldData.Npc(parts[1]); if (n != null) { Play("wave", 1.2f); Say(n.hello, 5); } break; }
                case "item": Play("think", 1.5f); Say($"{label}. I want it. I don't need it. I want it.", 4); break;
                case "critter": Play("celebrate", 1.2f); Say($"A {label.ToLower()}! Nib, look. Quietly.", 4); break;
                case "object":
                    if (parts[1] == "door") { showWorld = true; Say("Where to? Pick a place.", 3); }
                    else if (parts[1] == "bed") { Play("sleep", 4); Say("Just resting my eyes.", 3); }
                    else { goalX = AppX(label); Say($"The {label.ToLower()}.", 2.5f); }
                    break;
            }
        }

        float AppX(string label) => Mathf.Clamp(148 + world.Pipo.transform.position.x / 0.025f + Random.Range(-30, 30), 20, 276);

        void Update()
        {
            t += Time.deltaTime;
            if (anim != "idle" && t > animUntil) anim = "idle";
            float dx = goalX - state.x;
            bool walking = Mathf.Abs(dx) > 1.5f && anim != "sleep";
            if (walking) { state.x += Mathf.Sign(dx) * Mathf.Min(Mathf.Abs(dx), 34 * Time.deltaTime); if (anim == "idle") anim = "walk"; }
            else if (anim == "walk") anim = "idle";
            nextIdea -= Time.deltaTime;
            if (nextIdea <= 0 && anim == "idle") { nextIdea = 8 + Random.value * 10; goalX = Random.Range(40f, 256f); }

            state.pose = Pose(anim, t);
            if (walking) state.pose.yaw = dx > 0 ? 1.2f : -1.2f;
            state.expr = anim == "sleep" ? "CLOSED" : anim == "think" ? "CURIOUS" : anim == "celebrate" || anim == "wave" ? "HAPPY" : anim == "dance" ? "EXCITED"
                : ai == "error" ? "WORRIED" : ai == "success" ? "HAPPY" : ai == "listening" ? "CURIOUS" : ai == "thinking" || ai == "working" ? "FOCUSED" : "CONTENT";
            state.mood = anim == "sleep" ? "SLEEPY" : "CURIOUS";
            state.sleepy = anim == "sleep";
            state.talking = Time.time < bubbleUntil - 2.5f;
            state.ai.state = ai;
            state.suit.mk = suitOn ? suitMk : 0;
            // the same message the Android app sends, through the same entry point
            bridge.Receive(JsonUtility.ToJson(new Envelope { v = PipoBridge.Version, type = "state", state = state }));
        }

        static PoseDto Pose(string a, float t)
        {
            var p = new PoseDto();
            switch (a)
            {
                case "walk": p.bob = Mathf.Abs(Mathf.Sin(t * 10)) * 2.2f; p.swing = Mathf.Sin(t * 10); p.swingAmt = 26; p.armL = p.armR = 10; p.lean = 4; break;
                case "think": p.armR = 128; p.hold = 0.35f; p.headTilt = -10 + Mathf.Sin(t * 1.4f) * 3; p.headBob = -4; p.armL = 14; break;
                case "wave": p.armR = 150 + Mathf.Sin(t * 10) * 18; p.headTilt = 8; p.bob = Mathf.Sin(t * 3) * 0.5f; break;
                case "celebrate": p.armL = p.armR = 160; p.jump = Mathf.Abs(Mathf.Sin(t * 7)) * 9; break;
                case "dance": p.bob = Mathf.Abs(Mathf.Sin(t * 6)) * 5; p.armL = 60 + Mathf.Sin(t * 6) * 70; p.armR = 60 - Mathf.Sin(t * 6) * 70; p.headTilt = Mathf.Sin(t * 6) * 14; p.yaw = Mathf.Sin(t * 3) * 0.6f; break;
                case "sleep": p.lie = 1; p.liePitch = 0.05f; p.bob = Mathf.Sin(t * 1.2f) * 0.6f; p.armL = p.armR = 6; break;
                default: p.bob = Mathf.Sin(t * 2.2f) * 0.6f; p.armL = 8 + Mathf.Sin(t * 2.2f) * 2; p.armR = 8 + Mathf.Sin(t * 2.2f + 0.4f) * 2; p.headTilt = Mathf.Sin(t * 0.5f) * 4; p.headYaw = Mathf.Sin(t * 0.27f) * 0.25f; break;
            }
            return p;
        }

        void Styles()
        {
            if (panel != null) return;
            panel = new GUIStyle(GUI.skin.box) { padding = new RectOffset(12, 12, 10, 10) };
            title = new GUIStyle(GUI.skin.label) { fontSize = 20, fontStyle = FontStyle.Bold };
            small = new GUIStyle(GUI.skin.label) { fontSize = 13, wordWrap = true };
            bubbleStyle = new GUIStyle(GUI.skin.box) { fontSize = 15, wordWrap = true, alignment = TextAnchor.MiddleCenter, padding = new RectOffset(12, 12, 8, 8) };
            bubbleStyle.normal.textColor = new Color(0.1f, 0.12f, 0.18f);
            bubbleStyle.normal.background = Tex(new Color(0.98f, 0.98f, 1f, 0.96f));
        }
        static Texture2D Tex(Color c) { var t = new Texture2D(1, 1); t.SetPixel(0, 0, c); t.Apply(); return t; }

        void OnGUI()
        {
            Styles();
            float s = Mathf.Max(1, Screen.height / 900f);
            GUI.matrix = Matrix4x4.Scale(new Vector3(s, s, 1));
            float W = Screen.width / s, H = Screen.height / s;
            var cur = world.Current;
            var rects = new List<Rect>();

            var top = new Rect(12, 12, Mathf.Min(420, W - 24), 92); rects.Add(top);
            GUILayout.BeginArea(top, panel);
            GUILayout.Label(cur != null ? cur.Name : "…", title);
            GUILayout.Label(cur != null ? $"{cur.Kind.ToLower()} · {cur.Blurb}" : "", small);
            GUILayout.EndArea();

            var bar = new Rect(12, H - 120, W - 24, 108); rects.Add(bar);
            GUILayout.BeginArea(bar, panel);
            GUILayout.BeginHorizontal();
            if (GUILayout.Button(showWorld ? "World ▴" : $"World ▾ ({world.Order.Count} places)", GUILayout.Height(34))) showWorld = !showWorld;
            if (GUILayout.Button(suitOn ? $"Suit off ({PipoCharacter.Looks[suitMk.ToString()].label})" : "Suit on", GUILayout.Height(34))) suitOn = !suitOn;
            if (GUILayout.Button("Dance", GUILayout.Height(34))) { Play("dance", 5); Say("Music in my head. Go."); }
            if (GUILayout.Button(cur != null && !cur.IsHome ? "Go home" : "Sleep", GUILayout.Height(34))) { if (cur != null && !cur.IsHome) Travel("home"); else Play("sleep", 5); }
            GUILayout.EndHorizontal();
            GUILayout.BeginHorizontal();
            GUILayout.Label("App state:", GUILayout.Width(74));
            foreach (var a in aiStates) if (GUILayout.Toggle(ai == a, a, "Button", GUILayout.Height(28))) ai = a;
            GUILayout.EndHorizontal();
            GUILayout.EndArea();

            if (showWorld)
            {
                var list = new Rect(12, 112, Mathf.Min(420, W - 24), H - 250); rects.Add(list);
                GUILayout.BeginArea(list, panel);
                GUILayout.Label("Pipo's world (from the app's catalog)", small);
                listFilter = GUILayout.TextField(listFilter);
                scroll = GUILayout.BeginScrollView(scroll);
                foreach (var id in world.Order)
                {
                    var l = world.Locations[id];
                    if (listFilter.Length > 0 && !l.Name.ToLower().Contains(listFilter.ToLower())) continue;
                    if (GUILayout.Button($"{l.Name}   ·  {l.Kind.ToLower()}", GUILayout.Height(30))) { Travel(id); showWorld = false; }
                }
                GUILayout.EndScrollView();
                GUILayout.EndArea();
            }

            // speech bubble above his head
            if (Time.time < bubbleUntil && world.Pipo != null && Camera.main != null)
            {
                var sp = Camera.main.WorldToScreenPoint(world.Pipo.HeadPosition + Vector3.up * 0.75f);
                if (sp.z > 0)
                {
                    var size = bubbleStyle.CalcHeight(new GUIContent(bubble), 300);
                    var r = new Rect(sp.x / s - 160, (Screen.height - sp.y) / s - size - 10, 320, size + 8);
                    GUI.Box(r, bubble, bubbleStyle);
                }
            }
            if (world.Fade > 0.01f) { GUI.color = new Color(0, 0, 0, world.Fade); GUI.DrawTexture(new Rect(0, 0, W, H), Texture2D.whiteTexture); GUI.color = Color.white; }

            var m = Input.mousePosition; var mp = new Vector2(m.x / s, (Screen.height - m.y) / s);
            PointerOverUi = rects.Exists(r => r.Contains(mp));
        }
    }
}
