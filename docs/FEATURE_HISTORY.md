# Pipo feature history

This file contains the detailed development notes moved out of the main README so the project landing page stays readable.

## 🪞 The mirror-world, Bolt and neighbours update

The newest round, played on a Galaxy S23 (✅ = seen working on the phone):

- **Real conversations out in the world** (`engine/Talks.kt`). When you peek at him, Pipo talks with whoever is there. Each neighbour has their own voice: Grumble at the hardware shop is grumpy, Juniper is all blinking lights, Coach Raj is at the cricket ground, Lin plays table tennis and Bea plays badminton. They talk about what he came for (the part and the project it's for), the match, the weather and how often he's been. Nib chips in. A bubble shows over whoever is speaking, the transcript builds up underneath, and he brings a line of it home in his story. ✅
- **New sports places:** a cricket ground (pitch, boundary rope, sight-screen), Riverside Sports Hall (table tennis) and a badminton court, each with its own neighbour. While he's out playing, a **"Watch the match"** button on the home screen opens the live view. ✅
- **Cricket, two innings.** You bat first and tap when the ball reaches you. Mistimed swings show "too early" or "too late", and the stumps fly when you're bowled. Then the view flips: Pipo bats, and you bowl by tapping when the sweeping arrow lines up with his stumps. ✅
- **The hallway mirror.** A tall mirror by the front door shows his reflection. He checks himself before going out, and you can tap it. Once the mystery has started, the glass shimmers cyan, and sometimes his reflection stays behind and waves on its own. ✅ (reflection and tap)
- **Nib doesn't trust the mirror.** Nib goes and growls at it, then runs to tell Pipo in its own words ("Pipo, listen! Other Pipo in mirror. Same face. Different."). Pipo goes to check. ✅
- **Nib is its own character.** Text Nib directly ("nib, ...") and it answers in its own small voice and short words. It isn't Pipo's assistant, except in the lab. ✅ **Pipo answers Nib** when Nib says something. ✅
- **Bolt, his J.A.R.V.I.S.** "Hey Bolt" talks to his desk helper. If Bolt isn't built yet, he offers to build it, then really goes out for the missing parts (a little light from Juniper, wire from Grumble). ✅
- **Suit Mk 0:** before he can build real armour, "suit up" gets him a cardboard suit with tape, marker eyes and a drawn-on core. ✅
- **Movie nights:** popcorn, the rug, Nib, and a film on the TV (space, dinosaurs, scary, nature or cartoon), with reactions and a review afterwards. ✅
- **"Pipo, come back"** brings him home within seconds, wherever he is. ✅
- **The map** shows places he hasn't been yet as dashed "?" circles, with fog over the edge of the world. ✅

---

## 🆕 The living-world update

What changed in this round, with what was verified on a real phone (Galaxy S23) marked ✅:

**He knows his own life.** Pipo's answers come from his real memories, journal, possessions and Nib, through a grounding layer (`engine/Grounding.kt`). "What did we do yesterday?" gets the actual day. Ask "who is Nib?", "what's on your shelf?", "remember the rubber duck?" and he answers with what's true. ✅ He tries to do what you ask: asking him out, to cook or to play isn't refused because of his own internal cooldowns. If the shops are shut he says so ("Mo's Bakery is closed now. It opens at 6"). ✅

**Nib is a character** (`engine/NibLife.kt`, `ui/render/PetPainter.kt`, menu → **Nib**):
- **Feelings:** eight eye expressions, five sounds (happy trill, grumpy buzz, curious boop, sad slide, smug double-chirp) and picture thought-bubbles (ball, Pipo, battery, food, heart, "!", "?", music). ✅
- **Getting under Pipo's feet:** Nib pesters him while he's busy and he grumbles theatrically before giving in. ✅ Nib begs while he eats ✅, waits for the first bite when he cooks, curls up at bedtime, waits by the door when he goes out, and gets jealous when you pat him.
- **Growing up together:** friendship stages (shy newcomer → family), six tricks Pipo teaches over many sessions (high-five, fetch, spin, dance, play dead, sing), a favourite nap spot, presents, sulking when ignored, and getting braver in storms.

**Sports, as two-player games with Nib.** Football, cricket, table tennis and badminton at the field, park and garden, always Pipo vs Nib with a real score (Nib can win). There's indoor cricket with Nib bowling along the floor ✅, and the ball is physically at his feet, never teleported ✅. You can also play **Cricket** and **Table Tennis** against Pipo yourself. ✅

**Pipo the inventor** (`engine/Inventor.kt`):
- **Builder levels** (Tinkerer → Genius) that grow from every build and failure. ✅
- **Harder blueprints with real effects:**
  - Nib's turbo wheel and light-up tail
  - Bolt, his desk helper
  - a scout drone that brings home photos
  - rocket boots
  - the **armor**, Mk I → Mk II (flies) → Mk III (glowing core, flip-up visor)
- "Suit up" ✅ and "suit up and fly" ✅. The workshop turns into a lab as he levels up (tools, blueprints ✅, a hologram, holo-screens).

**The world is yours:**
- **Real weather** for your city from Open-Meteo (✅ Kochi, rain, 26.9 °C), with seasons (monsoon included) in the window tree.
- **Festivals:** Christmas, Halloween, New Year, Diwali, Onam, Vishu, Pongal, Holi and Eid. Each one decorates the room and the garden, gives Pipo and Nib outfits, and has a greeting; on the other side of the mirror they celebrate "wrongly". The moving dates for 2026–28 come from a table and need checking.
- **Outside places** are painted scenes (park, garden, field, lake, hills, shopfronts) where he actually does the thing he went for, with Nib.
- **The wall** keeps a pennant for every place he's been, plus a calendar.

**"Pipo, look!"** He asks first, then your own camera app takes one photo. A vision model sees it and he reacts as a curious kid. He never identifies people, guesses names or comments on looks. The photo is deleted right away, and what he sees feeds his world (a ball starts a game, food gives him a craving). ✅ up to the camera opening.

**Your phone:**
- **Music and video:** "play X" plays the top YouTube result ✅. "search X on YouTube" ✅ and "google X" show results.
- **Other AIs:** "ask ChatGPT X" sends your question to ChatGPT in the browser ✅, and "ask Gemini X" uses Google AI Mode ✅.
- **Finding out:** "find out X" makes him look it up and tell you which AI answered ✅.
- **Voice chat** continues hands-free after he answers. Not yet tested with a real voice.

**Fixes found by living with him on the phone:**
- Flappy Pipo's and Pixel Shooter's scores never updated.
- "play football" opened YouTube.
- He never scrolled his phone or played his console: his choice only ever looked at his top 3 ideas.
- He charged on loop while your phone was plugged in.
- The keyboard covered the room after you sent a message.
- He talked about his book after putting it away.
- Two footballs could exist at once.
- After 9 pm, "let's cook" sent him to look at bugs.

