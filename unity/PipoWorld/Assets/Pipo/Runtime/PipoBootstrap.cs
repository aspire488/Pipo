using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    /** Orbit/follow camera: drag to look, pinch or scroll to zoom, framed on Pipo's face. */
    public class PipoCamera : MonoBehaviour
    {
        public Camera Cam { get; private set; }
        Transform target;
        float az = 0.2f, el = 0.2f, dist = 4.2f, gAz = 0.2f, gEl = 0.2f, gDist = 4.2f;
        Vector3 focus;
        Vector2 lastPointer; bool dragging; float lastPinch;
        public Color Background { set { if (Cam) Cam.backgroundColor = value; } }

        void Awake()
        {
            Cam = gameObject.AddComponent<Camera>();
            Cam.clearFlags = CameraClearFlags.SolidColor; Cam.fieldOfView = 40; Cam.nearClipPlane = 0.05f; Cam.farClipPlane = 200;
            gameObject.tag = "MainCamera";
        }

        public void Snap(Transform t) { target = t; focus = t.position + Vector3.up * 0.62f; az = gAz = 0.2f; el = gEl = 0.2f; dist = gDist = Screen.height > Screen.width ? 4.6f : 3.4f; Place(); }

        void Update()
        {
            // touch / mouse orbit (single finger or left button), pinch / wheel zoom
            if (Input.touchCount == 2)
            {
                float d = Vector2.Distance(Input.GetTouch(0).position, Input.GetTouch(1).position);
                if (lastPinch > 0) gDist = Mathf.Clamp(gDist * lastPinch / Mathf.Max(1, d), 1.6f, 9);
                lastPinch = d; dragging = false;
            }
            else
            {
                lastPinch = 0;
                bool down = Input.GetMouseButton(0);
                Vector2 p = Input.mousePosition;
                if (down && dragging) { var dlt = p - lastPointer; gAz = Mathf.Clamp(gAz - dlt.x * 0.005f, -1.4f, 1.4f); gEl = Mathf.Clamp(gEl - dlt.y * 0.004f, 0.02f, 1.1f); }
                dragging = down; lastPointer = p;
            }
            gDist = Mathf.Clamp(gDist * (1 - Input.mouseScrollDelta.y * 0.08f), 1.6f, 9);
            float k = 1 - Mathf.Exp(-6 * Time.deltaTime);
            az = Mathf.Lerp(az, gAz, k); el = Mathf.Lerp(el, gEl, k); dist = Mathf.Lerp(dist, gDist, k);
            if (target) focus = Vector3.Lerp(focus, target.position + Vector3.up * 0.62f, 1 - Mathf.Exp(-5 * Time.deltaTime));
            Place();
        }

        void Place()
        {
            var off = new Vector3(Mathf.Sin(az) * Mathf.Cos(el), Mathf.Sin(el), Mathf.Cos(az) * Mathf.Cos(el)) * dist;
            transform.position = focus + off;
            transform.LookAt(focus);
        }

        public void SetView(float azimuth, float elevation, float distance) { az = gAz = azimuth; el = gEl = elevation; dist = gDist = distance; Place(); }
    }

    /** Applies the host's state (the app, or the preview host) to the world. Unity never invents state. */
    public class StateApplier : MonoBehaviour
    {
        public static readonly Dictionary<string, Color> MoodGlow = new Dictionary<string, Color>
        {
            ["HAPPY"] = Hex(0xFFC27A), ["EXCITED"] = Hex(0xFFD36E), ["SLEEPY"] = Hex(0x9FA8FF), ["BORED"] = Hex(0x9AA7B8), ["GRUMPY"] = Hex(0xFF7A6B),
            ["LONELY"] = Hex(0x7FB6FF), ["NERVOUS"] = Hex(0xFFE08A), ["EMBARRASSED"] = Hex(0xFF8FA3), ["MISCHIEVOUS"] = Hex(0xC99BFF), ["CURIOUS"] = Hex(0x8FF5E2),
            ["RELAXED"] = Hex(0x8FF5E2), ["PROUD"] = Hex(0xFFC27A),
        };
        static Color Hex(int h) => new Color(((h >> 16) & 255) / 255f, ((h >> 8) & 255) / 255f, (h & 255) / 255f);

        public WorldDirector World; public PipoBridge Bridge;
        string lastExpr, lastMood;

        void Start() { Bridge.OnState += Apply; Bridge.OnGoto += id => World.Goto(id); }

        public void Apply(StateDto s)
        {
            var p = World.Pipo; if (p == null) return;
            p.Pose = s.pose ?? new PoseDto();
            // where he is: the app's trip state decides the place
            string want = s.away && !string.IsNullOrEmpty(s.place) ? s.place : "home";
            if (!World.Busy && World.Current != null && World.Current.Id != want && World.Has(want)) World.Goto(want);
            var li = World.Instance_;
            if (li != null && !World.Busy)
            {
                float x = World.Current.IsHome ? (s.x - 148) * 0.025f : Mathf.Lerp(li.MinX, li.MaxX, Mathf.Clamp01(s.x / 296f));
                var pos = p.transform.position;
                pos.x = Mathf.Lerp(pos.x, World.ClampX(x), 1 - Mathf.Exp(-14 * Time.deltaTime));
                pos.y = s.lift * 0.025f; pos.z = li.LaneZ;
                p.transform.position = pos;
            }
            var suit = s.suit ?? new SuitDto();
            p.Equip(PipoCharacter.SuitKey(suit.mk, suit.cardboard));
            var owned = PipoCharacter.SuitKey(suit.owned > 0 ? suit.owned : (suit.ownedCardboard ? 1 : 0), suit.owned == 0 && suit.ownedCardboard);
            if (World.DockSuit != null)
            {
                if (owned != null) World.DockSuit.Equip(owned, true);
                World.DockSuit.gameObject.SetActive(owned != null && p.Suit == null);   // the dock holds it exactly when he isn't wearing it
            }
            if (p.Face != null)
            {
                if (s.expr != lastExpr || s.mood != lastMood)
                {
                    lastExpr = s.expr; lastMood = s.mood;
                    p.Face.SetExpression(s.expr, MoodGlow.TryGetValue(s.mood ?? "", out var c) ? c : (Color?)null);
                }
                p.Face.Talking = s.talking || s.ai?.state == "speaking";
                p.Face.Sleepy = s.sleepy;
                var st = s.ai?.state ?? "idle";
                p.Face.Hold(st == "thinking" || st == "working" ? new Vector2(0.42f, -0.55f) : st == "listening" || st == "speaking" ? new Vector2(0, 0.05f) : (Vector2?)null);
            }
        }
    }

    /** Taps: whatever was touched is reported to the host; the host decides what it means. */
    public class TapInput : MonoBehaviour
    {
        public Camera Cam;
        Vector2 downAt; float downT;
        void Update()
        {
            if (Input.GetMouseButtonDown(0)) { downAt = Input.mousePosition; downT = Time.unscaledTime; }
            if (!Input.GetMouseButtonUp(0) || Vector2.Distance(downAt, Input.mousePosition) > 10 || Time.unscaledTime - downT > 0.6f) return;
            if (PreviewHost.PointerOverUi) return;
            if (!Physics.Raycast(Cam.ScreenPointToRay(Input.mousePosition), out var hit, 100)) return;
            var it = hit.collider.GetComponentInParent<InteractionTarget>();
            if (it != null) PipoBridge.Emit(new OutEvent { type = it.Id.StartsWith("door:") ? "navRequest" : "interaction", target = it.Id, place = WorldDirector.Instance.Current?.Id, message = it.Label });
            else if (hit.collider.GetComponentInParent<PipoCharacter>() != null) PipoBridge.Emit(new OutEvent { type = "interaction", target = "pipo", place = WorldDirector.Instance.Current?.Id });
        }
    }

    /** Builds the whole runtime from an empty scene, so the project has no hand-made scene state. */
    public class PipoBootstrap : MonoBehaviour
    {
        /** Tests turn the preview host off and drive the bridge themselves. */
        public static bool UsePreviewHost = true;
        public WorldDirector World { get; private set; }

        void Awake()
        {
            Application.targetFrameRate = 60;
            QualitySettings.shadowDistance = 18;
            var bridge = new GameObject("PipoBridge").AddComponent<PipoBridge>();
            var camGo = new GameObject("Camera");
            var cam = camGo.AddComponent<PipoCamera>();
            var sun = new GameObject("Sun").AddComponent<Light>();
            sun.type = LightType.Directional; sun.shadows = LightShadows.Soft; sun.shadowStrength = 0.55f;
            var fill = new GameObject("Fill").AddComponent<Light>();
            fill.type = LightType.Directional; fill.intensity = 0.35f; fill.color = new Color(0.8f, 0.88f, 1f); fill.transform.rotation = Quaternion.Euler(20, 160, 0); fill.shadows = LightShadows.None;

            World = new GameObject("World").AddComponent<WorldDirector>();
            World.Sun = sun; World.Cam = cam;
            World.Pipo = PipoCharacter.Spawn(World.transform);
            World.Pipo.gameObject.AddComponent<CapsuleCollider>().center = new Vector3(0, 0.5f, 0);
            World.DockSuit = PipoCharacter.Spawn(World.transform, armorOnlyOnDock: true);
            World.DockSuit.gameObject.SetActive(false);
            World.Nib = NibCritter.Spawn(World.transform);
            World.Nib.Follow = World.Pipo.transform;

            var applier = gameObject.AddComponent<StateApplier>(); applier.World = World; applier.Bridge = bridge;
            gameObject.AddComponent<TapInput>().Cam = cam.Cam;
            World.Goto("home", instant: true);
            if (UsePreviewHost && Application.platform != RuntimePlatform.Android) gameObject.AddComponent<PreviewHost>();
            PipoBridge.Emit(new OutEvent { type = "ready", message = "bridge v" + PipoBridge.Version });
        }
    }
}
