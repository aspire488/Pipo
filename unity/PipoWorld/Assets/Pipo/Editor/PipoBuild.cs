using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;

namespace Pipo.EditorTools
{
    /*
     * Batch entry points (Unity -batchmode -executeMethod ...):
     *   Pipo.EditorTools.PipoBuild.Setup          create the bootstrap scene + project settings
     *   Pipo.EditorTools.PipoBuild.ExportAndroid  export an Android library project (Unity as a Library)
     */
    public static class PipoBuild
    {
        const string ScenePath = "Assets/Scenes/World.unity";

        [MenuItem("Pipo/Set Up Project")]
        public static void Setup()
        {
            Directory.CreateDirectory("Assets/Scenes");
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            new GameObject("Pipo").AddComponent<PipoBootstrap>();
            EditorSceneManager.SaveScene(scene, ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
            AlwaysInclude("Standard", "Unlit/Transparent");
            PlayerSettings.companyName = "Pipo";
            PlayerSettings.productName = "Pipo World";
            PlayerSettings.SetApplicationIdentifier(UnityEditor.Build.NamedBuildTarget.Android, "com.pipo.robot.world");
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel26;
            PlayerSettings.SetScriptingBackend(UnityEditor.Build.NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            AssetDatabase.SaveAssets();
            Debug.Log("[PipoBuild] scene + settings ready: " + ScenePath);
        }

        /** Shaders the runtime finds by name must ship even though no asset references them. */
        static void AlwaysInclude(params string[] names)
        {
            var so = new SerializedObject(GraphicsSettings.GetGraphicsSettings());
            var arr = so.FindProperty("m_AlwaysIncludedShaders");
            foreach (var n in names)
            {
                var sh = Shader.Find(n);
                if (sh == null) { Debug.LogWarning("[PipoBuild] shader not found: " + n); continue; }
                bool has = Enumerable.Range(0, arr.arraySize).Any(i => arr.GetArrayElementAtIndex(i).objectReferenceValue == sh);
                if (has) continue;
                arr.InsertArrayElementAtIndex(arr.arraySize);
                arr.GetArrayElementAtIndex(arr.arraySize - 1).objectReferenceValue = sh;
            }
            so.ApplyModifiedProperties();
        }

        [MenuItem("Pipo/Export Android Library")]
        public static void ExportAndroid()
        {
            if (!File.Exists(ScenePath)) Setup();
            EditorUserBuildSettings.exportAsGoogleAndroidProject = true;
            var outDir = Path.GetFullPath(Path.Combine(Application.dataPath, "..", "..", "export", "android"));
            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { ScenePath }, locationPathName = outDir, target = BuildTarget.Android, options = BuildOptions.None,
            });
            Debug.Log($"[PipoBuild] Android export: {report.summary.result} -> {outDir} ({report.summary.totalSize / 1e6:F1} MB, {report.summary.totalErrors} errors)");
            if (report.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded && Application.isBatchMode) EditorApplication.Exit(1);
        }
    }
}
