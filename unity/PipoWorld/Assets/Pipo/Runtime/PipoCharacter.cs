using System;
using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    /*
     * Pipo from models/pipo.glb (art/blender/build_pipo3d.py), the same asset the app's WebView
     * stage uses. Pivots are the Blender empties: Body, Head, Antenna, Arm.L/R, Leg.L/R. Pose fields
     * and units are the app's Rig (Pipo-local, 100 units tall, degrees), so the app can stream its
     * pose here unchanged. The importer's handedness flip is detected from the model, not assumed.
     */
    public class PipoCharacter : MonoBehaviour
    {
        public Transform Model { get; private set; }
        Transform body, head, antenna; readonly Transform[] arms = new Transform[2], legs = new Transform[2];
        Vector3 armBind0, armBind1, legBind0, legBind1, headBind;
        float mX = 1, mY = 1, mZ = 1;       // sign of rotations about each axis after the importer's mirror
        public PipoFace Face { get; private set; }
        public readonly List<Transform> Armor = new List<Transform>();
        readonly Dictionary<Material, Material> armorMats = new Dictionary<Material, Material>();
        readonly Dictionary<Transform, Vector3> armorScale = new Dictionary<Transform, Vector3>();
        bool armorOnly;

        public PoseDto Pose = new PoseDto();
        PoseDto cur = new PoseDto();
        float yawCur, yawV, antAngle, antVel, prevYaw, suitT = 1;
        int suitDir;
        /** Equipment, one source of truth: null = no suit; "mk0" (cardboard) | "1" | "2" | "3". */
        public string Suit { get; private set; }

        public static PipoCharacter Spawn(Transform parent, bool armorOnlyOnDock = false)
        {
            var prefab = Resources.Load<GameObject>("Models/pipo");
            if (prefab == null) throw new InvalidOperationException("Resources/Models/pipo.glb did not import (is com.unity.cloud.gltfast installed?)");
            var root = new GameObject(armorOnlyOnDock ? "DockSuit" : "Pipo");
            root.transform.SetParent(parent, false);
            var c = root.AddComponent<PipoCharacter>();
            c.armorOnly = armorOnlyOnDock;
            c.Init(Instantiate(prefab, root.transform).transform);
            return c;
        }

        static Transform Find(Transform t, params string[] names)
        {
            foreach (var n in names) if (t.name == n) return t;
            foreach (Transform ch in t) { var f = Find(ch, names); if (f != null) return f; }
            return null;
        }

        void Init(Transform model)
        {
            Model = model;
            body = Find(model, "Body"); head = Find(model, "Head"); antenna = Find(model, "Antenna");
            arms[0] = Find(model, "Arm.L", "ArmL"); arms[1] = Find(model, "Arm.R", "ArmR");
            legs[0] = Find(model, "Leg.L", "LegL"); legs[1] = Find(model, "Leg.R", "LegR");
            if (body == null || head == null || arms[0] == null || legs[0] == null) throw new InvalidOperationException("pipo.glb rig pivots missing");
            armBind0 = arms[0].localPosition; armBind1 = arms[1].localPosition; legBind0 = legs[0].localPosition; legBind1 = legs[1].localPosition; headBind = head.localPosition;

            // glTF is right-handed; the importer mirrors one axis. Find which, from the model itself:
            // Arm.L sits at -x in glTF, and the visor at +z.
            var visor = Find(model, "Visor");
            var visorLocal = head.InverseTransformPoint(visor.GetComponentInChildren<Renderer>().bounds.center);
            bool xMirror = armBind0.x > 0, zMirror = visorLocal.z < 0;
            if (xMirror) { mY = -1; mZ = -1; }
            if (zMirror) { mX = -1; mY = -1; }
            // face the camera (+Z) whichever way the import came in
            model.localRotation = zMirror ? Quaternion.Euler(0, 180, 0) : Quaternion.identity;
            model.localScale = Vector3.one * 0.01f;   // 100 model units -> 1 m

            foreach (var t in model.GetComponentsInChildren<Transform>(true))
                if (t.name.StartsWith("MK1_")) { Armor.Add(t); armorScale[t] = t.localScale; }
            foreach (var r in model.GetComponentsInChildren<Renderer>(true))
            {
                if (!r.transform.name.StartsWith("MK1_")) { if (armorOnly) r.enabled = false; continue; }
                var shared = r.sharedMaterials;
                for (int i = 0; i < shared.Length; i++)
                {
                    if (!armorMats.TryGetValue(shared[i], out var inst)) armorMats[shared[i]] = inst = new Material(shared[i]);
                    shared[i] = inst;
                }
                r.sharedMaterials = shared;
            }
            foreach (var a in Armor) a.gameObject.SetActive(false);
            if (!armorOnly) Face = PipoFace.Build(head, visorLocal, zMirror ? -1 : 1);
        }

        /* ---------------- suit ---------------- */
        public static readonly Dictionary<string, (Color red, Color gold, Color core, float glow, string label)> Looks = new Dictionary<string, (Color, Color, Color, float, string)>
        {
            ["mk0"] = (new Color(0.77f, 0.6f, 0.42f), new Color(0.91f, 0.86f, 0.77f), new Color(0.88f, 0.38f, 0.35f), 0f, "Mk 0 (cardboard)"),
            ["1"] = (new Color(0.7f, 0.13f, 0.16f), new Color(0.89f, 0.7f, 0.29f), new Color(0.87f, 0.98f, 1f), 1.4f, "Mk I"),
            ["2"] = (new Color(0.66f, 0.12f, 0.18f), new Color(0.85f, 0.64f, 0.23f), new Color(0.87f, 0.98f, 1f), 1.6f, "Mk II"),
            ["3"] = (new Color(0.62f, 0.11f, 0.2f), new Color(0.94f, 0.76f, 0.35f), new Color(0.62f, 0.95f, 1f), 2.2f, "Mk III"),
        };
        public static string SuitKey(int mk, bool cardboard) => mk <= 0 ? null : cardboard ? "mk0" : Mathf.Min(3, mk).ToString();

        /** Put a suit on or take it off. Idempotent: the same request twice changes nothing. */
        public bool Equip(string key, bool instant = false)
        {
            if (key != null && !Looks.ContainsKey(key)) throw new ArgumentException("unknown suit " + key);
            if (key == Suit && (suitDir == 0 || instant)) return false;
            if (key != null) ApplyLook(Looks[key]);
            bool wasOn = Suit != null;
            Suit = key;
            if (instant)
            {
                suitDir = 0; suitT = 1;
                foreach (var a in Armor) { a.gameObject.SetActive(key != null); a.localScale = armorScale[a]; }
                ShowThrusters(key);
                return true;
            }
            suitDir = key != null ? 1 : -1; suitT = 0;
            if (key != null && !wasOn) foreach (var a in Armor) { a.gameObject.SetActive(true); a.localScale = armorScale[a] * 0.001f; }
            if (key != null) ShowThrusters(key);
            return true;
        }

        void ApplyLook((Color red, Color gold, Color core, float glow, string label) look)
        {
            foreach (var kv in armorMats)
            {
                var n = kv.Key.name; var m = kv.Value;
                var c = n.Contains("Armor_Red") ? look.red : n.Contains("Armor_Gold") ? look.gold : look.core;
                SetColor(m, c);
                if (n.Contains("Armor_Core")) { SetEmission(m, look.core * look.glow); }
                foreach (var p in new[] { "metallicFactor", "_Metallic" }) if (m.HasProperty(p)) m.SetFloat(p, look.glow > 0 ? 0.5f : 0f);
                foreach (var p in new[] { "roughnessFactor" }) if (m.HasProperty(p)) m.SetFloat(p, look.glow > 0 ? 0.36f : 0.95f);
            }
        }

        /** Boot thrusters are a Mk II+ feature: hidden on Mk 0 and Mk I. */
        public static bool HasThrusters(string key) => key != null && Looks[key].glow >= 1.6f;
        void ShowThrusters(string key) { foreach (var a in Armor) if (a.name.StartsWith("MK1_Thruster")) a.gameObject.SetActive(HasThrusters(key)); }

        public static void SetColor(Material m, Color c) { foreach (var p in new[] { "baseColorFactor", "_BaseColor", "_Color" }) if (m.HasProperty(p)) m.SetColor(p, c); }
        public static void SetEmission(Material m, Color c) { foreach (var p in new[] { "emissiveFactor", "_EmissionColor" }) if (m.HasProperty(p)) m.SetColor(p, c); }

        /* ---------------- per frame ---------------- */
        void Update()
        {
            if (armorOnly) { UpdateSuit(Time.deltaTime); return; }
            float dt = Mathf.Min(Time.deltaTime, 0.05f), t = Time.time;
            float k = 1 - Mathf.Exp(-7 * dt), kf = 1 - Mathf.Exp(-16 * dt);
            var p = Pose; var c = cur;
            c.bob = Mathf.Lerp(c.bob, p.bob, kf); c.squash = Mathf.Lerp(c.squash, p.squash, kf); c.stretch = Mathf.Lerp(c.stretch, p.stretch, k);
            c.lean = Mathf.Lerp(c.lean, p.lean, k); c.headTilt = Mathf.Lerp(c.headTilt, p.headTilt, k); c.headBob = Mathf.Lerp(c.headBob, p.headBob, k);
            c.armL = Mathf.Lerp(c.armL, p.armL, kf); c.armR = Mathf.Lerp(c.armR, p.armR, kf); c.legL = Mathf.Lerp(c.legL, p.legL, kf); c.legR = Mathf.Lerp(c.legR, p.legR, kf);
            c.spread = Mathf.Lerp(c.spread, p.spread, k); c.sit = Mathf.Lerp(c.sit, p.sit, k); c.lie = Mathf.Lerp(c.lie, p.lie, k); c.jump = Mathf.Lerp(c.jump, p.jump, kf);
            c.crouch = Mathf.Lerp(c.crouch, p.crouch, k); c.cross = Mathf.Lerp(c.cross, p.cross, k); c.cover = Mathf.Lerp(c.cover, p.cover, k); c.hold = Mathf.Lerp(c.hold, p.hold, k); c.shake = Mathf.Lerp(c.shake, p.shake, kf);
            c.headYaw = Mathf.Lerp(c.headYaw, p.headYaw, 1 - Mathf.Exp(-12 * dt));
            // yaw spring, underdamped like the app
            float a = (p.yaw - yawCur) * 42 - yawV * 8.5f; yawV += a * dt; yawCur += yawV * dt;

            body.localPosition = new Vector3(c.shake, (c.bob + c.jump) - c.sit * 9 - c.crouch * 8, 0);
            body.localScale = new Vector3(c.stretch, c.squash, c.stretch);
            float leanX = -c.lean;
            if (c.lie > 0.02f) { leanX = -c.lie * 90 + -c.lean * (1 - c.lie); body.localPosition = new Vector3(c.shake, Mathf.Lerp(body.localPosition.y, 6 * c.lie + c.bob, c.lie), 0); }
            body.localRotation = Quaternion.AngleAxis(mY * yawCur * Mathf.Rad2Deg, Vector3.up) * Quaternion.AngleAxis(mX * leanX, Vector3.right);
            head.localPosition = headBind;
            head.localRotation = Quaternion.AngleAxis(mY * Mathf.Clamp(c.headYaw, -1.2f, 1.2f) * Mathf.Rad2Deg, Vector3.up)
                * Quaternion.AngleAxis(mX * (c.headBob - c.headTilt * 0.4f), Vector3.right) * Quaternion.AngleAxis(mZ * -c.headTilt * 0.7f, Vector3.forward);

            float acc = (p.yaw - prevYaw) / Mathf.Max(dt, 1e-4f); prevYaw = p.yaw;
            float target = c.crouch * -30 + Mathf.Sin(t * 1.3f) * 5;
            float aa = (target - antAngle) * 140 - antVel * 7.5f - acc * 0.25f; antVel += aa * dt; antAngle += antVel * dt;
            if (antenna) antenna.localRotation = Quaternion.AngleAxis(mZ * Mathf.Clamp(antAngle, -70, 70), Vector3.forward);

            float[] lift = { c.legL, c.legR }; Vector3[] lb = { legBind0, legBind1 };
            for (int i = 0; i < 2; i++)
            {
                float sx = Mathf.Sign(lb[i].x);
                legs[i].localPosition = new Vector3(lb[i].x + sx * c.spread, lb[i].y + lift[i] * 0.4f, lb[i].z);
                float swing = (i == 0 ? 1 : -1) * p.swing * p.swingAmt;
                legs[i].localRotation = Quaternion.AngleAxis(mX * (swing - c.sit * 74 - c.lie * 30), Vector3.right);
            }
            Vector3[] ab = { armBind0, armBind1 };
            for (int i = 0; i < 2; i++)
            {
                float raise = i == 0 ? c.armL : c.armR, side = i == 0 ? -1 : 1, sx = Mathf.Sign(ab[i].x);
                float rz = side * raise, rx = p.swing * p.swingAmt * (i == 0 ? 1 : -1) * 0.9f, x = Mathf.Abs(ab[i].x);
                if (c.cross > 0.05f) { rz = side * Mathf.Lerp(raise, 16, c.cross); rx = Mathf.Lerp(rx, 72, c.cross); x = Mathf.Lerp(x, 6, c.cross); }
                if (c.hold > 0.05f) { rx = Mathf.Lerp(rx, 62, c.hold); rz = side * Mathf.Lerp(raise, 22, c.hold); }
                if (c.cover > 0.05f) { rx = Mathf.Lerp(rx, 128, c.cover); rz = side * Mathf.Lerp(raise, 26, c.cover); x = Mathf.Lerp(x, 17, c.cover); }
                arms[i].localPosition = new Vector3(sx * x, ab[i].y, ab[i].z);
                arms[i].localRotation = Quaternion.AngleAxis(mX * rx, Vector3.right) * Quaternion.AngleAxis(mZ * rz, Vector3.forward);
            }
            UpdateSuit(dt);
        }

        void UpdateSuit(float dt)
        {
            if (suitDir == 0) return;
            suitT = Mathf.Min(1, suitT + dt / 0.9f);
            int n = Armor.Count;
            for (int i = 0; i < n; i++)
            {
                float order = suitDir > 0 ? (float)i / n : 1 - (float)i / n;
                float local = Mathf.Clamp01((suitT - order * 0.55f) / 0.45f);
                float e = suitDir > 0 ? 1 - Mathf.Pow(1 - local, 3) * Mathf.Cos(local * 6) : 1 - local;
                Armor[i].localScale = armorScale[Armor[i]] * Mathf.Max(0.001f, e);
            }
            if (suitT >= 1)
            {
                if (suitDir < 0) foreach (var a in Armor) { a.gameObject.SetActive(false); a.localScale = armorScale[a]; }
                suitDir = 0;
            }
        }

        public bool SuitAnimating => suitDir != 0;
        public Vector3 HeadPosition => head.position;
        public int VisibleArmorPieces { get { int v = 0; foreach (var a in Armor) if (a.gameObject.activeInHierarchy) v++; return v; } }
        /** How many pieces a given suit should show. */
        public int ExpectedPieces(string key) { if (key == null) return 0; int n = 0; foreach (var a in Armor) if (!a.name.StartsWith("MK1_Thruster") || HasThrusters(key)) n++; return n; }
    }
}
