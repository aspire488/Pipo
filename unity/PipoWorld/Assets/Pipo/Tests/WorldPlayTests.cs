using System.Collections;
using System.Collections.Generic;
using System.IO;
using NUnit.Framework;
using UnityEngine;
using UnityEngine.TestTools;

namespace Pipo.Tests
{
    /** The real runtime in Play Mode: the world, the suit and the app bridge, as the phone would run them. */
    public class WorldPlayTests
    {
        GameObject boot; WorldDirector world; PipoBridge bridge;
        readonly List<OutEvent> events = new List<OutEvent>();
        void Record(OutEvent e) => events.Add(e);

        [UnitySetUp]
        public IEnumerator SetUp()
        {
            PipoBootstrap.UsePreviewHost = false;    // the test is the host
            events.Clear();
            PipoBridge.OnEvent += Record;
            boot = new GameObject("Boot");
            world = boot.AddComponent<PipoBootstrap>().World;
            bridge = PipoBridge.Instance;
            yield return WaitIdle();
        }

        [UnityTearDown]
        public IEnumerator TearDown()
        {
            PipoBridge.OnEvent -= Record;
            foreach (var go in Object.FindObjectsByType<GameObject>(FindObjectsSortMode.None)) if (go.transform.parent == null) Object.Destroy(go);
            yield return null;
        }

        IEnumerator WaitIdle() { for (int i = 0; i < 600 && (world.Busy || world.Current == null); i++) yield return null; }
        IEnumerator Frames(int n) { for (int i = 0; i < n; i++) yield return null; }

        static string ShotDir => Path.GetFullPath(System.Environment.GetEnvironmentVariable("PIPO_SHOTS") ?? Path.Combine(Application.dataPath, "..", "..", "..", "docs", "unity-preview"));

        static void Shot(string name)
        {
            var cam = Camera.main; if (cam == null) return;
            Directory.CreateDirectory(ShotDir);
            var rt = new RenderTexture(540, 960, 24);
            cam.targetTexture = rt; cam.Render();
            RenderTexture.active = rt;
            var tex = new Texture2D(rt.width, rt.height, TextureFormat.RGB24, false);
            tex.ReadPixels(new Rect(0, 0, rt.width, rt.height), 0, 0); tex.Apply();
            cam.targetTexture = null; RenderTexture.active = null;
            File.WriteAllBytes(Path.Combine(ShotDir, name + ".png"), tex.EncodeToPNG());
            Object.Destroy(rt); Object.Destroy(tex);
        }

        [Test]
        public void EveryCatalogPlaceIsInTheWorld()
        {
            var def = WorldData.Load();
            Assert.AreEqual(def.places.Length + 1, world.Order.Count, "home + every catalog place");
            foreach (var p in def.places)
            {
                Assert.IsTrue(world.Has(p.id), "missing " + p.id);
                Assert.IsNotNull(LocationKit.ArchetypeFor(world.Locations[p.id]), "no builder for " + p.id);
            }
        }

        [UnityTest]
        public IEnumerator EveryLocationIsReachableAndBuilt()
        {
            foreach (var id in new List<string>(world.Order))
            {
                events.Clear();
                yield return world.Goto(id, instant: true);
                yield return Frames(3);
                Assert.AreEqual(id, world.Current.Id, "did not arrive at " + id);
                Assert.Greater(world.Instance_.Root.GetComponentsInChildren<Renderer>().Length, 10, id + " is empty");
                Assert.IsTrue(events.Exists(e => e.type == "locationEnter" && e.place == id), "no locationEnter for " + id);
                Assert.IsTrue(events.Exists(e => e.type == "navComplete" && e.place == id), "no navComplete for " + id);
                var x = world.Pipo.transform.position.x;
                Assert.IsTrue(x >= world.Instance_.MinX - 0.01f && x <= world.Instance_.MaxX + 0.01f, id + ": Pipo outside the walkable area");
                world.Cam.SetView(0.2f, 0.18f, 4.6f);
                yield return Frames(2);
                Shot("loc_" + id);
            }
            Assert.AreEqual(1, Object.FindObjectsByType<WorldDirector>(FindObjectsSortMode.None).Length);
            Assert.Greater(Object.FindObjectsByType<InteractionTarget>(FindObjectsSortMode.None).Length, 0, "places have things to touch");
        }

        static Color ArmorRed(PipoCharacter p)
        {
            foreach (var r in p.Model.GetComponentsInChildren<Renderer>(true))
                if (r.name.StartsWith("MK1_") && r.sharedMaterial.name.Contains("Armor_Red"))
                    return r.sharedMaterial.HasProperty("baseColorFactor") ? r.sharedMaterial.GetColor("baseColorFactor") : r.sharedMaterial.color;
            return Color.clear;
        }

        [UnityTest]
        public IEnumerator SuitOnIsMkIAndSuitOffIsNothing()
        {
            var p = world.Pipo;
            int pieces = p.Armor.Count;
            Assert.Greater(pieces, 10, "MK1 armor pieces found in pipo.glb");
            Assert.AreEqual(0, p.VisibleArmorPieces, "starts without a suit");

            Assert.IsTrue(p.Equip("1"));
            yield return new WaitForSeconds(1.2f);
            Assert.AreEqual(p.ExpectedPieces("1"), p.VisibleArmorPieces, "every Mk I piece is on (no Mk II thrusters)");
            Assert.That(Vector4.Distance(ArmorRed(p), PipoCharacter.Looks["1"].red), Is.LessThan(0.02f), "Mk I is red, not cardboard");
            Assert.IsFalse(p.Equip("1"), "equipping the same suit again changes nothing");

            p.Equip(null);
            yield return new WaitForSeconds(1.2f);
            Assert.AreEqual(0, p.VisibleArmorPieces, "suit off leaves nothing behind");

            p.Equip("mk0");
            yield return new WaitForSeconds(1.2f);
            Assert.That(Vector4.Distance(ArmorRed(p), PipoCharacter.Looks["mk0"].red), Is.LessThan(0.02f), "Mk 0 is cardboard");
            p.Equip("1");
            yield return new WaitForSeconds(1.2f);
            Assert.That(Vector4.Distance(ArmorRed(p), PipoCharacter.Looks["1"].red), Is.LessThan(0.02f), "after Mk 0, Mk I is red again (the old cardboard bug)");
            Assert.AreEqual(pieces, p.Armor.Count, "no duplicate meshes after repeated changes");
            world.Cam.SetView(0.25f, 0.15f, 3.2f);
            yield return Frames(2);
            Shot("suit_mk1");
            p.Equip(null);
            yield return new WaitForSeconds(1.2f);
            Shot("suit_off");
        }

        static string State(string extra) => "{\"v\":1,\"type\":\"state\",\"state\":{" + extra + "}}";

        [UnityTest]
        public IEnumerator TheAppsStateDrivesTheWorld()
        {
            bridge.Receive(State("\"x\":148,\"suit\":{\"mk\":1,\"owned\":1},\"expr\":\"HAPPY\",\"mood\":\"HAPPY\""));
            yield return new WaitForSeconds(1.2f);
            Assert.AreEqual("1", world.Pipo.Suit);
            Assert.AreEqual("HAPPY", world.Pipo.Face.Current);
            Assert.IsFalse(world.DockSuit.gameObject.activeSelf, "the dock is empty while he wears the suit");

            // he went to the bakery (a trip in the app): the world follows
            bridge.Receive(State("\"away\":true,\"place\":\"bakery\",\"suit\":{\"mk\":0,\"owned\":1}"));
            yield return WaitIdle(); yield return Frames(2); yield return WaitIdle();
            Assert.AreEqual("bakery", world.Current.Id);
            Assert.IsNull(world.Pipo.Suit);
            // and home again
            bridge.Receive(State("\"away\":false,\"suit\":{\"mk\":0,\"owned\":1}"));
            yield return WaitIdle(); yield return Frames(2); yield return WaitIdle();
            Assert.AreEqual("home", world.Current.Id);
            Assert.IsTrue(world.DockSuit.gameObject.activeSelf, "the suit he owns stands on the dock when he isn't wearing it");
        }

        [Test]
        public void BadMessagesAreReportedNotObeyed()
        {
            var before = world.Current.Id;
            bridge.Receive("{\"v\":2,\"type\":\"state\",\"state\":{}}");
            bridge.Receive("not json at all");
            bridge.Receive("{\"v\":1,\"type\":\"teleport\"}");
            bridge.Receive("{\"v\":1,\"type\":\"goto\",\"place\":\"narnia\"}");
            Assert.AreEqual(4, events.FindAll(e => e.type == "error").Count, string.Join(" | ", events.ConvertAll(e => e.type + ":" + e.message)));
            Assert.AreEqual(before, world.Current.Id, "a bad message changed nothing");
        }
    }
}
