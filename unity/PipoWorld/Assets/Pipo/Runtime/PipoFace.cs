using System.Collections.Generic;
using UnityEngine;

namespace Pipo
{
    /*
     * Pipo's screen face in Unity: the same design as the app stage (stage/src/face.js) — a lit navy
     * screen, two mint pill eyes with one catchlight, a small smile. Built from a few textured quads
     * (textures generated once), animated by transform: no per-frame texture painting on the phone.
     */
    public class PipoFace : MonoBehaviour
    {
        // layout in head-local units (the screen is 45.6 x 31, like the stage)
        const float W = 45.6f, H = 31f, EyeDX = 8.2f, EyeY = 1.5f, EyeW = 5.7f, EyeH = 7.7f, MouthY = -7.75f, MouthW = 5.6f;

        Transform eyeL, eyeR, glowL, glowR, mouth, blushL, blushR;
        Material eyeMatL, eyeMatR, glowMat, mouthMat, blushMat;
        Texture2D texEye, texArc, texClosed, texSmile, texFlat, texOpen, texGlow;
        Color eyeColor = new Color(0.56f, 0.96f, 0.89f);

        class Expr { public float scale = 1, lidTop, lidAngle, arc, closed, round, curve = 0.5f, blush, open; }
        static readonly Dictionary<string, Expr> Presets = new Dictionary<string, Expr>
        {
            ["CONTENT"] = new Expr(), ["HAPPY"] = new Expr { arc = 1, curve = 1 }, ["EXCITED"] = new Expr { scale = 1.1f, curve = 0.9f, open = 0.5f },
            ["CURIOUS"] = new Expr { scale = 1.05f, curve = 0.1f }, ["SLEEPY"] = new Expr { lidTop = 0.45f, scale = 0.95f, curve = 0 },
            ["BORED"] = new Expr { lidTop = 0.5f, curve = 0 }, ["SURPRISED"] = new Expr { round = 1, scale = 1.1f, open = 0.6f, curve = 0 },
            ["ANNOYED"] = new Expr { lidTop = 0.25f, lidAngle = 0.5f, curve = -0.4f }, ["EMBARRASSED"] = new Expr { arc = 0.8f, blush = 1, curve = 0.2f },
            ["SAD"] = new Expr { lidAngle = -0.45f, curve = -0.6f, scale = 0.95f }, ["SUSPICIOUS"] = new Expr { lidTop = 0.3f, curve = 0 },
            ["NERVOUS"] = new Expr { scale = 0.88f, curve = 0 }, ["PROUD"] = new Expr { lidTop = 0.2f, curve = 0.85f },
            ["MISCHIEF"] = new Expr { lidTop = 0.35f, lidAngle = 0.2f, curve = 0.7f }, ["CLOSED"] = new Expr { closed = 1, curve = 0.1f },
            ["FOCUSED"] = new Expr { lidTop = 0.3f, curve = 0 }, ["LOVE"] = new Expr { arc = 1, blush = 1, curve = 1 },
            ["WINK"] = new Expr { curve = 0.8f, blush = 0.2f }, ["LAUGH"] = new Expr { arc = 1, curve = 1, open = 0.7f, blush = 0.25f },
            ["WORRIED"] = new Expr { lidAngle = -0.35f, scale = 0.92f, curve = -0.2f }, ["DIZZY"] = new Expr { round = 1, curve = 0 },
        };

        Expr target = Presets["CONTENT"];
        float scale = 1, lidTop, lidAngle, arc, closed, round, curve = 0.5f, blush, open;
        float blink, blinkT, nextBlink = 2.5f, gazeT = 1.5f, gazeX, gazeY, lookX, lookY;
        bool doubleBlink;
        public bool Talking, Sleepy;
        Vector2? hold;
        public string Current { get; private set; } = "CONTENT";
        public float BlinkAmount => blink;

        public static PipoFace Build(Transform head, Vector3 visorCenterLocal, float faceSign)
        {
            var root = new GameObject("Face").transform;
            root.SetParent(head, false);
            root.localPosition = new Vector3(visorCenterLocal.x, visorCenterLocal.y, visorCenterLocal.z + faceSign * 1.45f);
            // Unity's quad faces -Z: turn it to face out of the visor
            root.localRotation = faceSign > 0 ? Quaternion.Euler(0, 180, 0) : Quaternion.identity;
            var f = root.gameObject.AddComponent<PipoFace>();
            f.Make();
            return f;
        }

        Material NewMat(Texture2D tex, int queue)
        {
            var m = new Material(Shader.Find("Unlit/Transparent")) { mainTexture = tex, renderQueue = queue };
            return m;
        }

        Transform Quad(string name, Material m, float w, float h, Vector3 pos)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Destroy(q.GetComponent<Collider>());
            q.name = name; q.transform.SetParent(transform, false);
            q.transform.localPosition = pos; q.transform.localScale = new Vector3(w, h, 1);
            q.GetComponent<Renderer>().sharedMaterial = m;
            q.GetComponent<Renderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            return q.transform;
        }

        void Make()
        {
            texGlow = Radial(64, Color.white);
            BakeEyeTextures();
            texSmile = Curve(128, 48, 0.75f, false); texFlat = Curve(128, 48, 0f, false); texOpen = Oval(96, 64);
            // z is towards the viewer after the 180° turn (local -Z faces out)
            Quad("Screen", NewMat(Screen(256, 174), 3000), W, H, Vector3.zero);
            glowMat = NewMat(texGlow, 3001); glowMat.color = new Color(eyeColor.r, eyeColor.g, eyeColor.b, 0.32f);
            glowL = Quad("GlowL", glowMat, EyeW * 2.6f, EyeH * 2.2f, new Vector3(-EyeDX, EyeY, -0.05f));
            glowR = Quad("GlowR", glowMat, EyeW * 2.6f, EyeH * 2.2f, new Vector3(EyeDX, EyeY, -0.05f));
            eyeMatL = NewMat(texEye, 3002); eyeMatR = NewMat(texEye, 3002);
            eyeL = Quad("EyeL", eyeMatL, EyeW, EyeH, new Vector3(-EyeDX, EyeY, -0.1f));
            eyeR = Quad("EyeR", eyeMatR, EyeW, EyeH, new Vector3(EyeDX, EyeY, -0.1f));
            mouthMat = NewMat(texSmile, 3002); mouthMat.color = eyeColor;
            mouth = Quad("Mouth", mouthMat, MouthW * 1.4f, MouthW * 0.55f, new Vector3(0, MouthY, -0.1f));
            blushMat = NewMat(Radial(64, new Color(1f, 0.56f, 0.64f)), 3001); blushMat.color = new Color(1, 1, 1, 0);
            blushL = Quad("BlushL", blushMat, 9, 6, new Vector3(-13.2f, -4.6f, -0.08f));
            blushR = Quad("BlushR", blushMat, 9, 6, new Vector3(13.2f, -4.6f, -0.08f));
        }

        public void SetExpression(string name, Color? moodColor = null)
        {
            if (!Presets.TryGetValue(name ?? "CONTENT", out var e)) { e = Presets["CONTENT"]; name = "CONTENT"; }
            target = e; Current = name;
            if (moodColor.HasValue && moodColor.Value != eyeColor) { eyeColor = moodColor.Value; BakeEyeTextures(); glowMat.color = new Color(eyeColor.r, eyeColor.g, eyeColor.b, 0.32f); mouthMat.color = eyeColor; }
        }

        /** Held gaze from app state (thinking looks up and away; listening looks at you), or null. */
        public void Hold(Vector2? g) => hold = g;

        void Update()
        {
            float dt = Mathf.Min(Time.deltaTime, 0.05f);
            float dur = Sleepy ? 0.34f : 0.16f;
            nextBlink -= dt;
            if (nextBlink <= 0) { blinkT = dur; nextBlink = (Sleepy ? 1.4f : 2.2f) + Random.value * (Sleepy ? 1.6f : 3f); doubleBlink = Random.value < 0.18f; }
            blink = 0;
            if (blinkT > 0) { blinkT -= dt; blink = Mathf.Sin(Mathf.PI * Mathf.Clamp01(1 - blinkT / dur)); if (blinkT <= 0 && doubleBlink) { doubleBlink = false; blinkT = dur; } }
            gazeT -= dt;
            if (gazeT <= 0) { gazeT = 1.6f + Random.value * 2.8f; bool back = Random.value < 0.45f; gazeX = back ? 0 : (Random.value * 2 - 1) * (Sleepy ? 0.15f : 0.32f); gazeY = back ? 0 : (Random.value * 2 - 1) * 0.18f; }
            float kE = 1 - Mathf.Exp(-12 * dt), kM = 1 - Mathf.Exp(-6 * dt), kG = 1 - Mathf.Exp(-9 * dt);
            scale = Mathf.Lerp(scale, Mathf.Min(target.scale, 1.1f), kE); lidTop = Mathf.Lerp(lidTop, target.lidTop, kE); lidAngle = Mathf.Lerp(lidAngle, target.lidAngle, kE);
            arc = Mathf.Lerp(arc, target.arc, kE); closed = Mathf.Lerp(closed, target.closed, kE); round = Mathf.Lerp(round, target.round, kE);
            curve = Mathf.Lerp(curve, target.curve, kM); blush = Mathf.Lerp(blush, target.blush, kM); open = Mathf.Lerp(open, target.open, kM);
            var g = hold ?? new Vector2(gazeX, gazeY);
            lookX = Mathf.Lerp(lookX, g.x, kG); lookY = Mathf.Lerp(lookY, g.y, kG);

            float gx = lookX * 1.4f, gy = -lookY * 1.0f;   // +lookY is down, like the stage
            bool arcEyes = arc > 0.5f && closed < 0.5f, closedEyes = closed > 0.5f || blink > 0.9f;
            var tex = arcEyes ? texArc : closedEyes ? texClosed : texEye;
            eyeMatL.mainTexture = tex; eyeMatR.mainTexture = tex;
            float w = EyeW * scale * (round > 0.5f ? 1.15f : 1), h = EyeH * scale * (round > 0.5f ? 0.86f : 1);
            float hh = arcEyes || closedEyes ? h * 0.55f : h * (1 - blink * 0.9f) * (1 - lidTop * 0.5f);
            float drop = arcEyes || closedEyes ? 0 : (h - hh) * 0.35f * (lidTop > 0.02f ? 1 : 0.3f);
            for (int i = 0; i < 2; i++)
            {
                var e = i == 0 ? eyeL : eyeR; var gl = i == 0 ? glowL : glowR; float side = i == 0 ? -1 : 1;
                e.localPosition = new Vector3(side * EyeDX + gx, EyeY + gy - drop, -0.1f);
                e.localScale = new Vector3(w, hh, 1);
                // a soft slant for annoyed/sad, never an aggressive one (local x is mirrored by the 180° turn)
                e.localRotation = Quaternion.Euler(0, 0, side * lidAngle * 12f);
                gl.localPosition = new Vector3(side * EyeDX + gx, EyeY + gy, -0.05f);
                gl.localScale = new Vector3(w * 2.6f, Mathf.Max(hh, h * 0.5f) * 2.2f, 1);
            }
            float mo = Talking ? Mathf.Max(open, (0.35f + 0.65f * Mathf.Abs(Mathf.Sin(Time.time * 13))) ) : open;
            mouthMat.mainTexture = mo > 0.15f ? texOpen : Mathf.Abs(curve) < 0.2f ? texFlat : texSmile;
            mouth.localPosition = new Vector3(gx * 0.5f, MouthY + gy * 0.4f, -0.1f);
            mouth.localScale = mo > 0.15f ? new Vector3(MouthW * 1.1f, MouthW * (0.35f + mo * 0.5f), 1) : new Vector3(MouthW * 1.4f, MouthW * 0.55f * (curve < -0.2f ? -1 : 1), 1);
            blushMat.color = new Color(1, 1, 1, blush * 0.7f);
        }

        /* ---------------- textures (made once) ---------------- */
        void BakeEyeTextures()
        {
            texEye = Pill(64, 86, eyeColor);
            texArc = Curve(96, 48, -0.85f, true, eyeColor);
            texClosed = Curve(96, 48, 0.6f, true, eyeColor);
            if (eyeMatL) { eyeMatL.mainTexture = texEye; eyeMatR.mainTexture = texEye; }
        }

        static Texture2D New(int w, int h) => new Texture2D(w, h, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp, filterMode = FilterMode.Bilinear };

        static float RoundRectAlpha(float x, float y, float w, float h, float r)
        {
            float dx = Mathf.Max(Mathf.Abs(x - w / 2) - (w / 2 - r), 0), dy = Mathf.Max(Mathf.Abs(y - h / 2) - (h / 2 - r), 0);
            return Mathf.Clamp01(r - Mathf.Sqrt(dx * dx + dy * dy) + 0.5f);
        }

        static Texture2D Screen(int w, int h)
        {
            var t = New(w, h); var px = new Color[w * h];
            var top = new Color(0.165f, 0.255f, 0.4f); var mid = new Color(0.11f, 0.17f, 0.28f); var bot = new Color(0.08f, 0.12f, 0.2f);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                float v = 1 - (float)y / h;   // 0 at top
                var c = v < 0.55f ? Color.Lerp(top, mid, v / 0.55f) : Color.Lerp(mid, bot, (v - 0.55f) / 0.45f);
                float d = Vector2.Distance(new Vector2(x, y), new Vector2(w / 2f, h / 2f)) / (w * 0.55f);
                c += new Color(0.56f, 0.96f, 0.89f) * 0.12f * Mathf.Clamp01(1 - d);           // faint mint glow behind the features
                if (v < 0.38f) c += Color.white * 0.11f * (1 - v / 0.38f);                       // soft top reflection
                if (y % 2 == 0) c += Color.white * 0.012f;                                      // scanlines
                float a = RoundRectAlpha(x, y, w, h, 34);
                float edge = RoundRectAlpha(x, y, w, h, 34) - RoundRectAlpha(x - 2, y - 2, w - 4, h - 4, 32);
                c = Color.Lerp(c, c + Color.white * 0.1f, Mathf.Clamp01(edge));
                c.a = a; px[y * w + x] = c;
            }
            t.SetPixels(px); t.Apply(); return t;
        }

        static Texture2D Pill(int w, int h, Color col)
        {
            var t = New(w, h); var px = new Color[w * h];
            var light = Color.Lerp(col, Color.white, 0.55f); var deep = col * 0.78f; deep.a = 1;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                float v = 1 - (float)y / h;
                var c = v < 0.45f ? Color.Lerp(light, col, v / 0.45f) : Color.Lerp(col, deep, (v - 0.45f) / 0.55f);
                // catchlight, upper left
                float hx = (x - w * 0.33f) / (w * 0.15f), hy = (y - h * 0.73f) / (h * 0.1f);
                if (hx * hx + hy * hy < 1) c = Color.Lerp(c, Color.white, 0.92f);
                c.a = RoundRectAlpha(x, y, w, h, w / 2f); px[y * w + x] = c;
            }
            t.SetPixels(px); t.Apply(); return t;
        }

        /** A soft stroke: bend > 0 sags (∪), < 0 arches (∩). */
        static Texture2D Curve(int w, int h, float bend, bool thick, Color? col = null)
        {
            var t = New(w, h); var px = new Color[w * h]; var c = col ?? Color.white;
            float thickness = thick ? h * 0.22f : h * 0.18f;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                float u = (float)x / (w - 1) * 2 - 1;
                float cy = h * 0.5f + bend * (h * 0.32f) * (u * u - 0.5f) * -1;
                float d = Mathf.Abs(y - cy);
                float a = Mathf.Abs(u) > 0.92f ? 0 : Mathf.Clamp01(thickness / 2 - d + 0.8f);
                var p = c; p.a = a; px[y * w + x] = p;
            }
            t.SetPixels(px); t.Apply(); return t;
        }

        static Texture2D Oval(int w, int h)
        {
            var t = New(w, h); var px = new Color[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                float u = (x - w / 2f) / (w / 2f), v = (y - h / 2f) / (h / 2f);
                var p = Color.white; p.a = Mathf.Clamp01((1 - (u * u + v * v)) * 6); px[y * w + x] = p;
            }
            t.SetPixels(px); t.Apply(); return t;
        }

        static Texture2D Radial(int s, Color col)
        {
            var t = New(s, s); var px = new Color[s * s];
            for (int y = 0; y < s; y++) for (int x = 0; x < s; x++)
            {
                float d = Vector2.Distance(new Vector2(x, y), new Vector2(s / 2f, s / 2f)) / (s / 2f);
                var p = col; p.a = Mathf.Pow(Mathf.Clamp01(1 - d), 2); px[y * s + x] = p;
            }
            t.SetPixels(px); t.Apply(); return t;
        }
    }
}
