# 3D stage preview

The app's 3D room lives in `app/src/main/assets/stage/` (Three.js + the Blender models from
`art/blender/build_pipo3d.py`). On the phone, `ui/home/Stage3D.kt` loads it in a WebView and
`HomeViewModel` drives it. This folder runs the same files in a desktop browser.

```powershell
node tools/stage-preview/server.mjs        # http://127.0.0.1:5178/  (add ?city=London for the weather tool)
```

`/api/chat` and `/api/research` call Gemini, then Groq, with the keys from `local.properties`;
the keys never reach the browser. In the browser, `src/preview-host.js` stands in for the
app's engine (simplified autonomy and poses; real chat, weather, find-out and suit state).

Regression smoke test (headless Chrome, writes screenshots + `transcript.txt`):

```powershell
cd tools/stage-preview; npm install; node smoke.mjs smoke-out
```

Rebuild the models after editing the script: run `art/blender/build_pipo3d.py` in Blender
(Blender MCP, or `blender -b -P art/blender/build_pipo3d.py`).
