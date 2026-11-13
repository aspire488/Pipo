using System;
using UnityEngine;

namespace Pipo
{
    /*
     * The app <-> Unity contract, version 1. The app (HomeViewModel) stays the authority: it owns
     * Pipo's state, memory, trips and AI. Unity renders, and reports what the user touched.
     *
     * In (app -> Unity), via UnitySendMessage("PipoBridge", "Receive", json):
     *   { "v":1, "type":"state", "state":{ ...same shape as the WebView stage's PipoStage.apply... } }
     *   { "v":1, "type":"goto",  "place":"bakery" }      the app moved him (a trip, home, the map)
     * Out (Unity -> app), via com.pipo.robot.unity.UnityBridge.onUnityEvent(json):
     *   ready | loading | locationEnter | locationExit | navComplete | navRequest | interaction | error
     */
    [Serializable] public class PoseDto { public float bob, squash = 1, stretch = 1, lean, headTilt, headBob, armL = 8, armR = 8, legL, legR, spread, sit, lie, liePitch, jump, crouch, cross, cover, hold, shake, yaw, headYaw, swing, swingAmt; }
    [Serializable] public class SuitDto { public int mk, owned; public bool cardboard, ownedCardboard, flying; }
    [Serializable] public class PanelDto { public string title, detail, status; }
    [Serializable] public class AiDto { public string state = "idle"; public PanelDto panel; }
    [Serializable] public class NibDto { public float x = 120; public string anim = "IDLE"; public bool visible = true; }
    [Serializable] public class EnvDto { public float hour = 12; public string weather = "clear"; }
    [Serializable] public class StateDto
    {
        public float x = 148, lift;
        public PoseDto pose = new PoseDto();
        public string expr = "CONTENT", mood = "CURIOUS", place;
        public bool talking, sleepy, away;
        public float[] look;
        public SuitDto suit = new SuitDto();
        public AiDto ai = new AiDto();
        public NibDto nib = new NibDto();
        public EnvDto env = new EnvDto();
    }
    [Serializable] public class Envelope { public int v; public string type; public StateDto state; public string place; }
    [Serializable] public class OutEvent { public int v = PipoBridge.Version; public string type, place, target, message; }

    public class PipoBridge : MonoBehaviour
    {
        public const int Version = 1;
        /** Everything Unity reports. The preview host and the tests listen here; on Android it also goes to the app. */
        public static event Action<OutEvent> OnEvent;
        public static PipoBridge Instance { get; private set; }

        public event Action<StateDto> OnState;
        public event Action<string> OnGoto;
        public StateDto Last { get; private set; } = new StateDto();

        void Awake() { Instance = this; gameObject.name = "PipoBridge"; }

        /** Entry point for UnitySendMessage. Never throws: a bad message is reported, not fatal. */
        public void Receive(string json)
        {
            Envelope e;
            try { e = JsonUtility.FromJson<Envelope>(json); }
            catch (Exception ex) { Emit(new OutEvent { type = "error", message = "malformed message: " + ex.Message }); return; }
            if (e == null) { Emit(new OutEvent { type = "error", message = "empty message" }); return; }
            if (e.v != Version) { Emit(new OutEvent { type = "error", message = $"unsupported bridge version {e.v} (this build speaks {Version})" }); return; }
            switch (e.type)
            {
                case "state":
                    if (e.state == null) { Emit(new OutEvent { type = "error", message = "state message without state" }); return; }
                    Last = e.state;
                    OnState?.Invoke(e.state);
                    break;
                case "goto":
                    if (string.IsNullOrEmpty(e.place)) { Emit(new OutEvent { type = "error", message = "goto without place" }); return; }
                    OnGoto?.Invoke(e.place);
                    break;
                default:
                    Emit(new OutEvent { type = "error", message = "unknown message type: " + e.type });
                    break;
            }
        }

        public static void Emit(OutEvent ev)
        {
            OnEvent?.Invoke(ev);
#if UNITY_ANDROID && !UNITY_EDITOR
            try { using var c = new AndroidJavaClass("com.pipo.robot.unity.UnityBridge"); c.CallStatic("onUnityEvent", JsonUtility.ToJson(ev)); }
            catch (Exception ex) { Debug.LogWarning("[PipoBridge] host unavailable: " + ex.Message); }
#endif
            if (ev.type == "error") Debug.LogWarning("[PipoBridge] " + ev.message);
        }
    }
}
